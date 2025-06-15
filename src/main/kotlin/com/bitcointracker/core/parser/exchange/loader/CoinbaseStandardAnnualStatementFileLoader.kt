package com.bitcointracker.core.parser.exchange.loader

import com.bitcointracker.core.parser.FileLoader
import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseStandardTransaction
import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseTransactionType
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import jakarta.inject.Inject

class CoinbaseStandardAnnualStatementFileLoader @Inject constructor() : FileLoader<CoinbaseStandardTransaction> {
    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss 'UTC'"
    }

    override fun readCsv(lines: List<String>): List<CoinbaseStandardTransaction>  {
        val dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.ENGLISH)
            .withZone(ZoneOffset.UTC)

        return lines
            .filter {
                if (it.split(",").size < 10) {
                    println("Skipping invalid line: $it")
                    false
                } else {
                    true
                }
            }
            .map { line ->
                val columns = line.split(",")
                val timestamp = parseDate(columns[1], dateFormatter)

                CoinbaseStandardTransaction(
                    id = columns[0],
                    timestamp = timestamp,
                    type = CoinbaseTransactionType.valueOf(columns[2].uppercase(Locale.ROOT).replace(" ", "_")),
                    quantityTransacted = ExchangeAmount(columns[4].toDouble(), columns[3]),
                    // TODO support other currencies
                    assetValue = ExchangeAmount(columns[6].replace("$", "").toDouble(), columns[5]),
                    transactionAmount = ExchangeAmount(columns[7].replace("$", "").toDouble(), "USD"),
                    fee = ExchangeAmount(columns[9].replace("$", "").toDouble(), "USD"),
                    notes = columns[10]
                )
            }.toList()
    }
}