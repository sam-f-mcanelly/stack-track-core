package com.bitcointracker.model.internal.tax

import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction

/**
 * Details of how a buy transaction was used in tax calculations.
 *
 * @property transactionId ID of the buy transaction
 * @property amountUsed Amount of assets used from this transaction
 * @property costBasis Portion of the original cost basis used
 */
data class UsedBuyTransaction(
    val transactionId: String,
    val amountUsed: Double,
    val costBasis: Double,
    val taxType: TaxType,
    val originalTransaction: NormalizedTransaction
)