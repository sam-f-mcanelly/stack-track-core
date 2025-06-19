package com.bitcointracker.core.database

import com.bitcointracker.core.cache.TransactionMetadataCache
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.inList
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.ZoneOffset

class H2TransactionRepository @Inject constructor(
    private val database: Database,
    private val transactionCache: TransactionMetadataCache
) : TransactionRepository {

    init {
        // Initialize tables in a regular transaction
        transaction(database) {
            SchemaUtils.create(TransactionTable)
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T {
        return try {
            newSuspendedTransaction(Dispatchers.IO, database) {
                block()
            }
        } catch (e: Exception) {
            println("Database query failed: ${e.message}")
            e.printStackTrace()
            throw e // Re-throw to let caller handle
        }
    }

    /**
     * Clears all data from the database tables.
     * This should only be used in test environments.
     */
    override suspend fun clearDatabase() {
        dbQuery {
            TransactionTable.deleteAll()
        }
        try {
            transactionCache.clear()
        } catch (e: Exception) {
            println("Failed to clear cache: ${e.message}")
        }
    }

    override suspend fun addTransaction(transaction: NormalizedTransaction) {
        dbQuery {
            try {
                addTransactionToTable(transaction)
            } catch(e: Exception) {
                println("Failed to add transaction ${transaction.id}")
                println(e.localizedMessage)
            }
        }

        transactionCache.update(getAllTransactions())
    }

    override suspend fun addTransactions(transactions: List<NormalizedTransaction>) {
        val addedTransactions = dbQuery {
            val added = mutableListOf<NormalizedTransaction>()
            transactions.forEach { transaction ->
                try {
                    addTransactionToTable(transaction)
                    added.add(transaction)
                } catch(e: Exception) {
                    println("Failed to add transaction ${transaction.id}: ${e.message}")
                    e.printStackTrace()
                }
            }
            added
        }

        // Only update cache if we successfully added transactions
        if (addedTransactions.isNotEmpty()) {
            try {
                val allTransactions = getAllTransactions()
                transactionCache.update(allTransactions)
            } catch (e: Exception) {
                println("Failed to update cache: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun addTransactionToTable(transaction: NormalizedTransaction) {
        TransactionTable.insert {
            it[id] = transaction.id
            it[transactionSource] = transaction.source
            it[type] = transaction.type

            it[transactionAmountFiatValue] = transaction.transactionAmountFiat.amount
            it[transactionAmountFiatUnit] = transaction.transactionAmountFiat.unit

            it[feeFiatValue] = transaction.fee.amount
            it[feeFiatUnit] = transaction.fee.unit

            it[assetAmountValue] = transaction.assetAmount.amount
            it[assetAmountUnit] = transaction.assetAmount.unit

            it[assetValueFiatValue] = transaction.assetValueFiat.amount
            it[assetValueFiatUnit] = transaction.assetValueFiat.unit

            it[timestamp] = kotlinx.datetime.Instant.fromEpochMilliseconds(transaction.timestamp.toEpochMilli())
            it[timestampText] = transaction.timestampText
            it[address] = transaction.address
            it[notes] = transaction.notes
            it[filedWithIRS] = transaction.filedWithIRS
        }
    }

    override suspend fun getTransactionById(id: String): NormalizedTransaction? {
        return dbQuery {
            TransactionTable.selectAll()
                .where { TransactionTable.id eq id }
                .map { it.toNormalizedTransaction() }
                .singleOrNull()
        }
    }

    override suspend fun getAllTransactions(): List<NormalizedTransaction> {
        return dbQuery {
            TransactionTable.selectAll()
                .map { it.toNormalizedTransaction() }
        }
    }

    override suspend fun getSellTransactionsByYear(year: Int): List<NormalizedTransaction> {
        return dbQuery {
            TransactionTable.selectAll()
                .where {
                    // Filter for SELL transactions
                    (TransactionTable.type eq NormalizedTransactionType.SELL) and
                            // Filter for the specified year
                            (ExtractYear(TransactionTable.timestamp) eq year)
                }
                .orderBy(TransactionTable.timestamp, SortOrder.DESC)
                .map { it.toNormalizedTransaction() }
        }
    }

    override suspend fun getFilteredTransactions(
        sources: List<TransactionSource>?,
        types: List<NormalizedTransactionType>?,
        assets: List<String>?,
        startDate: Instant?,
        endDate: Instant?
    ): List<NormalizedTransaction> {
        return dbQuery {
            val conditions = mutableListOf<Op<Boolean>>()

            sources?.takeIf { it.isNotEmpty() }?.let {
                conditions.add(TransactionTable.transactionSource inList sources)
            }

            types?.takeIf { it.isNotEmpty() }?.let {
                conditions.add(TransactionTable.type inList types)
            }

            assets?.takeIf { it.isNotEmpty() }?.let {
                conditions.add(TransactionTable.assetAmountUnit inList assets)
            }

            if (startDate != null) {
                conditions.add(TransactionTable.timestamp greaterEq
                        kotlinx.datetime.Instant.fromEpochMilliseconds(startDate.toEpochMilli()))
            }

            if (endDate != null) {
                conditions.add(TransactionTable.timestamp lessEq
                        kotlinx.datetime.Instant.fromEpochMilliseconds(endDate.toEpochMilli()))
            }

            val whereCondition = when {
                conditions.isEmpty() -> Op.TRUE
                else -> conditions.reduce { acc, op -> acc and op }
            }

            TransactionTable.selectAll()
                .where { whereCondition }
                .map { it.toNormalizedTransaction() }
        }
    }

    override suspend fun getTransactionsBySource(vararg source: TransactionSource): List<NormalizedTransaction> {
        return dbQuery {
            TransactionTable.selectAll()
                .where { TransactionTable.transactionSource inList source.toList() }
                .map { it.toNormalizedTransaction() }
        }
    }

    override suspend fun getTransactionsByType(vararg type: NormalizedTransactionType): List<NormalizedTransaction> {
        return dbQuery {
            TransactionTable.selectAll()
                .where { TransactionTable.type inList type.toList() }
                .map { it.toNormalizedTransaction() }
        }
    }

    override suspend fun getTransactionsByDateRange(startDate: Instant, endDate: Instant): List<NormalizedTransaction> {
        return dbQuery {
            TransactionTable.selectAll()
                .where {
                    (TransactionTable.timestamp greaterEq kotlinx.datetime.Instant.fromEpochMilliseconds(startDate.toEpochMilli())) and
                            (TransactionTable.timestamp lessEq kotlinx.datetime.Instant.fromEpochMilliseconds(endDate.toEpochMilli()))
                }
                .map { it.toNormalizedTransaction() }
        }
    }

    override suspend fun getTransactionsByAsset(vararg asset: String): List<NormalizedTransaction> {
        return dbQuery {
            TransactionTable.selectAll()
                .where { TransactionTable.assetAmountUnit inList asset.toList() }
                .map { it.toNormalizedTransaction() }
        }
    }

    override suspend fun updateTransaction(transaction: NormalizedTransaction) {
        dbQuery {
            TransactionTable.update ({ TransactionTable.id eq transaction.id }) {
                it[transactionSource] = transaction.source
                it[type] = transaction.type

                it[transactionAmountFiatValue] = transaction.transactionAmountFiat.amount
                it[transactionAmountFiatUnit] = transaction.transactionAmountFiat.unit

                it[feeFiatValue] = transaction.fee.amount
                it[feeFiatUnit] = transaction.fee.unit

                it[assetAmountValue] = transaction.assetAmount.amount
                it[assetAmountUnit] = transaction.assetAmount.unit

                it[assetValueFiatValue] = transaction.assetValueFiat.amount
                it[assetValueFiatUnit] = transaction.assetValueFiat.unit

                it[timestamp] = kotlinx.datetime.Instant.fromEpochMilliseconds(transaction.timestamp.toEpochMilli())
                it[timestampText] = transaction.timestampText
                it[address] = transaction.address
                it[notes] = transaction.notes
                it[filedWithIRS] = transaction.filedWithIRS
            }
        }
    }

    override suspend fun deleteTransaction(id: String) {
        dbQuery {
            TransactionTable.deleteWhere { TransactionTable.id eq id }
        }
        transactionCache.update(getAllTransactions())
    }

    internal class ExtractYear(val expression: ExpressionWithColumnType<kotlinx.datetime.Instant>) :
        CustomFunction<Int>("EXTRACT", IntegerColumnType()) {

        override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
            append("EXTRACT(YEAR FROM ")
            append(expression)
            append(")")
        }
    }

    // Utility extension function to convert ResultRow to NormalizedTransaction
    private fun ResultRow.toNormalizedTransaction(): NormalizedTransaction {
        return NormalizedTransaction(
            id = this[TransactionTable.id],
            source = this[TransactionTable.transactionSource],
            type = this[TransactionTable.type],
            transactionAmountFiat = ExchangeAmount(
                amount = this[TransactionTable.transactionAmountFiatValue],
                unit = this[TransactionTable.transactionAmountFiatUnit]
            ),
            fee = ExchangeAmount(
                amount = this[TransactionTable.feeFiatValue],
                unit = this[TransactionTable.feeFiatUnit]
            ),
            assetAmount = ExchangeAmount(
                amount = this[TransactionTable.assetAmountValue],
                unit = this[TransactionTable.assetAmountUnit]
            ),
            assetValueFiat = ExchangeAmount(
                amount = this[TransactionTable.assetValueFiatValue],
                unit = this[TransactionTable.assetValueFiatUnit]
            ),
            timestamp = this[TransactionTable.timestamp].toJavaInstant(),
            timestampText = this[TransactionTable.timestampText],
            address = this[TransactionTable.address],
            notes = this[TransactionTable.notes],
            filedWithIRS = this[TransactionTable.filedWithIRS]
        )
    }
}
