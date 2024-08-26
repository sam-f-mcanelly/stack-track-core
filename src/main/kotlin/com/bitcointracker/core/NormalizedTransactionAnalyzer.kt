package com.bitcointracker.core

import com.bitcointracker.model.report.ProfitStatement
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType

class NormalizedTransactionAnalyzer {
    fun calculateAssetPurchased(transactions: List<NormalizedTransaction>, asset: String): ExchangeAmount {
        val purchased = transactions.filter { it.type == NormalizedTransactionType.BUY }
                .filter { it.assetAmount.unit == asset }
                .map { it.assetAmount }
                .reduceOrNull { acc, i -> acc + i }

        return purchased ?: ExchangeAmount(0.0, asset)
    }

    fun calculateUSDSpentOnAsset(transactions: List<NormalizedTransaction>, asset: String): ExchangeAmount {
        val spent = transactions.filter { it.type == NormalizedTransactionType.BUY }
                .filter { it.assetAmount.unit == asset }
                .map { it.transactionAmountUSD }
                .reduceOrNull { acc, i -> acc + i }

        return spent ?: ExchangeAmount(0.0, asset)
    }

    fun calculateWithdrawals(transactions: List<NormalizedTransaction>, asset: String): ExchangeAmount {
        val withdrawals = transactions.filter { it.type == NormalizedTransactionType.WITHDRAWAL }
                .filter { it.assetAmount.unit == asset }
                .map { it.transactionAmountUSD }
                .reduceOrNull { acc, i -> acc + i }

        return withdrawals ?: ExchangeAmount(0.0, asset)
    }

    /**
     * Simple profit statement.
     *
     * TODO: more accurate math
     */
//    fun calculateUnrealizedProfit(transactions: List<NormalizedTransaction>, asset: String, assetValue: ExchangeAmount = ExchangeAmount(64400.0, "USD")): ProfitStatement {
//        val filteredTransactions = transactions
//                .filter { transaction -> transaction.assetAmount.unit == asset }
//
//        val buyTransactions = filteredTransactions
//                .filter { transaction -> transaction.type == NormalizedTransactionType.BUY }
//
////        val sellTransactions = transactions
////            .filter { transaction -> transaction.type == NormalizedTransactionType.SELL }
//
//        val costBasis = buyTransactions.map { it.transactionAmountUSD }.reduce { acc, i -> acc + i }
//        val units = buyTransactions.map { it.assetAmount }.reduce { acc, i -> acc + i }
//
//        val currentValue = assetValue * units
//        val profit = currentValue - costBasis
//        val profitPercentage = (profit.amount / costBasis.amount) * 100.0
//
//        return ProfitStatement(
//                units = units,
//                costBasis = costBasis,
//                currentValue = ExchangeAmount(units.amount * assetValue.amount, assetValue.unit),
//                profit = profit,
//                profitPercentage = profitPercentage
//        )
//    }

