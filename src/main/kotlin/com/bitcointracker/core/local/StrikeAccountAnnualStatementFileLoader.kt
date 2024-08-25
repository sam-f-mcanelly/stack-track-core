package com.bitcointracker.core.local

import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.strike.StrikeTransaction
import com.bitcointracker.model.transaction.strike.StrikeTransactionState
import com.bitcointracker.model.transaction.strike.StrikeTransactionType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class StrikeAccountAnnualStatementFileLoader(): FileLoader<StrikeTransaction>  {

    override fun readCsv(fileLocation: String): List<StrikeTransaction> {
        val dateFormatter = SimpleDateFormat("MMM dd yyyy HH:mm:ss")

        return File(fileLocation).useLines { lines ->
            lines.drop(1)
                .map { line ->
                    val columns = line.split(",")
                    val date = dateFormatter.parse(columns[1]) // Parsing "Jan 01 2024 13:30:07"

                    StrikeTransaction(
                        transactionId = columns[0],
                        date = date,
                        type = StrikeTransactionType.valueOf(columns[3].uppercase(Locale.ROOT).replace(" ", "_")),
                        state = StrikeTransactionState.valueOf(columns[4].uppercase(Locale.ROOT).replace(" ", "_")),
                        fee = ExchangeAmount(0.0, "USD") ,
                        asset1 = columns[5].toDoubleOrNull()?.let { ExchangeAmount(it, columns[6]) },
                        asset2 = columns[7].toDoubleOrNull()?.let { ExchangeAmount(it, columns[8]) },
                        assetValue = columns[9].toDoubleOrNull()?.let { ExchangeAmount(it, "BTC") },
                        balance = columns[10].toDoubleOrNull()?.let { ExchangeAmount(it, columns[11].uppercase(Locale.ROOT)) },
                        balanceBtc = columns[12].toDoubleOrNull()?.let { ExchangeAmount(it, "BTC") },
                        destination = if (columns.size > 13) columns[13] else null,
                        description = if (columns.size > 14) columns[14] else ""
                    )
                }.toList()
        }
    }
}