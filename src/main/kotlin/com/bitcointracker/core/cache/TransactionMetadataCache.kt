package com.bitcointracker.core.cache

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransactionType
import java.lang.IllegalArgumentException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A singleton cache that maintains the current state of cryptocurrency holdings based on transaction history.
 * This cache tracks the total amount held for each cryptocurrency asset by processing buy and sell transactions.
 *
 * The cache maintains:
 * - A count of all processed transactions
 * - A mapping of asset symbols to their current holdings
 *
 * Asset symbols are stored in uppercase format for consistency.
 * All amounts are stored as [ExchangeAmount] objects which include both the amount and the currency unit.
 */
@Singleton
class TransactionMetadataCache @Inject constructor() {

    /**
     * The total number of transactions that have been processed by this cache.
     * This count includes all transactions, regardless of their type or success status.
     */
    var transactionCount: Int = 0

    /**
     * Maps cryptocurrency symbols to their current holdings.
     * The key is the uppercase asset symbol (e.g., "BTC", "ETH")
     * The value is an [ExchangeAmount] representing the current amount held.
     */
    var assetToAmountHeld: MutableMap<String, ExchangeAmount> = mutableMapOf()

    /**
     * Updates the cache with a new list of transactions, recalculating all holdings.
     * This method clears the existing cache and processes all transactions from scratch.
     *
     * For each transaction:
     * - BUY transactions increase the held amount for the asset
     * - SELL transactions decrease the held amount for the asset
     *
     * Invalid transactions are logged and skipped to maintain cache integrity.
     *
     * @param transactions List of normalized transactions to process
     * @throws IllegalArgumentException if a transaction contains invalid asset data
     */
    fun update(transactions: List<NormalizedTransaction>) {
        assetToAmountHeld = mutableMapOf()
        transactions.forEach {
            try {
                val asset = it.assetAmount.unit
                if (it.type == NormalizedTransactionType.BUY) {
                    assetToAmountHeld[asset] = assetToAmountHeld.getOrDefault(
                        asset,
                        ExchangeAmount(0.0, asset)
                    ) + it.assetAmount
                } else if (it.type == NormalizedTransactionType.SELL) {
                    assetToAmountHeld[asset] = assetToAmountHeld.getOrDefault(
                        asset,
                        ExchangeAmount(0.0, asset)
                    ) - it.assetAmount
                }
            } catch(e: IllegalArgumentException) {
                println("Error updating the transaction cache when adding the transaction: \n $it")
            }
        }
        transactionCount = transactions.size
        println("Updated transaction count: $transactionCount")
        println("Updated assetToAmountHeld map: $assetToAmountHeld")
    }

    /**
     * Retrieves the current holdings for a specific asset.
     * Asset symbols are case-insensitive as they are converted to uppercase before lookup.
     *
     * @param asset The symbol of the cryptocurrency asset (e.g., "BTC", "ETH")
     * @return [ExchangeAmount] representing the current holdings for the asset.
     *         Returns an [ExchangeAmount] with 0.0 amount if the asset is not found.
     */
    fun getAssetAmount(asset: String): ExchangeAmount =
        assetToAmountHeld.getOrDefault(asset.uppercase(), ExchangeAmount(0.0, asset))

    /**
     * Retrieves a list of all current asset holdings.
     * This includes all assets that have been processed through transactions,
     * even if their current balance is 0.
     *
     * @return List of [ExchangeAmount] objects representing all asset holdings
     */
    fun getAllAssetAmounts(): List<ExchangeAmount> =
        assetToAmountHeld.values.toList().also { println("getting all asset amounts: $it") }
}