package com.bitcointracker.core.database

import com.bitcointracker.core.database.TransactionRepository
import com.bitcointracker.core.cache.TransactionMetadataCache
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.SortOrder
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject

class H2TransactionRepository @Inject constructor(
    private val database: Database,
    private val transactionCache: TransactionMetadataCache
) : TransactionRepository {

    init {
        // Initialize tables in a regular transaction
        org.jetbrains.exposed.sql.transactions.transaction(database) {
            SchemaUtils.create(TransactionTable)
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) {
            block()
        }

    override suspend fun addTransaction(transaction: NormalizedTransaction) {
        dbQuery {
            try {
                addTransactionToTable(transaction)
            } catch(e: Exception) {
                println("Failed to add transaction ${transaction.id}")
                println(e.localizedMessage)
                println(e.stackTrace)
            }
        }

        transactionCache.update(getAllTransactions())
    }

    override suspend fun addTransactions(transactions: List<NormalizedTransaction>) {
        dbQuery {
            transactions.forEach { transaction ->
                try {
                    addTransactionToTable(transaction)
                } catch(e: Exception) {
                    println("Failed to add transaction ${transaction.id}")
                    println(e.localizedMessage)
                    println(e.stackTrace)
                }
            }
        }
        transactionCache.update(getAllTransactions())
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

            it[timestamp] =
                transaction.timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            it[timestampText] = transaction.timestampText
            it[address] = transaction.address
            it[notes] = transaction.notes
            it[filedWithIRS] = transaction.filedWithIRS
        }
    }

    override suspend fun getTransactionById(id: String): NormalizedTransaction? {
        return dbQuery {
            TransactionTable.select { TransactionTable.id eq id }
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
            TransactionTable
                .select {
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
        startDate: Date?,
        endDate: Date?
    ): List<NormalizedTransaction> {
        return dbQuery {
            TransactionTable.select {
                val conditions = mutableListOf<Op<Boolean>>()

                sources?.takeIf { it.isNotEmpty() }?.let {
                    conditions.add(TransactionTable.transactionSource inList sources)
                }

                types?.takeIf { it.isNotEmpty() }?.let {
                    conditions. add(TransactionTable.type inList types)
                }

                assets?.takeIf { it.isNotEmpty() }?.let {
                    conditions.add(TransactionTable.assetAmountUnit inList assets)
                }

                if (startDate != null) {
                    conditions.add(TransactionTable.timestamp greaterEq startDate.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime())
                }

                if (endDate != null) {
                    conditions.add(TransactionTable.timestamp lessEq endDate.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime())
                }

                when {
                    conditions.isEmpty() -> Op.TRUE
                    else -> conditions.reduce { acc, op -> acc and op }
                }

            }.map { it.toNormalizedTransaction() }
        }
    }

    override suspend fun getTransactionsBySource(vararg source: TransactionSource): List<NormalizedTransaction> {
        return dbQuery {
            TransactionTable.select { TransactionTable.transactionSource inList source.toList() }
                .map { it.toNormalizedTransaction() }
        }
    }

    override suspend fun getTransactionsByType(vararg type: NormalizedTransactionType): List<NormalizedTransaction> {
        return dbQuery {
            TransactionTable.select { TransactionTable.type inList type.toList() }
                .map { it.toNormalizedTransaction() }
        }
    }

    override suspend fun getTransactionsByDateRange(startDate: Date, endDate: Date): List<NormalizedTransaction> {
        return dbQuery {
            TransactionTable.select {
                (TransactionTable.timestamp greaterEq startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()) and
                (TransactionTable.timestamp lessEq endDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
            }.map { it.toNormalizedTransaction() }
        }
    }

    override suspend fun getTransactionsByAsset(vararg asset: String): List<NormalizedTransaction> {
        return dbQuery {
            TransactionTable.select { TransactionTable.assetAmountUnit inList asset.toList() }
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

                it[timestamp] = transaction.timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
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

    internal class ExtractYear(val expr: ExpressionWithColumnType<java.time.LocalDateTime>) : Function<Int>(IntegerColumnType()) {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
            append("EXTRACT(YEAR FROM ")
            append(expr)
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
            timestamp = Date.from(this[TransactionTable.timestamp].atZone(ZoneId.systemDefault()).toInstant()),
            timestampText = this[TransactionTable.timestampText],
            address = this[TransactionTable.address],
            notes = this[TransactionTable.notes],
            filedWithIRS = this[TransactionTable.filedWithIRS]
        )
    }
}