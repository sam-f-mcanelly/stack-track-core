package com.bitcointracker.core.mapper

import com.bitcointracker.model.transaction.normalized.NormalizedTransaction

interface NormalizingMapper<T> {
    fun normalizeTransactions(transactions: List<T>): List<NormalizedTransaction> 
        = transactions.map { normalizeTransaction(it) }
            .toList()

    fun normalizeTransaction(transaction: T): NormalizedTransaction
}