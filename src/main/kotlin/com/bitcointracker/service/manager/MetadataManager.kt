package com.bitcointracker.service.manager

import com.bitcointracker.core.database.TransactionRepository
import com.bitcointracker.core.cache.TransactionMetadataCache
import com.bitcointracker.core.chart.BitcoinDataRepository
import com.bitcointracker.external.client.CoinbaseClient
import com.bitcointracker.model.api.AssetHoldingsReport
import com.bitcointracker.model.api.DailyData
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.NavigableMap
import java.util.TreeMap
import javax.inject.Inject

/**
 * Manages cryptocurrency metadata including price information and portfolio analytics.
 * Acts as an intermediary between external data sources and internal transaction analysis.
 */
class MetadataManager @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val coinbaseClient: CoinbaseClient,
    private val transactionCache: TransactionMetadataCache,
    private val bitcoinDataRepository: BitcoinDataRepository,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MetadataManager::class.java)
    }

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
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val cutoffDate = today.minus(days.toLong(), ChronoUnit.DAYS)

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
            // Calculate day boundaries in UTC
            val dayStart = Instant.now()
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .minusDays((days - 1 - day).toLong())
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()

            val dayEnd = dayStart
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()

            // Filter transactions that occurred on this specific day
            val dayBuys = recentBuys.filter {
                it.timestamp.isAfter(dayStart) && it.timestamp.isBefore(dayEnd)
            }
            val daySells = recentSells.filter {
                it.timestamp.isAfter(dayStart) && it.timestamp.isBefore(dayEnd)
            }

            // Calculate totals for the day
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

    /**
     * Get all addresses pulled from transaction data
     *
     * @param asset Asset (BTC, ETH, etc)
     * @return Set of addresses
     */
    fun getAddresses(asset: String): Set<String> =
        transactionCache.getAddresses(asset)

    /**
     * Retrieves historical data for a specific asset showing daily values and accumulated amounts
     *
     * @param asset The asset symbol to retrieve history for (e.g., "BTC")
     * @return List of daily data points with accumulated asset amounts and values
     */
    fun getHistory(asset: String): List<DailyData> = runBlocking {
        // Get all transactions for the specified asset
        val assetTransactions = transactionRepository.getTransactionsByAsset(asset)

        // If no transactions, return empty list
        if (assetTransactions.isEmpty()) {
            return@runBlocking emptyList()
        }

        // Sort transactions by date
        val sortedTransactions = assetTransactions.sortedBy { it.timestamp }

        // Get date range (from first transaction to today)
        val startDate = sortedTransactions.first().timestamp
        val endDate = Instant.now()

        // Get Bitcoin data for the date range
        val bitcoinData = bitcoinDataRepository.findByDateRange(startDate, endDate)
        logger.info("bitcoinData: $bitcoinData")

        // Build running totals cache by processing all transactions
        val runningTotals = buildRunningTotalsCache(sortedTransactions, asset)

        // Process each Bitcoin data point to create DailyData
        bitcoinData.map { data ->
            // Get cached running total for this date
            val assetAmount = getRunningTotalForDate(runningTotals, data.date, asset)

            // Create DailyData object
            DailyData(
                date = data.date,
                value = data.close, // Use closing price as the value
                assetAmount = assetAmount
            )
        }
    }

    /**
     * Builds a cache of running totals for all transaction dates
     *
     * @param transactions Sorted list of transactions
     * @param asset The asset symbol
     * @return A NavigableMap of dates to running totals
     */
    private fun buildRunningTotalsCache(
        transactions: List<NormalizedTransaction>,
        asset: String
    ): NavigableMap<Instant, ExchangeAmount> {
        val runningTotals = TreeMap<Instant, ExchangeAmount>()
        var runningTotal = ExchangeAmount(0.0, asset)

        // Process transactions in chronological order
        transactions.forEach { transaction ->
            // Update running total based on transaction type
            runningTotal = when (transaction.type) {
                NormalizedTransactionType.BUY -> runningTotal + transaction.assetAmount
                NormalizedTransactionType.SELL -> runningTotal - transaction.assetAmount
                else -> runningTotal // Other transaction types don't affect asset amount
            }

            // Store the updated running total for this date
            runningTotals[transaction.timestamp] = runningTotal
        }

        return runningTotals
    }

    /**
     * Gets the running total for a specific date from the cache
     * If there's no exact match, returns the running total from the closest earlier date
     *
     * @param runningTotals The cache of running totals by date
     * @param date The date to get the running total for
     * @param asset The asset symbol
     * @return The accumulated amount as an ExchangeAmount
     */
    private fun getRunningTotalForDate(
        runningTotals: NavigableMap<Instant, ExchangeAmount>,
        date: Instant,
        asset: String
    ): ExchangeAmount {
        // Try to get the entry for this date or the floor entry (closest earlier date)
        val floorEntry = runningTotals.floorEntry(date)

        logger.info("floor entry: $floorEntry")

        // Return the running total from the floor entry, or zero if no earlier entries
        return floorEntry?.value ?: ExchangeAmount(0.0, asset)
    }
}