package com.bitcointracker.core.parser.exchange.loader

import com.bitcointracker.core.parser.FileLoader
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.strike.StrikeTransaction
import com.bitcointracker.model.internal.transaction.strike.StrikeTransactionSource
import com.bitcointracker.model.internal.transaction.strike.StrikeTransactionState
import com.bitcointracker.model.internal.transaction.strike.StrikeTransactionType
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class StrikeAccountStatementFileLoader @Inject constructor(): FileLoader<StrikeTransaction> {
    companion object {
        private val logger = LoggerFactory.getLogger(StrikeAccountStatementFileLoader::class.java)
    }

    override fun readCsv(lines: List<String>): List<StrikeTransaction> {
        val dateFormatter = SimpleDateFormat("MMM dd yyyy")
        val timeFormatter = SimpleDateFormat("HH:mm:ss")

        val unfilteredTransactions = lines
            .filter {
                if (it.split(",").size < 15) {
                    println("Skipping invalid line: $it")
                    false
                } else {
                    true
                }
            }
            .map { line ->
                val columns = line.split(",")
                val datePart = dateFormatter.parse(columns[1]) // Parsing "Jan 01 2024"
                val timePart = timeFormatter.parse(columns[2]) // Parsing "13:30:07"

                // Combine date and time into a single Date object
                val dateTime = Date(datePart.time + timePart.time)

                StrikeTransaction(
                    transactionId = columns[0],
                    date = dateTime,
                    type = StrikeTransactionType.valueOf(columns[5].uppercase(Locale.ROOT).replace(" ", "_")),
                    state = StrikeTransactionState.valueOf(columns[6].uppercase(Locale.ROOT).replace(" ", "_")),
                    source = StrikeTransactionSource.MONTHLY_STATEMENT,
                    fee = columns[9].toDoubleOrNull()?.let { ExchangeAmount(it, columns[8]) },
                    asset1 = columns[7].toDoubleOrNull()?.let { ExchangeAmount(it, columns[8]) },
                    asset2 = columns[10].toDoubleOrNull()?.let { ExchangeAmount(it, columns[11]) },
                    assetValue = columns[13].toDoubleOrNull()?.let { ExchangeAmount(it, "USD") },
                    balance = columns[14].toDoubleOrNull()
                        ?.let { ExchangeAmount(it, columns[15].uppercase(Locale.ROOT)) },
                    balanceBtc = columns[15].toDoubleOrNull()?.let { ExchangeAmount(it, "BTC") },
                    destination = if (columns.size > 16) columns[17] else null,
                    description = "" // TODO: support this? I've never used it
                )
            }.toList()

        return filter(unfilteredTransactions)
    }

    /**
     * Special logic for handling transactions that have been reversed on Strike.
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