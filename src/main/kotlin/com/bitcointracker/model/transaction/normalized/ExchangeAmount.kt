package com.bitcointracker.model.transaction.normalized

data class ExchangeAmount(
    val amount: Double,
    val unit: String, // TODO make type
)