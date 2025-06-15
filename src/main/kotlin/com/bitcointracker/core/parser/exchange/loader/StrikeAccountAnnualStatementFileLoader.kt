package com.bitcointracker.core.parser.exchange.loader

import com.bitcointracker.core.parser.FileLoader
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.strike.StrikeTransaction
import com.bitcointracker.model.internal.transaction.strike.StrikeTransactionSource
import com.bitcointracker.model.internal.transaction.strike.StrikeTransactionState
import com.bitcointracker.model.internal.transaction.strike.StrikeTransactionType
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import jakarta.inject.Inject

class StrikeAccountAnnualStatementFileLoader @Inject constructor(): FileLoader<StrikeTransaction> {
    companion object {
        private const val DATE_FORMAT = "MMM dd yyyy HH:mm:ss"
    }

    override fun readCsv(lines: List<String>): List<StrikeTransaction> {
        val dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.ENGLISH)
            .withZone(ZoneOffset.UTC)

        return filter(
            lines.filter {
                if (it.split(",").size < 13) {
                    println("Skipping invalid line: $it")
                    false
                } else {
                    true
                }
            }
            .map { line ->
                val columns = line.split(",")
                val date = parseDate(columns[1], dateFormatter) // Parsing "Jan 01 2024 13:30:07"

                StrikeTransaction(
                    transactionId = columns[0],
                    date = date,
                    type = StrikeTransactionType.valueOf(columns[3].uppercase(Locale.ROOT).replace(" ", "_")),
                    state = StrikeTransactionState.valueOf(columns[4].uppercase(Locale.ROOT).replace(" ", "_")),
                    source = StrikeTransactionSource.ANNUAL_STATEMENT,
                    fee = ExchangeAmount(0.0, "USD"),
                    // TODO remove nullability
                    asset1 = columns[5].toDoubleOrNull()?.let { ExchangeAmount(it, columns[6]) },
                    asset2 = columns[7].toDoubleOrNull()?.let { ExchangeAmount(it, columns[8]) },
                    assetValue = columns[9].toDoubleOrNull()?.let { ExchangeAmount(it, "USD") },
                    balance = columns[10].toDoubleOrNull()
                        ?.let { ExchangeAmount(it, columns[11].uppercase(Locale.ROOT)) },
                    balanceBtc = columns[12].toDoubleOrNull()?.let { ExchangeAmount(it, "BTC") },
                    destination = if (columns.size > 13) columns[13] else null,
                    description = if (columns.size > 14) columns[14] else ""
                )
            }.toList()
        )
    }

    /**
     * Special logic for handling transactions that have been reversed on Strike.
     *
     * TODO put in updated interface for strike
     *
     * @param transactions List of Strike Transactions
     */
    private fun filter(transactions: List<StrikeTransaction>): List<StrikeTransaction> {
        val result = mutableMapOf<String, StrikeTransaction>()

        transactions.forEach { transaction ->
            if (transaction.transactionId in result) {
                val existingTransaction = result[transaction.transactionId]
                if (transaction.state == StrikeTransactionState.REVERSED || existingTransaction!!.state == StrikeTransactionState.REVERSED) {
                    println("Found reversed transaction with id: ${transaction.transactionId}. Ignoring the two transactions")
                    result.remove(transaction.transactionId)
                }
            } else {
                result[transaction.transactionId] = transaction
            }
        }

        return result.values.toList()
    }
}