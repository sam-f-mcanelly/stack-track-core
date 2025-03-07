package com.bitcointracker.service.manager

import com.bitcointracker.core.NormalizedTransactionAnalyzer
import com.bitcointracker.core.cache.TransactionMetadataCache
import com.bitcointracker.external.client.CoinbaseClient
import com.bitcointracker.model.api.AssetHoldingsReport
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import javax.inject.Inject

/**
 * Manages cryptocurrency metadata including price information and portfolio analytics.
 * Acts as an intermediary between external data sources and internal transaction analysis.
 */
class MetadataManager @Inject constructor(
    private val transactionAnalyzer: NormalizedTransactionAnalyzer,
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
        return transactionAnalyzer.getAccumulation(days, asset).map {
            it.amount
        }
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
}