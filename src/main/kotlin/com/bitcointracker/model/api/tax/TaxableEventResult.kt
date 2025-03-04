package com.bitcointracker.model.api.tax

import com.bitcointracker.model.api.tax.UsedBuyTransaction
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.api.transaction.NormalizedTransaction

/**
 * Contains the results of processing a single taxable event.
 *
 * @property sellTransactionId ID of the processed sell transaction
 * @property proceeds Total proceeds from the sale
 * @property costBasis Total cost basis of the used buy transactions
 * @property gain Net gain or loss from the sale
 * @property sellTransaction Normalized transaction for the sell
 * @property usedBuyTransactions List of buy transactions used and their allocation
 * @property uncoveredSellAmount Amount of asset that was not covered
 * @property uncoveredSellValue Fiat value of uncovered amounts
 */
data class TaxableEventResult(
    val sellTransactionId: String,
    val proceeds: ExchangeAmount,
    val costBasis: ExchangeAmount,
    val gain: ExchangeAmount,
    val sellTransaction: NormalizedTransaction,
    val usedBuyTransactions: List<UsedBuyTransaction>,
    val uncoveredSellAmount: ExchangeAmount? = null,
    val uncoveredSellValue: ExchangeAmount? = null,
)
