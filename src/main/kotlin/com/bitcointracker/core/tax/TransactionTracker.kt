package com.bitcointracker.core.tax

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import org.slf4j.LoggerFactory

/**
 * Extension function for ExchangeAmount to coerce to a minimum value
 */
private fun ExchangeAmount.coerceAtLeast(minValue: Double): ExchangeAmount {
    return if (this.amount < minValue) {
        ExchangeAmount(minValue, this.unit)
    } else {
        this
    }
}

/**
 * Tracks and manages used buy transactions across a tax report.
 * Created for each tax report processing session.
 */
class TransactionTracker(
    private val usedBuyAmounts: MutableMap<String, ExchangeAmount>,
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(TransactionTracker::class.java)
    }

    /**
     * Gets the available (unused) amount for a buy transaction.
     *
     * @param buyTransaction The buy transaction to check
     * @return The available amount that can be used for sells
     */
    fun getAvailableAmount(buyTransaction: NormalizedTransaction): ExchangeAmount {
        val usedAmount = usedBuyAmounts[buyTransaction.id]
        return if (usedAmount == null) {
            // No usage recorded yet, full amount available
            buyTransaction.assetAmount
        } else {
            // Some amount has been used, calculate remaining using operators
            (buyTransaction.assetAmount - usedAmount).coerceAtLeast(0.0)
        }
    }

    /**
     * Updates the used amount for a buy transaction.
     *
     * @param buyTransactionId The ID of the buy transaction
     * @param amountUsed The additional amount being used
     */
    fun updateUsedAmount(buyTransactionId: String, amountUsed: ExchangeAmount) {
        val currentUsed = usedBuyAmounts[buyTransactionId]
        if (currentUsed == null) {
            // No usage recorded yet
            usedBuyAmounts[buyTransactionId] = amountUsed
        } else {
            // Add to existing usage using proper + operator
            usedBuyAmounts[buyTransactionId] = currentUsed + amountUsed
        }
        logger.info("Updated used amount for $buyTransactionId: ${usedBuyAmounts[buyTransactionId]}")
    }
}
