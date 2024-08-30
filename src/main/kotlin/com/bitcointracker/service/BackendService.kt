package com.bitcointracker.service

import com.bitcointracker.core.NormalizedTransactionAnalyzer
import com.bitcointracker.core.TransactionCache
import com.bitcointracker.core.mapper.FileContentNormalizingMapper
import com.bitcointracker.core.parser.UniversalFileLoader
import com.bitcointracker.external.client.CoinbaseClient
import com.bitcointracker.model.report.ProfitStatement
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*
import javax.inject.Inject

interface IBackendService {
    fun loadInput(input: List<String>)
    fun getTransactions(): List<NormalizedTransaction>
    fun getFiatGain(days: Int, currency: String, asset: String): List<Double>
    fun getProfitStatement(): ProfitStatement
}

class BackendService @Inject constructor(
    private val fileLoader: UniversalFileLoader,
    private val fileContentNormalizingMapper: FileContentNormalizingMapper,
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
        println("Hello world you fuck")
        return TransactionCache.getAllTransactions()
    }

    override fun getFiatGain(days: Int, currency: String, asset: String): List<Double> {
        return transactionAnalyzer.computeFiatGain(TransactionCache, days, currency, asset)
    }

    override fun getProfitStatement(): ProfitStatement {
        val bitcoinPrice = coinbaseClient.getCurrentPrice("BTC", "USD") ?: 15000.0
        return transactionAnalyzer.computeTransactionResults(
            TransactionCache,
            Date.from(Instant.now()),
            Date.from(Instant.ofEpochSecond(1704067200L)), // Start of 2024. TODO allow input
            "BTC",
            ExchangeAmount(bitcoinPrice, "USD")
        )
    }
}