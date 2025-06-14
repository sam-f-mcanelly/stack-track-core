package com.bitcointracker.model.internal.transaction.coinbase

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import java.time.Instant

data class CoinbaseStandardTransaction(
    val id: String,
    val timestamp: Instant,
    val type: CoinbaseTransactionType,
    val quantityTransacted: ExchangeAmount,
    val assetValue: ExchangeAmount,
    val transactionAmount: ExchangeAmount,
    val fee: ExchangeAmount,
    val notes: String,
)