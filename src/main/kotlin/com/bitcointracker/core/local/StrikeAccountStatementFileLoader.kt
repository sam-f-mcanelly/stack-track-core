package com.bitcointracker.core.local

import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.strike.StrikeTransaction
import com.bitcointracker.model.transaction.strike.StrikeTransactionState
import com.bitcointracker.model.transaction.strike.StrikeTransactionType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
// TODO remove this
import java.util.*

class StrikeAccountStatementFileLoader {

    fun readStrikeAccountStatementCsv(fileLocation: String): List<StrikeTransaction> {
        val dateFormatter = SimpleDateFormat("MMM dd yyyy")
        val timeFormatter = SimpleDateFormat("HH:mm:ss")

        return File(fileLocation).useLines { lines ->
            lines.drop(1)
                .map { line ->
                    System.out.println("Line: \n" + line)
                    val columns = line.split(",")
                    val datePart = dateFormatter.parse(columns[1]) // Parsing "Jan 01 2024"
                    val timePart = timeFormatter.parse(columns[2]) // Parsing "13:30:07"

                    System.out.println("datePart: " + datePart)
                    System.out.println("timePart: " + timePart)
        
                    // Combine date and time into a single Date object
                    val dateTime = Date(datePart.time + timePart.time)

                    // TODO: handle deposits that have no asset out
                    // TODO: handle on-chain transactions

                    StrikeTransaction(
                        transactionId = columns[0],
                        date = dateTime,
                        type = StrikeTransactionType.valueOf(columns[5].uppercase(Locale.ROOT)),
                        state = StrikeTransactionState.valueOf(columns[6].uppercase(Locale.ROOT)),
                        fee = columns[9].toDoubleOrNull()?.let { ExchangeAmount(it, columns[8]) },
                        assetOut = columns[7].toDoubleOrNull()?.let { ExchangeAmount(it, columns[8]) },
                        assetIn = columns[10].toDoubleOrNull()?.let { ExchangeAmount(it, columns[11]) },
                        assetValue = columns[12].toDoubleOrNull()?.let { ExchangeAmount(it, "BTC") },
                        balance = columns[15].toDoubleOrNull()?.let { ExchangeAmount(it, "BTC") },
                        balanceBtc = columns[15].toDoubleOrNull()?.let { ExchangeAmount(it, "BTC") },
                        destination = if (columns.size > 16) columns[16] else null,
                        description = if (columns.size > 17) columns[17] else ""
                    )
                }.toList()
        }
    }
}