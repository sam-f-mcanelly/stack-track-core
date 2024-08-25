package com.bitcointracker.model.report

import com.bitcointracker.model.transaction.normalized.ExchangeAmount

data class ProfitStatement(
    val units: ExchangeAmount,
    val costBasis: ExchangeAmount,
    val currentValue: ExchangeAmount,
    val profit: ExchangeAmount,
    val profitPercentage: Double,
)