    fun computeTransactionResults(
        transactions: List<NormalizedTransaction>,
        taxPercentage: Double,
        asset: String,
        currentAssetPrice: ExchangeAmount
    ): ProfitStatement {
        val buyQueue = mutableListOf<NormalizedTransaction>()
        var totalRealizedProfit = ExchangeAmount(0.0, "USD")
        var totalRealizedCost = ExchangeAmount(0.0, "USD")
        var totalTaxes = ExchangeAmount(0.0, "USD")
        val soldLots: MutableList<NormalizedTransaction> = mutableListOf()
        val partialSells: MutableList<NormalizedTransaction> = mutableListOf()
        val filteredTransactions = transactions
            .filter { transaction -> transaction.assetAmount.unit == asset }
        val buyAndSells = filteredTransactions.filter {
            transaction -> transaction.type == NormalizedTransactionType.BUY || transaction.type == NormalizedTransactionType.SELL
        }

        // Sort transactions by timestamp to ensure FIFO based on time
        val sortedTransactions = buyAndSells.sortedBy { it.timestamp }

        for (transaction in sortedTransactions) {
            when (transaction.type) {
                NormalizedTransactionType.BUY -> {
                    buyQueue.add(transaction)
                }
                NormalizedTransactionType.SELL -> {
                    // Sort the buyQueue by timestamp before selling
                    buyQueue.sortBy { it.timestamp }

                    var remainingAmountToSell = transaction.assetAmount
                    var totalSellUsd = ExchangeAmount(0.0, "USD")

                    while (remainingAmountToSell.amount > 0.0 && buyQueue.isNotEmpty()) {
                        val buyTransaction = buyQueue.first()
                        val buyPrice = buyTransaction.assetValueUSD
                        val holdingPeriodInDays = (transaction.timestamp.time - buyTransaction.timestamp.time) / (1000 * 60 * 60 * 24)
                        val holdingPeriodInYears = holdingPeriodInDays / 365.0
                        val applicableTaxPercentage = if (holdingPeriodInYears > 1) 15.0 else taxPercentage

                        if (buyTransaction.assetAmount <= remainingAmountToSell) {
                            // Sell the entire amount of this buy transaction
                            totalSellUsd += ExchangeAmount(buyTransaction.assetAmount.amount * buyPrice.amount, buyPrice.unit)
                            remainingAmountToSell -= buyTransaction.assetAmount
                            totalRealizedCost += buyTransaction.assetAmount * buyPrice
                            soldLots.add(buyTransaction)
                            buyQueue.removeAt(0)
                        } else {
                            // Partially sell this buy transaction
                            totalSellUsd += ExchangeAmount(remainingAmountToSell.amount * buyPrice.amount, buyPrice.unit)
                            totalRealizedCost += ExchangeAmount(remainingAmountToSell.amount * buyPrice.amount, buyPrice.unit)
                            partialSells.add(
                                NormalizedTransaction(
                                    id = buyTransaction.id,
                                    type = buyTransaction.type,
                                    transactionAmountUSD = buyTransaction.assetValueUSD * (buyTransaction.assetAmount - remainingAmountToSell),
                                    fee = buyTransaction.fee,
                                    assetAmount = buyTransaction.assetAmount - remainingAmountToSell,
                                    assetValueUSD = (ExchangeAmount((buyTransaction.assetAmount.amount - remainingAmountToSell.amount) * buyTransaction.assetValueUSD.amount, buyTransaction.assetValueUSD.unit)),
                                    timestamp = buyTransaction.timestamp,
                                    address = buyTransaction.address
                                )
                            )
                            remainingAmountToSell = ExchangeAmount(0.0, asset)
                        }
                    }

                    // Calculate profit and taxes for this sell transaction
                    val sellPrice = transaction.transactionAmountUSD
                    val sellTotal = ExchangeAmount(transaction.assetAmount.amount * sellPrice.amount, sellPrice.unit)
                    val profit = sellTotal - totalSellUsd
                    totalRealizedProfit += profit
                    totalTaxes += ExchangeAmount(profit.amount * taxPercentage / 100.0, profit.unit)
                }
                else -> {
                    throw RuntimeException("Non-buy or sell transaction detected " + transaction)
                }
            }
        }

        // Calculate remaining asset units and their cost basis
        val remainingUnits = buyQueue.map { it.assetAmount }.reduce { acc, i -> acc + i }
        val costBasis = buyQueue.map { it.assetAmount * it.assetValueUSD }.reduce { acc, i -> acc + i }
        val currentValue = ExchangeAmount(remainingUnits.amount * currentAssetPrice.amount, currentAssetPrice.unit)
        val unrealizedProfit = currentValue - costBasis
        val unrealizedProfitPercentage = if (costBasis.amount != 0.0) {
            unrealizedProfit.amount / costBasis.amount * 100
        } else 0.0
        val realizedProfitPercentage = if (totalRealizedCost.amount != 0.0) {
            totalRealizedProfit.amount / totalRealizedCost.amount * 100
        } else 0.0

        return ProfitStatement(
            units = remainingUnits,
            costBasis = costBasis,
            currentValue = currentValue,
            realizedProfit = totalRealizedProfit,
            unrealizedProfit = unrealizedProfit,
            realizedProfitPercentage = realizedProfitPercentage,
            unrealizedProfitPercentage = unrealizedProfitPercentage,
            partialSells = partialSells,
            soldLots = soldLots,
        )
    }
}
