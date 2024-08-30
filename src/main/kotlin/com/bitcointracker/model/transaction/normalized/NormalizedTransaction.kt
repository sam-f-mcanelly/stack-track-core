package com.bitcointracker.model.transaction.normalized

import java.util.Date

data class NormalizedTransaction(
    val id: String,
    val source: TransactionSource,
    val type: NormalizedTransactionType,
    val transactionAmountFiat: ExchangeAmount,
    val fee: ExchangeAmount,
    var assetAmount: ExchangeAmount,
    val assetValueFiat: ExchangeAmount,
    val timestamp: Date,
    val address: String = "",
    val filedWithIRS: Boolean = false,
)