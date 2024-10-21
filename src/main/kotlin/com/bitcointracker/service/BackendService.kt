package com.bitcointracker.service

import com.bitcointracker.core.NormalizedTransactionAnalyzer
import com.bitcointracker.core.TransactionCache
import com.bitcointracker.core.parser.UniversalFileLoader
import com.bitcointracker.external.client.CoinbaseClient
import com.bitcointracker.model.report.ProfitStatement
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import org.slf4j.LoggerFactory
import javax.inject.Inject

interface IBackendService {
    fun loadInput(input: List<String>)
    fun getCurrentPrice(asset: String, currency: String): Double
    fun getTransactions(): List<NormalizedTransaction>
    fun getProfitStatement(): ProfitStatement
    fun getAccumulation(days: Int, asset: String): List<Double>
}

class BackendService @Inject constructor(
    private val fileLoader: UniversalFileLoader,
    private val transactionAnalyzer: NormalizedTransactionAnalyzer,
    private val coinbaseClient: CoinbaseClient
) : IBackendService {

    companion object {
        // TODO make this stupid thing work
        private val logger = LoggerFactory.getLogger(BackendService::class.java)
    }

    override fun loadInput(input: List<String>) {
        println("Parsing transactions...")
        val transactions = input.flatMap {
            fileLoader.loadFromFileContents(it)
        }

        TransactionCache.addTransactions(transactions)
    }

    override fun getTransactions(): List<NormalizedTransaction> {
        println("Checking for sells...")
        println("Checking for 276586080: ${TransactionCache.getTransactionById("276586080")}")
        println("Checking for 276586088: ${TransactionCache.getTransactionById("276586088")}")
        println("Checking for 338463662: ${TransactionCache.getTransactionById("338463662")}")
        println("Checking for 338463663: ${TransactionCache.getTransactionById("338463663")}")
        return TransactionCache.getAllTransactions()
    }

    override fun getCurrentPrice(asset: String, currency: String): Double {
        coinbaseClient.getCurrentPrice(asset, currency)?.let {
            return it
        }
        return 55000.0
    }

    override fun getAccumulation(days: Int, asset: String): List<Double> {
        return transactionAnalyzer.getAccumulation(days, asset).map {
            it.amount
        }
    }

    override fun getProfitStatement(): ProfitStatement {
        val bitcoinPrice = coinbaseClient.getCurrentPrice("BTC", "USD") ?: 15000.0
        return transactionAnalyzer.computeTransactionResults(
            "BTC",
            ExchangeAmount(bitcoinPrice, "USD"),
        )
    }
}