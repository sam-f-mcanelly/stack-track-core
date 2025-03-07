package com.bitcointracker.model.internal.historical

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import java.util.Date

/**
 * Represents Bitcoin price data for a single day
 */
data class BitcoinData(
    val date: Date,
    val open: ExchangeAmount,
    val high: ExchangeAmount,
    val low: ExchangeAmount,
    val close: ExchangeAmount
)
