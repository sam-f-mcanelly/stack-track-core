package com.bitcointracker.core.local

import com.bitcointracker.core.mapper.StrikeTransactionNormalizingMapper
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import java.io.File

class UniversalFileLoader {

    private val strikeTransactionNormalizingMapper = StrikeTransactionNormalizingMapper()
    private val strikeAccountAnnualStatementFileLoader = StrikeAccountAnnualStatementFileLoader()
    private val strikeAccountStatementFileLoader = StrikeAccountStatementFileLoader()

    fun loadFiles(files: List<File>): List<NormalizedTransaction>
        = files.map { loadFile(it) }
            .flatMap { it }
            .toList()

    fun loadFile(file: File): List<NormalizedTransaction> {
        if (file.name.contains("annual transactions")) {
            val transactions = strikeAccountAnnualStatementFileLoader.readCsv(file.absolutePath)
            transactions.forEach { println(it) }
            return strikeTransactionNormalizingMapper.normalizeTransactions(transactions)
        } else if (file.name.contains("Account statement")) {
            return strikeTransactionNormalizingMapper.normalizeTransactions(
                strikeAccountStatementFileLoader.readCsv(file.absolutePath)
            )
        } else {
            throw RuntimeException("Unsupported file type")
        }
    }
}
