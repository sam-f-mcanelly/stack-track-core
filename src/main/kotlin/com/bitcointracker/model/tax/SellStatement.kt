package com.bitcointracker.model.tax

import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction

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