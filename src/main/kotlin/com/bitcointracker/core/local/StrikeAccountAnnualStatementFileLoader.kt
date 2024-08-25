package com.bitcointracker.core.local

import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.strike.StrikeTransaction
import com.bitcointracker.model.transaction.strike.StrikeTransactionState
import com.bitcointracker.model.transaction.strike.StrikeTransactionType
import java.io.File
import java.text.SimpleDateFormat
// TODO remove this
import java.util.*

class StrikeAccountAnnualStatementFileLoader {

    fun readStrikeAnnualStatementCsv(fileLocation: String): List<StrikeTransaction> {
        val dateFormatter = SimpleDateFormat("MMM dd yyyy")
        val timeFormatter = SimpleDateFormat("HH:mm:ss")

        return File(fileLocation).useLines { lines ->
            lines.drop(1)
                .map { line ->
                    System.out.println("Line: \n" + line)
                    val columns = line.split(",")
                    val date = dateFormatter.parse(columns[1]) // Parsing "Jan 01 2024 13:30:07"


                    // TODO: handle deposits that have no asset out
                    // TODO: handle on-chain transactions

                    StrikeTransaction(
                        transactionId = columns[0],
                        date = date,
                        type = StrikeTransactionType.valueOf(columns[5].uppercase(Locale.ROOT)),
                        state = StrikeTransactionState.valueOf(columns[6].uppercase(Locale.ROOT)),
                        fee = columns[9].toDoubleOrNull()?.let { ExchangeAmount(it, columns[8]) },
                        asset1 = columns[7].toDoubleOrNull()?.let { ExchangeAmount(it, columns[8]) },
                        asset2 = columns[10].toDoubleOrNull()?.let { ExchangeAmount(it, columns[11]) },
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