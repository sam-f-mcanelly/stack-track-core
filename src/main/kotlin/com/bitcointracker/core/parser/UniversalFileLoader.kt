package com.bitcointracker.core.parser

import com.bitcointracker.core.mapper.CoinbaseProFillsNormalizingMapper
import com.bitcointracker.core.mapper.CoinbaseStandardTransactionNormalizingMapper
import com.bitcointracker.core.mapper.FileContentNormalizingMapper
import com.bitcointracker.core.mapper.StrikeTransactionNormalizingMapper
import com.bitcointracker.model.file.FileType
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import java.io.File
import javax.inject.Inject

class UniversalFileLoader @Inject constructor(
    private val fileContentNormalizingMapper: FileContentNormalizingMapper,
    private val strikeTransactionNormalizingMapper: StrikeTransactionNormalizingMapper,
    private val strikeAccountAnnualStatementFileLoader: StrikeAccountAnnualStatementFileLoader,
    private val strikeAccountStatementFileLoader: StrikeAccountStatementFileLoader,
    private val coinbaseProFillsNormalizingMapper: CoinbaseProFillsNormalizingMapper,
    private val coinbaseProFillsFileLoader: CoinbaseProFillsFileLoader,
    private val coinbaseStandardTransactionNormalizingMapper: CoinbaseStandardTransactionNormalizingMapper,
    private val coinbaseAnnualFileLoader: CoinbaseStandardAnnualStatementFileLoader,
) {
    fun loadFiles(files: List<File>): List<NormalizedTransaction>
        = files.map { loadFile(it) }
            .flatMap { it }
            .toList()

    fun loadFile(file: File): List<NormalizedTransaction> {
        println("Loading file: ${file.absolutePath}")
        return loadFromFileContents(loadLocalFile(file))
    }

    fun loadFromFileContents(contents: String): List<NormalizedTransaction> {
        val normalizedFile = fileContentNormalizingMapper.normalizeFile(contents)
        return when (normalizedFile.fileType) {
            FileType.STRIKE_ANNUAL -> {
                println("Loading a strike annual statement...")
                val transactions = strikeAccountAnnualStatementFileLoader.readCsv(normalizedFile.fileLines)
                strikeTransactionNormalizingMapper.normalizeTransactions(transactions)
            }
            FileType.STRIKE_MONTHLY -> {
                println("Loading a strike monthly statement...")
                strikeTransactionNormalizingMapper.normalizeTransactions(
                    strikeAccountStatementFileLoader.readCsv(normalizedFile.fileLines)
                )
            }
            FileType.COINBASE_PRO_FILLS -> {
                println("Loading a coinbase fills report...")
                coinbaseProFillsNormalizingMapper.normalizeTransactions(
                    coinbaseProFillsFileLoader.readCsv(normalizedFile.fileLines)
                )
            }
            FileType.COINBASE_ANNUAL -> {
                println("Loading a coinbase annual report...")
                coinbaseStandardTransactionNormalizingMapper.normalizeTransactions(
                    coinbaseAnnualFileLoader.readCsv(normalizedFile.fileLines)
                )
            }
            else -> {
                println("Ignoring unsupported file: ")
                listOf()
            }
        }
    }

    // Drop 4 for coinbase standard, 1 otherwise
    fun loadLocalFile(file: File): String {
        return file.useLines { lines ->
            lines
        }.joinToString { "\n" }
    }
}
