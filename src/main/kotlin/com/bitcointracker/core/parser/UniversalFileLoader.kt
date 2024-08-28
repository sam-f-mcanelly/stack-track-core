package com.bitcointracker.core.parser

import com.bitcointracker.core.mapper.CoinbaseFillsNormalizingMapper
import com.bitcointracker.core.mapper.StrikeTransactionNormalizingMapper
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import java.io.File
import javax.inject.Inject

class UniversalFileLoader @Inject constructor(
    private val strikeTransactionNormalizingMapper: StrikeTransactionNormalizingMapper,
    private val strikeAccountAnnualStatementFileLoader: StrikeAccountAnnualStatementFileLoader,
    private val strikeAccountStatementFileLoader: StrikeAccountStatementFileLoader,
    private val coinbaseFillsNormalizingMapper: CoinbaseFillsNormalizingMapper,
    private val coinbaseProFillsFileLoader: CoinbaseProFillsFileLoader,
) {
    fun loadFiles(files: List<File>): List<NormalizedTransaction>
        = files.map { loadFile(it) }
            .flatMap { it }
            .toList()

    fun loadFile(file: File): List<NormalizedTransaction> {
        println("Loading file: ${file.absolutePath}")
        return if (file.name.contains("annual transactions")) {
            println("Loading a strike annual statement...")
            val transactions = strikeAccountAnnualStatementFileLoader.readCsv(loadLocalFile(file, 1))
            strikeTransactionNormalizingMapper.normalizeTransactions(transactions)
        } else if (file.name.contains("Account statement")) {
            println("Loading a strike monthly statement...")
            strikeTransactionNormalizingMapper.normalizeTransactions(
                strikeAccountStatementFileLoader.readCsv(loadLocalFile(file, 1))
            )
        } else if (file.name.contains("_fills")) {
            println("Loading a coinbase fills report...")
            coinbaseFillsNormalizingMapper.normalizeTransactions(
                coinbaseProFillsFileLoader.readCsv(loadLocalFile(file, 1))
            )
        } else {
            println("Ignoring unsupported file: " + file.name)
            listOf()
        }
    }

    // Drop 4 for coinbase standard, 1 otherwise
    fun loadLocalFile(file: File, numLinesToDrop: Int): String {
        return file.useLines { lines ->
            lines.drop(numLinesToDrop)
        }.joinToString { "\n" }
    }
}
