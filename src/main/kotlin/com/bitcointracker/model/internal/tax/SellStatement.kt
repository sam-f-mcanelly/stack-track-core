package com.bitcointracker.model.internal.tax

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction

data class SellStatement(
    val id: String,
    val costBasis: ExchangeAmount,
    val sellPrice: ExchangeAmount,
    val grossProfit: ExchangeAmount,
    val grossProfitPercentage: Double,
    val netProfit: ExchangeAmount,
    val netProfitPercentage: ExchangeAmount,
    val sellTransaction: NormalizedTransaction,
    val buyTransactions: List<NormalizedTransaction>,
    val partialRemainderBuyTransactions: List<NormalizedTransaction>,
    val taxLotStatements: List<TaxLotStatement>,
)