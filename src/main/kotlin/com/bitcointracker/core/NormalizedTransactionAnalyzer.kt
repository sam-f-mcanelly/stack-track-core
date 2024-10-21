package com.bitcointracker.core

import com.bitcointracker.model.report.ProfitStatement
import com.bitcointracker.model.tax.TaxLotStatement
import com.bitcointracker.model.tax.TaxType
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType
import java.util.*
import javax.inject.Inject

class NormalizedTransactionAnalyzer @Inject constructor(){

    fun getAccumulation(days: Int, asset: String): List<ExchangeAmount> {
        // Calculate the cutoff date n days ago
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); clear(Calendar.MINUTE); clear(Calendar.SECOND); clear(Calendar.MILLISECOND) }.time
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days)
        }.time

        // Filter transactions that are buys within the last n days
        val recentBuys = TransactionCache.getTransactionsByAsset(asset).filter { transaction ->
            transaction.type == NormalizedTransactionType.BUY && transaction.timestamp.after(cutoffDate)
        }.sortedBy { it.timestamp }
        val recentSells = TransactionCache.getTransactionsByAsset(asset).filter { transaction ->
            transaction.type == NormalizedTransactionType.SELL && transaction.timestamp.after(cutoffDate)
        }.sortedBy { it.timestamp }

        // Initialize the cumulative amounts list
        val cumulativeAmounts = MutableList(days) { ExchangeAmount(0.0, asset) }
        var totalAmount = ExchangeAmount(0.0, asset)

        // Track each day's cumulative total
        for (day in 0 until days) {
            val dayStart = Calendar.getInstance().apply {
                time = today
                add(Calendar.DAY_OF_YEAR, -(days - 1 - day))
            }.time
            val dayEnd = Calendar.getInstance().apply {
                time = dayStart
                add(Calendar.DAY_OF_YEAR, 1)
            }.time

            // Sum up transactions that occurred on this specific day
            val dayBuys = recentBuys.filter {
                it.timestamp.after(dayStart) && it.timestamp.before(dayEnd)
            }
            val daySells = recentSells.filter {
                it.timestamp.after(dayStart) && it.timestamp.before(dayEnd)
            }


            for (transaction in dayBuys) {
                totalAmount += transaction.assetAmount
            }
            for (transaction in daySells) {
                totalAmount -= transaction.assetAmount
            }

            // Store the cumulative amount for the day
            cumulativeAmounts[day] = totalAmount
        }

        return cumulativeAmounts
    }

    fun computeTransactionResults(
        asset: String,
        currentAssetPrice: ExchangeAmount
    ): ProfitStatement {
        val buyQueue = mutableListOf<NormalizedTransaction>()
        val soldLots = mutableListOf<NormalizedTransaction>()
        val taxLotStatements = mutableListOf<TaxLotStatement>()
        val transactions = TransactionCache.getTransactionsByAsset(asset)
            .intersect(TransactionCache.getTransactionsByType(
                NormalizedTransactionType.BUY,
                NormalizedTransactionType.SELL
            ).toSet()
        ).filter {
            !it.filedWithIRS
        }

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
                            val spent = buyTransaction.transactionAmountFiat
                            val returned = ExchangeAmount(
                                buyTransaction.assetAmount.amount * transaction.assetValueFiat.amount,
                                transaction.assetValueFiat.unit
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
                            val spent = buyTransaction.transactionAmountFiat * percentSold
                            val returned = ExchangeAmount(
                                buyTransaction.assetAmount.amount * transaction.assetValueFiat.amount * percentSold,
                                transaction.assetValueFiat.unit
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
                            val id = if (buyTransaction.id.endsWith("-partial")) buyTransaction.id else "${buyTransaction.id}-partial"
                            val updatedBuyTransaction = buyTransaction.copy(
                                id = id,
                                transactionAmountFiat = ExchangeAmount((buyTransaction.assetAmount.amount - partialAmount.amount) * buyTransaction.assetValueFiat.amount, buyTransaction.assetValueFiat.unit),
                                assetAmount = buyTransaction.assetAmount - partialAmount,
                                assetValueFiat = buyTransaction.assetValueFiat
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

        val totalBoughtUnitsList = sortedTransactions.filter { it.type == NormalizedTransactionType.BUY }
            .map { it.assetAmount }
        val totalBoughtUnits = if (totalBoughtUnitsList.isNotEmpty()) {
            totalBoughtUnitsList.sumOf { it.amount }
        } else 0.0

        val soldUnitsList = sortedTransactions.filter { it.type == NormalizedTransactionType.SELL }
            .map { it.assetAmount }

        val soldUnits = if (soldUnitsList.isNotEmpty()) {
            soldUnitsList.sumOf { it.amount }
        } else 0.0


        val currentValue = ExchangeAmount((totalBoughtUnits - soldUnits) * currentAssetPrice.amount, currentAssetPrice.unit)
        val remainingCostBasis = buyQueue.map { it.transactionAmountFiat }.reduceOrNull { acc, i -> acc + i } ?: ExchangeAmount(0.0, currentAssetPrice.unit)
        val realizedProfit = taxLotStatements.map { it.profit }.reduceOrNull { acc, i -> acc + i} ?: ExchangeAmount(0.0, currentAssetPrice.unit)
        val soldCostBasis = soldLots.map { it.transactionAmountFiat }.reduceOrNull { acc, i -> acc + i} ?: ExchangeAmount(0.0, currentAssetPrice.unit)
        val soldValue = taxLotStatements.map { it.sellTransaction.transactionAmountFiat }.reduceOrNull { acc, i -> acc + i } ?: ExchangeAmount(0.0, currentAssetPrice.unit)

        return ProfitStatement(
            remainingUnits = ExchangeAmount(totalBoughtUnits - soldUnits, asset),
            soldUnits = ExchangeAmount(soldUnits, asset),
            currentValue = currentValue,
            costBasis = remainingCostBasis,
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
