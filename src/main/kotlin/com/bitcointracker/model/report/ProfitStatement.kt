package com.bitcointracker.model.report

import com.bitcointracker.model.tax.TaxLotStatement
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction

data class ProfitStatement(
    val units: ExchangeAmount,
    val currentValue: ExchangeAmount,
    val realizedProfit: ExchangeAmount,
    val unrealizedProfit: ExchangeAmount,
    val realizedProfitPercentage: Double,
    val unrealizedProfitPercentage: Double,
    val partialSells: List<NormalizedTransaction>,
    val soldLots: List<NormalizedTransaction>,
    val taxLotStatements: List<TaxLotStatement>,
)