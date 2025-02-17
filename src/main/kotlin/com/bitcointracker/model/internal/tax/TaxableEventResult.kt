package com.bitcointracker.model.internal.tax

/**
 * Contains the results of processing a single taxable event.
 *
 * @property sellTransactionId ID of the processed sell transaction
 * @property proceeds Total proceeds from the sale
 * @property costBasis Total cost basis of the used buy transactions
 * @property gain Net gain or loss from the sale
 * @property usedBuyTransactions List of buy transactions used and their allocation
 */
data class TaxableEventResult(
    val sellTransactionId: String,
    val proceeds: Double,
    val costBasis: Double,
    val gain: Double,
    val usedBuyTransactions: List<UsedBuyTransaction>
)
