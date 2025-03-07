package com.bitcointracker.core.parser.historical

import com.bitcointracker.model.internal.historical.BitcoinData
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import java.io.BufferedReader
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

class BitcoinHistoricalDataCsvParser @Inject constructor() {

    companion object {
        private val DATE_FORMATTER = SimpleDateFormat("MM/dd/yyyy")
        const val DEFAULT_CURRENCY_UNIT = "USD"
    }

    /**
     * Parses a CSV file into Bitcoin data objects
     *
     * @param reader Reader containing CSV content
     * @return List of BitcoinData objects
     */
    fun parse(reader: BufferedReader): List<BitcoinData> {
        return reader.use { r ->
            r.lineSequence()
                .drop(1) // Skip header
                .filter { it.isNotBlank() }
                .map { parseLine(it) }
                .toList()
        }
    }

    private fun parseLine(line: String): BitcoinData {
        val parts = line.split(',').map { it.trim() }
        require(parts.size >= 5) { "Invalid CSV line format: $line" }

        return BitcoinData(
            date = parseDate(parts[0]),
            open = parseExchangeAmount(parts[1]),
            high = parseExchangeAmount(parts[2]),
            low = parseExchangeAmount(parts[3]),
            close = parseExchangeAmount(parts[4])
        )
    }

    private fun parseDate(dateStr: String): Date {
        return try {
            DATE_FORMATTER.parse(dateStr)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid date format: $dateStr", e)
        }
    }

    private fun parseExchangeAmount(value: String): ExchangeAmount {
        return try {
            ExchangeAmount(
                amount = value.toDoubleOrNull() ?: 0.0,
                unit = DEFAULT_CURRENCY_UNIT
            )
        } catch (e: NumberFormatException) {
            ExchangeAmount(0.0, DEFAULT_CURRENCY_UNIT)
        }
    }
}