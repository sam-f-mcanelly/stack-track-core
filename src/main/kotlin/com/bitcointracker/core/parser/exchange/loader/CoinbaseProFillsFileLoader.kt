package com.bitcointracker.core.parser.exchange.loader

import com.bitcointracker.core.parser.FileLoader
import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseFillsSide
import com.bitcointracker.model.internal.transaction.coinbase.CoinbaseFillsTransaction
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class CoinbaseProFillsFileLoader @Inject constructor(): FileLoader<CoinbaseFillsTransaction> {
    override fun readCsv(lines: List<String>): List<CoinbaseFillsTransaction> {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

        return lines
            .filter {
                if (it.split(",").size < 11) {
                    println("Skipping invalid line: $it")
                    false
                } else {
                    true
                }
            }
            .map { line ->
                val columns = line.split(",")
                val timestamp = dateFormatter.parse(columns[4])

                CoinbaseFillsTransaction(
                    portfolio = columns[0],
                    tradeId = columns[1],
                    product = columns[2],
                    side = CoinbaseFillsSide.valueOf(columns[3].uppercase(Locale.ROOT)),
                    createdAt = Date(timestamp.time),
                    size = ExchangeAmount(columns[5].toDouble(), columns[6]),
                    price = ExchangeAmount(columns[7].toDouble(), columns[10]),
                    fee = ExchangeAmount(columns[8].toDouble(), columns[10]),
                    total = ExchangeAmount(columns[9].toDouble(), columns[10])
                )
            }.toList()
    }
}