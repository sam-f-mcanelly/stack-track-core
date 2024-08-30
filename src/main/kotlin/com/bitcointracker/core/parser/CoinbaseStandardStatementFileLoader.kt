package com.bitcointracker.core.parser

import com.bitcointracker.model.transaction.coinbase.CoinbaseStandardTransaction
import com.bitcointracker.model.transaction.coinbase.CoinbaseTransactionType
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CoinbaseStandardStatementFileLoader : FileLoader<CoinbaseStandardTransaction> {
    override fun readCsv(lines: List<String>): List<CoinbaseStandardTransaction>  {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

        return lines
            .filter {
                // TODO update
                if (it.split(",").size < 5) {
                    println("Skipping invalid line: $it")
                    false
                } else {
                    true
                }
            }
            .map { line ->
                val columns = line.split(",")
                val timestamp = dateFormatter.parse(columns[1])

                CoinbaseStandardTransaction(
                    id = columns[0],
                    timestamp = Date(),
                    type = CoinbaseTransactionType.DEPOSIT,
                    quantityTransacted = ExchangeAmount(0.0, "USD"),
                    assetValue = ExchangeAmount(0.0, "USD"),
                    transactionAmount = ExchangeAmount(0.0, "USD"),
                    fee = ExchangeAmount(0.0, "USD"),
                    notes = "Notes"
                )
            }.toList()
    }
}