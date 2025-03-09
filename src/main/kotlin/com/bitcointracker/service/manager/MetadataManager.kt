package com.bitcointracker.service.manager

import com.bitcointracker.core.database.TransactionRepository
import com.bitcointracker.core.cache.TransactionMetadataCache
import com.bitcointracker.external.client.CoinbaseClient
import com.bitcointracker.model.api.AssetHoldingsReport
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import java.util.Calendar
import javax.inject.Inject

/**
 * Manages cryptocurrency metadata including price information and portfolio analytics.
 * Acts as an intermediary between external data sources and internal transaction analysis.
 */
class MetadataManager @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val coinbaseClient: CoinbaseClient,
    private val transactionCache: TransactionMetadataCache,
) {

    /**
     * Retrieves current price for specified asset in given currency.
     * Falls back to default value if external service unavailable.
     *
     * @param asset The cryptocurrency asset symbol
     * @param currency The fiat currency for pricing
     * @return Current price as a double value
     */
    fun getCurrentPrice(asset: String, currency: String): Double {
        coinbaseClient.getCurrentPrice(asset, currency)?.let {
            return it
        }
        return 55000.0
    }

    /**
     * Gets historical accumulation data for an asset over specified time period.
     *
     * @param days Number of days to analyze
     * @param asset The cryptocurrency asset symbol
     * @return List of daily accumulation amounts
     */
    suspend fun getAccumulation(days: Int, asset: String): List<Double> {
        // Calculate the cutoff date n days ago
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); clear(Calendar.MINUTE); clear(Calendar.SECOND); clear(Calendar.MILLISECOND) }.time
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days)
        }.time

        // Filter transactions that are buys within the last n days
        val recentBuys = transactionRepository.getFilteredTransactions(
            types = listOf(NormalizedTransactionType.BUY),
            assets = listOf(asset),
            startDate = cutoffDate
        ).sortedBy { it.timestamp }
        val recentSells = transactionRepository.getFilteredTransactions(
            types = listOf(NormalizedTransactionType.SELL),
            assets = listOf(asset),
            startDate = cutoffDate
        ).sortedBy { it.timestamp }

        // Initialize the cumulative amounts list
        val cumulativeAmounts = MutableList(days) { 0.0 }
        var totalAmount = 0.0

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
                totalAmount += transaction.assetAmount.amount
            }
            for (transaction in daySells) {
                totalAmount -= transaction.assetAmount.amount
            }

            // Store the cumulative amount for the day
            cumulativeAmounts[day] = totalAmount
        }

        return cumulativeAmounts
    }



    /**
     * Calculates total portfolio value in specified fiat currency.
     *
     * @param fiat The fiat currency for valuation
     * @return Total portfolio value as ExchangeAmount
     */
    fun getPortfolioValue(fiat: String): ExchangeAmount =
        ExchangeAmount(
            transactionCache.getAllAssetAmounts()
                .map {
                    (coinbaseClient.getCurrentPrice(it.unit, fiat) ?: 0.0) * it.amount
                }.sumOf {
                    it
                },
            fiat
        )

    /**
     * Retrieves detailed holdings report for specific asset.
     *
     * @param asset The cryptocurrency asset symbol
     * @param currency The fiat currency for valuation
     * @return Asset holdings with quantity and current value
     */
    fun getAssetHoldings(asset: String, currency: String): AssetHoldingsReport {
        val assetAmount = transactionCache.getAssetAmount(asset)
        val totalValue = coinbaseClient.getCurrentPrice(asset, currency)?.let {
            assetAmount * it
        }
        return AssetHoldingsReport(
            asset = asset,
            assetAmount = assetAmount,
            fiatValue = totalValue,
        )
    }

    fun getAddresses(asset: String): Set<String> =
        transactionCache.getAddresses(asset)
}