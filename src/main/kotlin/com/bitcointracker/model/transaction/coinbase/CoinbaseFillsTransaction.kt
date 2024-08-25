package com.bitcointracker.model.transaction.coinbase

import java.util.Date

data class CoinbaseFillsTransaction(
    val portfolio: String,
    val tradeId: String,
    val product: String, // Example: BTC-USD, USD-BTC, etc
    val side: String, // TODO create type (BUY/SELL)
    val createdAt: Date,
    val size: Double,
    val sizeUnit: String, // TODO make currency type
    val price: Double, // Price of asset at time of trade
    val fee: Double,
    val total: Double,
    val priceFeeAndTotalUnit: String, // TODO make currency type
)