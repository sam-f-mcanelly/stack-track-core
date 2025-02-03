package com.bitcointracker.util.jackson

import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction

data class PaginatedNormalizedTransactions(
    val items: List<NormalizedTransaction>,
    val totalPages: Int,
)