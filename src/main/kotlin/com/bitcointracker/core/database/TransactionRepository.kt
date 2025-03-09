package com.bitcointracker.core.database

import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import java.util.Date

interface TransactionRepository {
    suspend fun addTransaction(transaction: NormalizedTransaction)
    suspend fun addTransactions(transactions: List<NormalizedTransaction>)
    suspend fun getTransactionById(id: String): NormalizedTransaction?
    suspend fun getAllTransactions(): List<NormalizedTransaction>
    suspend fun getSellTransactionsByYear(year: Int): List<NormalizedTransaction>
    suspend fun getFilteredTransactions(
        sources: List<TransactionSource>? = null,
        types: List<NormalizedTransactionType>? = null,
        assets: List<String>? = null,
        startDate: Date? = null, endDate: Date? = null
    ): List<NormalizedTransaction>
    suspend fun getTransactionsBySource(vararg source: TransactionSource): List<NormalizedTransaction>
    suspend fun getTransactionsByType(vararg type: NormalizedTransactionType): List<NormalizedTransaction>
    suspend fun getTransactionsByDateRange(startDate: Date, endDate: Date): List<NormalizedTransaction>
    suspend fun getTransactionsByAsset(vararg asset: String): List<NormalizedTransaction>
    suspend fun updateTransaction(transaction: NormalizedTransaction)
    suspend fun deleteTransaction(id: String)
}