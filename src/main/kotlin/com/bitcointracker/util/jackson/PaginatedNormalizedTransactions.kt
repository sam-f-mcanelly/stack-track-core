package com.bitcointracker.util.jackson

import com.bitcointracker.model.api.transaction.NormalizedTransaction

data class PaginatedNormalizedTransactions(
    val items: List<NormalizedTransaction>,
    val totalPages: Int,
)