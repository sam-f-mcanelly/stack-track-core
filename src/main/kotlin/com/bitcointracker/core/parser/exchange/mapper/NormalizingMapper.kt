package com.bitcointracker.core.parser.exchange.mapper

import com.bitcointracker.model.api.transaction.NormalizedTransaction

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
