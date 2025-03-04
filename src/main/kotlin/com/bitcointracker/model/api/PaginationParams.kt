package com.bitcointracker.model.api

import com.bitcointracker.model.api.transaction.NormalizedTransactionType

data class PaginationParams(
    val page: Int,
    val pageSize: Int,
    val sortBy: String,
    val sortOrder: String,
    val assets: List<String>? = null,
    val types: List<NormalizedTransactionType>? = null,
)