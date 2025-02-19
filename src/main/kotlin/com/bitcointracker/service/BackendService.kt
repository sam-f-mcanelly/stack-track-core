package com.bitcointracker.service

import com.bitcointracker.core.NormalizedTransactionAnalyzer
import com.bitcointracker.core.cache.TransactionMetadataCache
import com.bitcointracker.external.client.CoinbaseClient
import com.bitcointracker.model.api.AssetHoldingsReport
import com.bitcointracker.model.internal.report.ProfitStatement
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import org.slf4j.LoggerFactory
import javax.inject.Inject

interface IBackendService {
    fun getCurrentPrice(asset: String, currency: String): Double
    suspend fun getProfitStatement(): ProfitStatement
    suspend fun getAccumulation(days: Int, asset: String): List<Double>

    suspend fun getPortfolioValue(fiat: String): ExchangeAmount
    suspend fun getAssetHoldings(asset: String, currency: String): AssetHoldingsReport
}

class BackendService @Inject constructor(
    private val transactionAnalyzer: NormalizedTransactionAnalyzer,
    private val coinbaseClient: CoinbaseClient,
    private val transactionCache: TransactionMetadataCache,
) : IBackendService {

    companion object {
        // TODO make this work
        private val logger = LoggerFactory.getLogger(BackendService::class.java)
    }

    override fun getCurrentPrice(asset: String, currency: String): Double {
        coinbaseClient.getCurrentPrice(asset, currency)?.let {
            return it
        }
        return 55000.0
    }

    override suspend fun getAccumulation(days: Int, asset: String): List<Double> {
        return transactionAnalyzer.getAccumulation(days, asset).map {
            it.amount
        }
    }

    override suspend fun getPortfolioValue(fiat: String): ExchangeAmount =
        ExchangeAmount(
            transactionCache.getAllAssetAmounts()
                .map {
                    (coinbaseClient.getCurrentPrice(it.unit, fiat) ?: 0.0) * it.amount
                }.sumOf {
                    it
                },
            fiat
        )

    override suspend fun getAssetHoldings(asset: String, currency: String): AssetHoldingsReport {
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


    override suspend fun getProfitStatement(): ProfitStatement {
        val bitcoinPrice = coinbaseClient.getCurrentPrice("BTC", "USD") ?: 15000.0
        return transactionAnalyzer.computeTransactionResults(
            "BTC",
            ExchangeAmount(bitcoinPrice, "USD"),
        )
    }
}