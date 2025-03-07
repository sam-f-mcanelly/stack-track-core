package com.bitcointracker.core.parser.exchange.processor

import com.bitcointracker.core.parser.exchange.mapper.CoinbaseProFillsNormalizingMapper
import com.bitcointracker.core.parser.exchange.mapper.CoinbaseStandardTransactionNormalizingMapper
import com.bitcointracker.core.parser.exchange.mapper.StrikeTransactionNormalizingMapper
import com.bitcointracker.core.parser.exchange.loader.CoinbaseProFillsFileLoader
import com.bitcointracker.core.parser.exchange.loader.CoinbaseStandardAnnualStatementFileLoader
import com.bitcointracker.core.parser.exchange.loader.StrikeAccountAnnualStatementFileLoader
import com.bitcointracker.core.parser.exchange.loader.StrikeAccountStatementFileLoader
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import javax.inject.Inject

/**
 * FileProcessor implementation for Strike annual statements.
 */
class StrikeAnnualFileProcessor @Inject constructor(
    private val fileLoader: StrikeAccountAnnualStatementFileLoader,
    private val normalizer: StrikeTransactionNormalizingMapper
) : FileProcessor {
    override fun processFile(fileLines: List<String>): List<NormalizedTransaction> =
        normalizer.normalizeTransactions(fileLoader.readCsv(fileLines))
}

/**
 * FileProcessor implementation for Strike monthly statements.
 */
class StrikeMonthlyFileProcessor @Inject constructor(
    private val fileLoader: StrikeAccountStatementFileLoader,
    private val normalizer: StrikeTransactionNormalizingMapper
) : FileProcessor {
    override fun processFile(fileLines: List<String>): List<NormalizedTransaction> =
        normalizer.normalizeTransactions(fileLoader.readCsv(fileLines))
}

/**
 * FileProcessor implementation for Coinbase Pro fills.
 */
class CoinbaseProFillsFileProcessor @Inject constructor(
    private val fileLoader: CoinbaseProFillsFileLoader,
    private val normalizer: CoinbaseProFillsNormalizingMapper
) : FileProcessor {
    override fun processFile(fileLines: List<String>): List<NormalizedTransaction> =
        normalizer.normalizeTransactions(fileLoader.readCsv(fileLines))
}

/**
 * FileProcessor implementation for Coinbase annual statements.
 */
class CoinbaseAnnualFileProcessor @Inject constructor(
    private val fileLoader: CoinbaseStandardAnnualStatementFileLoader,
    private val normalizer: CoinbaseStandardTransactionNormalizingMapper
) : FileProcessor {
    override fun processFile(fileLines: List<String>): List<NormalizedTransaction> =
        normalizer.normalizeTransactions(fileLoader.readCsv(fileLines))
}