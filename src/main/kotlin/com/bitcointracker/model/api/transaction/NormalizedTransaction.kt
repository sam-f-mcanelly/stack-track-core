package com.bitcointracker.model.api.transaction

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import java.time.Instant

data class NormalizedTransaction(
    val id: String,
    val source: TransactionSource,
    val type: NormalizedTransactionType,
    val transactionAmountFiat: ExchangeAmount,
    val fee: ExchangeAmount,
    var assetAmount: ExchangeAmount,
    val assetValueFiat: ExchangeAmount,
    val timestamp: Instant,
    val timestampText: String,
    val address: String = "",
    val notes: String = "",
    val filedWithIRS: Boolean = false,
)