package com.bitcointracker.model.api

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import java.time.Instant

data class DailyData(
    val date: Instant,
    val value: ExchangeAmount,
    val assetAmount: ExchangeAmount
)
