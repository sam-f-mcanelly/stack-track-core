package com.bitcointracker.core.parser

import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.strike.StrikeTransaction
import com.bitcointracker.model.transaction.strike.StrikeTransactionSource
import com.bitcointracker.model.transaction.strike.StrikeTransactionState
import com.bitcointracker.model.transaction.strike.StrikeTransactionType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class StrikeAccountStatementFileLoader @Inject constructor(): FileLoader<StrikeTransaction> {

    override fun readCsv(fileContents: String): List<StrikeTransaction> {
        val dateFormatter = SimpleDateFormat("MMM dd yyyy")
        val timeFormatter = SimpleDateFormat("HH:mm:ss")

        return fileContents.split("\n").map { line ->
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
                assetValue = columns[12].toDoubleOrNull()?.let { ExchangeAmount(it, "BTC") },
                balance = columns[14].toDoubleOrNull()?.let { ExchangeAmount(it, columns[15].uppercase(Locale.ROOT)) },
                balanceBtc = columns[15].toDoubleOrNull()?.let { ExchangeAmount(it, "BTC") },
                destination = if (columns.size > 16) columns[16] else null,
                description = if (columns.size > 17) columns[17] else ""
            )
        }.toList()
    }
}