package com.bitcointracker.model.jackson

import com.bitcointracker.model.transaction.normalized.NormalizedTransaction

data class PaginatedNormalizedTransactions(
    val items: List<NormalizedTransaction>,
    val totalPages: Int,
)