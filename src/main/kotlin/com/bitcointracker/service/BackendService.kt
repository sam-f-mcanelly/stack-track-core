package com.bitcointracker.service

import com.bitcointracker.core.NormalizedTransactionAnalyzer
import com.bitcointracker.core.TransactionRepository
import com.bitcointracker.core.cache.TransactionMetadataCache
import com.bitcointracker.core.parser.UniversalFileLoader
import com.bitcointracker.external.client.CoinbaseClient
import com.bitcointracker.model.api.AssetHoldingsReport
import com.bitcointracker.model.internal.report.ProfitStatement
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import javax.inject.Inject

interface IBackendService {
    suspend fun loadInput(input: List<String>)
    fun getCurrentPrice(asset: String, currency: String): Double
    suspend fun getTransactions(): List<NormalizedTransaction>
    suspend fun getProfitStatement(): ProfitStatement
    suspend fun getAccumulation(days: Int, asset: String): List<Double>

    suspend fun getPortfolioValue(fiat: String): ExchangeAmount
    suspend fun getAssetHoldings(asset: String, currency: String): AssetHoldingsReport
}

class BackendService @Inject constructor(
    private val fileLoader: UniversalFileLoader,
    private val transactionAnalyzer: NormalizedTransactionAnalyzer,
    private val coinbaseClient: CoinbaseClient,
    private val transactionRepository: TransactionRepository,
    private val transactionCache: TransactionMetadataCache,
) : IBackendService {

    companion object {
        // TODO make this work
        private val logger = LoggerFactory.getLogger(BackendService::class.java)
    }

    override suspend fun loadInput(input: List<String>) {
        // Parse files in parallel
        println("Loading files in parallel")
        val parsedTransactions = runBlocking {
            input.map { fileContent ->
                async(Dispatchers.IO) {
                    fileLoader.loadFromFileContents(fileContent)
                }
            }.awaitAll()
        }

        // Flatten the results and insert into database
        val allTransactions = parsedTransactions.flatten()

        // Optional: Process in batches if dealing with large datasets
        println("Adding files to the database in chunks")
        val batchSize = 1000
        allTransactions.chunked(batchSize).forEach { batch ->
            transactionRepository.addTransactions(batch)
        }

        println("Successfully processed ${allTransactions.size} transactions")
    }

    override suspend fun getTransactions(): List<NormalizedTransaction> {
        println("Checking for sells...")
        println("Checking for 276586080: ${transactionRepository.getTransactionById("276586080")}")
        println("Checking for 276586088: ${transactionRepository.getTransactionById("276586088")}")
        println("Checking for 338463662: ${transactionRepository.getTransactionById("338463662")}")
        println("Checking for 338463663: ${transactionRepository.getTransactionById("338463663")}")
        return transactionRepository.getAllTransactions()
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