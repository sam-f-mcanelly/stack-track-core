package com.bitcointracker.model.api

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount

data class AssetHoldingsReport(
    val asset: String,
    val assetAmount: ExchangeAmount,
    val fiatValue: ExchangeAmount?,
)