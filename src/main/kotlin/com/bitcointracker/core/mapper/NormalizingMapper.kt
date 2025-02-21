package com.bitcointracker.core.mapper

import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction

/**
 * Generic interface for mappers that convert from exchange specific
 * formats to the normalized format in this application.
 */
interface NormalizingMapper<T> {
    fun normalizeTransactions(transactions: List<T>): List<NormalizedTransaction> 
        = transactions.map { normalizeTransaction(it) }
            .toList()

    fun normalizeTransaction(transaction: T): NormalizedTransaction
}
