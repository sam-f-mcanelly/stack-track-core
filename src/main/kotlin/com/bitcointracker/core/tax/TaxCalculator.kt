package com.bitcointracker.core.tax

import com.bitcointracker.model.api.tax.TaxType
import com.bitcointracker.model.api.tax.TaxableEventResult
import com.bitcointracker.model.api.tax.UsedBuyTransaction
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import org.slf4j.LoggerFactory
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs tax calculations for matching buy and sell transactions.
 */
@Singleton
class TaxCalculator @Inject constructor() {
    private companion object {
        private val logger = LoggerFactory.getLogger(TaxCalculator::class.java)
    }

    /**
     * Processes a sell transaction with a list of buy transactions.
     * Calculates cost basis and gains based on the provided buy transactions.
     *
     * @param sellTransaction The sell transaction to process
     * @param buyTransactions List of buy transactions to use, in order of preference
     * @param transactionTracker Tracker to manage used transaction amounts
     * @return TaxableEventResult containing the calculated gains and used buy transactions
     */
    fun calculateTaxableEvent(
        sellTransaction: NormalizedTransaction,
        buyTransactions: List<NormalizedTransaction>,
        transactionTracker: TransactionTracker
    ): TaxableEventResult {
        var remainingSellAmount = sellTransaction.assetAmount
        val usedBuyTransactions = mutableListOf<UsedBuyTransaction>()
        var totalCostBasis = ExchangeAmount(0.0, sellTransaction.transactionAmountFiat.unit)

        // Calculate the portion of buy transactions that can be covered
        for (buyTx in buyTransactions) {
            logger.info("processing buy: ${buyTx}")

            val zeroAmount = ExchangeAmount(0.0, sellTransaction.assetAmount.unit)
            if (remainingSellAmount <= zeroAmount) break

            // Get available amount for this buy transaction
            val availableBuyAmount = transactionTracker.getAvailableAmount(buyTx)
            if (availableBuyAmount.amount <= 0.0) continue  // Skip if no amount available

            val amountToUse = minOf(remainingSellAmount, availableBuyAmount)

            // Calculate the ratio of the amount being used to the total buy amount
            val ratio = (amountToUse.amount / buyTx.assetAmount.amount)
            // Use the ratio to calculate cost basis
            val costBasis = (buyTx.transactionAmountFiat + buyTx.fee) * ratio

            // Calculate holding period
            val holdingPeriodMillis = Duration.between(buyTx.timestamp, sellTransaction.timestamp).toMillis()
            val holdingPeriodDays = holdingPeriodMillis / (1000 * 60 * 60 * 24)
            val taxType = if (holdingPeriodDays >= 365) TaxType.LONG_TERM else TaxType.SHORT_TERM

            usedBuyTransactions.add(
                UsedBuyTransaction(
                    transactionId = buyTx.id,
                    amountUsed = amountToUse,
                    costBasis = costBasis,
                    taxType = taxType,
                    originalTransaction = buyTx,
                )
            )

            // Update used amount for this buy transaction
            transactionTracker.updateUsedAmount(buyTx.id, amountToUse)

            totalCostBasis = totalCostBasis + costBasis
            remainingSellAmount = remainingSellAmount - amountToUse

            logger.info("Total cost basis so far: $totalCostBasis")
            logger.info("Remaining sell: $remainingSellAmount")
        }

        // Calculate uncovered amount and value
        val uncoveredSellAmount = if (remainingSellAmount.amount > 0.0001) {
            remainingSellAmount
        } else {
            ExchangeAmount(0.0, sellTransaction.assetAmount.unit)
        }

        // Calculate the proportion of the total sell value that's uncovered
        val uncoveredRatio = uncoveredSellAmount.amount / sellTransaction.assetAmount.amount
        // Use the ratio to calculate uncovered value
        val uncoveredSellValue = sellTransaction.transactionAmountFiat * uncoveredRatio

        // Add the uncovered portion directly to the gain (cost basis = 0)
        val proceeds = sellTransaction.transactionAmountFiat
        val gain = proceeds - totalCostBasis

        return TaxableEventResult(
            sellTransactionId = sellTransaction.id,
            proceeds = proceeds,
            costBasis = totalCostBasis,
            gain = gain,
            sellTransaction = sellTransaction,
            usedBuyTransactions = usedBuyTransactions,
            uncoveredSellAmount = uncoveredSellAmount,
            uncoveredSellValue = uncoveredSellValue
        )
    }
}
