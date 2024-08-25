package com.bitcointracker.model.transaction.normalized

import java.util.Date

data class NormalizedTransaction(
    val id: String,
    val type: NormalizedTransactionType,
    val transactionAmountUSD: ExchangeAmount,
    val fee: ExchangeAmount,
    val assetAmount: ExchangeAmount,
    val assetValueUSD: ExchangeAmount,
    val timestamp: Date,
    val address: String = "",
)