package com.bitcointracker.model.api

data class PaginationParams(
    val page: Int,
    val pageSize: Int,
    val sortBy: String,
    val sortOrder: String
)
