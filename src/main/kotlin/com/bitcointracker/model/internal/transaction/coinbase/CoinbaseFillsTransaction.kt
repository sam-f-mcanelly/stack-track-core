package com.bitcointracker.model.internal.transaction.coinbase

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import java.time.Instant

data class CoinbaseFillsTransaction(
    val portfolio: String,
    val tradeId: String,
    val product: String, // Example: BTC-USD, USD-BTC, etc
    val side: CoinbaseFillsSide,
    val createdAt: Instant,
    val size: ExchangeAmount,
    val price: ExchangeAmount, // Price of asset at time of trade
    val fee: ExchangeAmount,
    val total: ExchangeAmount,
)