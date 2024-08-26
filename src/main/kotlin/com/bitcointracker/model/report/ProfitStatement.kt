package com.bitcointracker.model.report

import com.bitcointracker.model.tax.TaxLotStatement
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction

data class ProfitStatement(
    val remainingUnits: ExchangeAmount,
    val soldUnits: ExchangeAmount,
    val currentValue: ExchangeAmount,
    val realizedProfit: ExchangeAmount,
    val unrealizedProfit: ExchangeAmount,
    val realizedProfitPercentage: Double,
    val unrealizedProfitPercentage: Double,
    val soldLots: List<NormalizedTransaction>,
    val taxLotStatements: List<TaxLotStatement>,
)