package com.bitcointracker.core.local

import com.bitcointracker.core.mapper.CoinbaseFillsNormalizingMapper
import com.bitcointracker.core.mapper.StrikeTransactionNormalizingMapper
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import java.io.File

class UniversalFileLoader {

    /**
     * Strike dependencies
     */
    private val strikeTransactionNormalizingMapper = StrikeTransactionNormalizingMapper()
    private val strikeAccountAnnualStatementFileLoader = StrikeAccountAnnualStatementFileLoader()
    private val strikeAccountStatementFileLoader = StrikeAccountStatementFileLoader()

    /**
     * Coinbase dependencies
     */
    private val coinbaseFillsNormalizingMapper = CoinbaseFillsNormalizingMapper()
    private val coinbaseFillsFileLoader = CoinbaseFillsFileLoader()

    fun loadFiles(files: List<File>): List<NormalizedTransaction>
        = files.map { loadFile(it) }
            .flatMap { it }
            .toList()

    fun loadFile(file: File): List<NormalizedTransaction> {
        return if (file.name.contains("annual transactions")) {
            println("Loading a strike annual statement...")
            val transactions = strikeAccountAnnualStatementFileLoader.readCsv(file.absolutePath)
            transactions.forEach { println(it) }
            strikeTransactionNormalizingMapper.normalizeTransactions(transactions)
        } else if (file.name.contains("Account statement")) {
            println("Loading a strike monthly statement...")
            strikeTransactionNormalizingMapper.normalizeTransactions(
                strikeAccountStatementFileLoader.readCsv(file.absolutePath)
            )
        } else if (file.name.contains("_fills")) {
            println("Loading a coinbase fills report...")
            coinbaseFillsNormalizingMapper.normalizeTransactions(
                coinbaseFillsFileLoader.readCsv(file.absolutePath)
            )
        } else {
            println("Ignoring unsupported file: " + file.name)
            listOf()
        }
    }
}
