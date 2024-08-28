package com.bitcointracker.model.transaction.normalized

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class NormalizedTransaction(
    val id: String,
    val source: TransactionSource,
    val type: NormalizedTransactionType,
    val transactionAmountUSD: ExchangeAmount,
    val fee: ExchangeAmount,
    var assetAmount: ExchangeAmount,
    val assetValueUSD: ExchangeAmount,
    @Contextual
    val timestamp: Date,
    val address: String = "",
)