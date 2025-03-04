package com.bitcointracker.model.api.transaction

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
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
    val timestampText: String,
    val address: String = "",
    val notes: String = "",
    val filedWithIRS: Boolean = false,
)