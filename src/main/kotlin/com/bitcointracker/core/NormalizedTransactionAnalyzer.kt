package com.bitcointracker.core

import com.bitcointracker.model.report.ProfitStatement
import com.bitcointracker.model.tax.TaxLotStatement
import com.bitcointracker.model.tax.TaxType
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType
import javax.inject.Inject

class NormalizedTransactionAnalyzer @Inject constructor(){

    fun computeTransactionResults(
        transactionCache: TransactionCache,
        asset: String,
        currentAssetPrice: ExchangeAmount
    ): ProfitStatement {
        val buyQueue = mutableListOf<NormalizedTransaction>()
        val soldLots = mutableListOf<NormalizedTransaction>()
        val taxLotStatements = mutableListOf<TaxLotStatement>()
        val transactions = transactionCache.getTransactionsByAsset(asset)
            .intersect(transactionCache.getTransactionsByType(
                    NormalizedTransactionType.BUY,
                    NormalizedTransactionType.SELL
            ).toSet()
        )

        // Sort transactions by timestamp to ensure FIFO based on time
        val sortedTransactions = transactions.sortedBy { it.timestamp }

        for (transaction in sortedTransactions) {
            when (transaction.type) {
                NormalizedTransactionType.BUY -> {
                    buyQueue.add(transaction)
                }
                NormalizedTransactionType.SELL -> {
                    var remainingAmountToSell = transaction.assetAmount
                    var buysUsed = 1

                    while (remainingAmountToSell.amount > 0.0) {
                        if (buyQueue.isEmpty()) {
                            throw RuntimeException("Attempting to sell more than has been bought!")
                        }

                        val buyTransaction = buyQueue.first()

                        if (buyTransaction.assetAmount.amount <= remainingAmountToSell.amount) {
                            println("")
                            val spent = buyTransaction.transactionAmountUSD
                            val returned = ExchangeAmount(
                                buyTransaction.assetAmount.amount * transaction.assetValueUSD.amount,
                                transaction.assetValueUSD.unit
                            )
                            taxLotStatements.add(
                                TaxLotStatement(
                                    id = "${transaction.id}-${buysUsed}",
                                    buyTransaction = buyTransaction,
                                    sellTransaction = transaction,
                                    taxType = getTaxType(transaction, buyTransaction),
                                    holdingTimeInDays = getHoldingPeriod(transaction, buyTransaction),
                                    isPartialSell = false,
                                    percentOfLotSold = 100.0,
                                    spent = spent,
                                    returned = returned,
                                    profit = returned - spent,
                                )
                            )
                            buysUsed++
                            soldLots.add(buyTransaction)
                            remainingAmountToSell -= buyTransaction.assetAmount
                            buyQueue.removeAt(0)
                        } else {
                            // Partially sell this buy transaction
                            val percentSold = (remainingAmountToSell.amount / buyTransaction.assetAmount.amount)
                            val spent = buyTransaction.transactionAmountUSD * percentSold
                            val returned = ExchangeAmount(
                                buyTransaction.assetAmount.amount * transaction.assetValueUSD.amount * percentSold,
                                transaction.assetValueUSD.unit
                            )
                            taxLotStatements.add(
                                TaxLotStatement(
                                    id = "${transaction.id}-${buysUsed}",
                                    buyTransaction = buyTransaction,
                                    sellTransaction = transaction,
                                    taxType = getTaxType(transaction, buyTransaction),
                                    holdingTimeInDays = getHoldingPeriod(transaction, buyTransaction),
                                    isPartialSell = true,
                                    percentOfLotSold = percentSold * 100,
                                    spent = spent,
                                    returned = returned,
                                    profit = returned - spent,
                                )
                            )

                            val partialAmount = remainingAmountToSell
                            val updatedBuyTransaction = buyTransaction.copy(
                                id = "${buyTransaction.id}-partial",
                                transactionAmountUSD = ExchangeAmount((buyTransaction.assetAmount.amount - partialAmount.amount) * buyTransaction.assetValueUSD.amount, buyTransaction.assetValueUSD.unit),
                                assetAmount = buyTransaction.assetAmount - partialAmount,
                                assetValueUSD = buyTransaction.assetValueUSD
                            )
                            soldLots.add(updatedBuyTransaction)
                            buyQueue[0] = updatedBuyTransaction
                            remainingAmountToSell = ExchangeAmount(0.0, asset)
                        }
                    }
                }
                else -> {
                    throw RuntimeException("Non-buy or sell transaction detected: $transaction")
                }
            }
        }

        val totalBoughtUnits = sortedTransactions.filter { it.type == NormalizedTransactionType.BUY }
            .map { it.assetAmount }
            .sumOf { it.amount }
        val soldUnits = sortedTransactions.filter { it.type == NormalizedTransactionType.SELL }
            .map { it.assetAmount }
            .sumOf { it.amount }

        val currentValue = ExchangeAmount((totalBoughtUnits - soldUnits) * currentAssetPrice.amount, currentAssetPrice.unit)
        val remainingCostBasis = buyQueue.map { it.transactionAmountUSD }.reduce { acc, i -> acc + i }
        val realizedProfit = taxLotStatements.map { it.profit }.reduce { acc, i -> acc + i}
        val soldCostBasis = soldLots.map { it.transactionAmountUSD }.reduce { acc, i -> acc + i}
        val soldValue = taxLotStatements.map { it.sellTransaction.transactionAmountUSD }.reduce { acc, i -> acc + i }

        return ProfitStatement(
            remainingUnits = ExchangeAmount(totalBoughtUnits - soldUnits, asset),
            soldUnits = ExchangeAmount(soldUnits, asset),
            currentValue = currentValue,
            realizedProfit = realizedProfit,
            unrealizedProfit = currentValue - remainingCostBasis,
            realizedProfitPercentage = (soldValue.amount - soldCostBasis.amount) / soldCostBasis.amount * 100.0,
            unrealizedProfitPercentage = (currentValue.amount - remainingCostBasis.amount) / remainingCostBasis.amount * 100.0,
            soldLots = soldLots,
            taxLotStatements = taxLotStatements,
        )
    }

    private fun getHoldingPeriod(
        sellTransaction: NormalizedTransaction,
        buyTransaction: NormalizedTransaction,
    ) : Long
        = (sellTransaction.timestamp.time - buyTransaction.timestamp.time) / (1000 * 60 * 60 * 24)

    private fun getTaxType(
        sellTransaction: NormalizedTransaction,
        buyTransaction: NormalizedTransaction,
    ) : TaxType {
        val holdingPeriodInDays = getHoldingPeriod(sellTransaction, buyTransaction)
        val holdingPeriodInYears = holdingPeriodInDays / 365.0
        return if (holdingPeriodInYears > 1) TaxType.LONG_TERM else TaxType.SHORT_TERM
    }
}
