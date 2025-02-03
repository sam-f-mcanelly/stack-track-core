package com.bitcointracker.model.internal.report

import com.bitcointracker.model.internal.tax.TaxLotStatement
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction

data class ProfitStatement(
    val remainingUnits: ExchangeAmount,
    val soldUnits: ExchangeAmount,
    val currentValue: ExchangeAmount,
    val costBasis: ExchangeAmount,
    val realizedProfit: ExchangeAmount,
    val unrealizedProfit: ExchangeAmount,
    val realizedProfitPercentage: Double,
    val unrealizedProfitPercentage: Double,
    val soldLots: List<NormalizedTransaction>,
    val taxLotStatements: List<TaxLotStatement>,
)