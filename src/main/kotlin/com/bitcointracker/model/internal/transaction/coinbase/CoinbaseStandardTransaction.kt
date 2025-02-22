package com.bitcointracker.model.internal.transaction.coinbase

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import java.util.Date

data class CoinbaseStandardTransaction(
    val id: String,
    val timestamp: Date,
    val type: CoinbaseTransactionType,
    val quantityTransacted: ExchangeAmount,
    val assetValue: ExchangeAmount,
    val transactionAmount: ExchangeAmount,
    val fee: ExchangeAmount,
    val notes: String,
)