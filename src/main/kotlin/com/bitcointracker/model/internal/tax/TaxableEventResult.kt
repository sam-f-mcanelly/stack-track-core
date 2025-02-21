package com.bitcointracker.model.internal.tax

import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction

/**
 * Contains the results of processing a single taxable event.
 *
 * @property sellTransactionId ID of the processed sell transaction
 * @property proceeds Total proceeds from the sale
 * @property costBasis Total cost basis of the used buy transactions
 * @property gain Net gain or loss from the sale
 * @property sellTransaction Normalized transaction for the sell
 * @property usedBuyTransactions List of buy transactions used and their allocation
 */
data class TaxableEventResult(
    val sellTransactionId: String,
    val proceeds: Double,
    val costBasis: Double,
    val gain: Double,
    val sellTransaction: NormalizedTransaction,
    val usedBuyTransactions: List<UsedBuyTransaction>,
)
