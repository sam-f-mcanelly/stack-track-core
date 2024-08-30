package com.bitcointracker.core

import com.bitcointracker.model.report.ProfitStatement
import com.bitcointracker.model.tax.TaxLotStatement
import com.bitcointracker.model.tax.TaxType
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class NormalizedTransactionAnalyzer @Inject constructor(){

    fun computeFiatGain(
        transactionCache: TransactionCache,
        days: Int,
        currency: String,
        asset: String,
    ): List<Double> {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_YEAR)

        val bitcoinTransactions = transactionCache.getTransactionsByAsset("BTC")
            .filter {
                it.type == NormalizedTransactionType.BUY || it.type == NormalizedTransactionType.SELL
            }


        val itemValuePerDay = alignAssetValuesWithDates(generateDateSequence(30), bitcoinTransactions)

        val format = SimpleDateFormat("yyyyMMdd")

        // Initialize an array to hold the cumulative item counts
        val itemCounts = DoubleArray(days) { 0.0 }
        // Initialize an array for daily net values
        val dailyNetValues = DoubleArray(days) { 0.0 }

        // Process transactions to update itemCounts
        bitcoinTransactions.forEach { transaction ->
            val transactionDate = format.format(transaction.timestamp)
            val dayIndex = days - (currentDay - transactionDate.toInt()) - 1
            if (dayIndex in 0 until days) {
                when (transaction.type) {
                    NormalizedTransactionType.SELL -> itemCounts[dayIndex] += transaction.assetAmount.amount
                    NormalizedTransactionType.BUY -> itemCounts[dayIndex] -= transaction.assetAmount.amount
                    else -> throw RuntimeException("Found invalid transaction!")
                }
            }
        }

        // Calculate cumulative items held and compute net values
        var cumulativeItems = 0.0
        for (i in 0 until days) {
            cumulativeItems += itemCounts[i]
            dailyNetValues[i] = cumulativeItems * itemValuePerDay[i]
        }

        return dailyNetValues.toList()
    }

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
                            val updatedBuyTransaction = buyTransaction.copy(
                                id = "${buyTransaction.id}-partial",
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

    fun findNearestTransaction(transactions: List<NormalizedTransaction>, targetDate: Date): NormalizedTransaction? {
        // Sorting transactions just in case; if they are already sorted this can be omitted
        val sortedTransactions = transactions.sortedBy { it.timestamp }

        // Fold to find the most appropriate transaction
        return sortedTransactions.foldRight(null as NormalizedTransaction?) { transaction, nearest ->
            if (transaction.timestamp.time <= targetDate.time) transaction else nearest
        }
    }

    fun alignAssetValuesWithDates(dates: List<Date>, transactions: List<NormalizedTransaction>): List<Double> {
        return dates.map { date ->
            findNearestTransaction(transactions, date)?.assetValueFiat?.let {
                it.amount
            }
            0.0
        }
    }

    private fun generateDateSequence(days: Int): List<Date> {
        val calendar = Calendar.getInstance()
        return (1..days).map {
            calendar.add(Calendar.DATE, -1)
            calendar.time
        }
    }
}
