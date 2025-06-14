package com.bitcointracker.core.parser.historical

import com.bitcointracker.model.internal.historical.BitcoinData
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import javax.inject.Inject

// TODO: Make this implement FileLoader
class BitcoinHistoricalDataCsvParser @Inject constructor() {

    companion object {
        private val logger = LoggerFactory.getLogger(BitcoinHistoricalDataCsvParser::class.java)
        private const val DATE_FORMAT = "MM/dd/yyyy"
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.ENGLISH)
            .withZone(ZoneOffset.UTC)
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
        // Parse CSV line with quotes
        val parts = parseCSVLine(line)
        require(parts.size >= 5) { "Invalid CSV line format: $line" }

        return BitcoinData(
            date = parseDate(parts[0], DATE_FORMATTER),
            open = parseExchangeAmount(parts[1]),
            high = parseExchangeAmount(parts[2]),
            low = parseExchangeAmount(parts[3]),
            close = parseExchangeAmount(parts[4])
        )
    }

    // Parse a CSV line, handling quotes correctly
    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var currentField = StringBuilder()
        var inQuotes = false

        for (c in line) {
            if (c == '\"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                result.add(currentField.toString().trim())
                currentField = StringBuilder()
            } else {
                currentField.append(c)
            }
        }

        // Add the last field
        result.add(currentField.toString().trim())

        return result
    }

    /**
     * Parses a date string into an Instant using the provided formatter.
     * Assumes the date string represents a date at the start of day in UTC.
     *
     * @param dateString The date string to parse
     * @param formatter The DateTimeFormatter to use
     * @return Instant representing the parsed date
     */
    fun parseDate(dateString: String, formatter: DateTimeFormatter): Instant {
        return try {
            // Try to parse as LocalDateTime first (if time is included)
            LocalDateTime.parse(dateString, formatter)
                .atZone(ZoneOffset.UTC)
                .toInstant()
        } catch (e: DateTimeParseException) {
            try {
                // Fall back to LocalDate (date only) and assume start of day UTC
                LocalDate.parse(dateString, formatter)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
            } catch (e2: DateTimeParseException) {
                // If both fail, try parsing with zone information
                ZonedDateTime.parse(dateString, formatter)
                    .toInstant()
            }
        }
    }

    private fun parseExchangeAmount(value: String): ExchangeAmount {
        return try {
            // Remove all commas from the value
            val cleanValue = value.replace(",", "")
            ExchangeAmount(
                amount = cleanValue.toDoubleOrNull() ?: 0.0,
                unit = DEFAULT_CURRENCY_UNIT
            )
        } catch (e: NumberFormatException) {
            logger.warn("Failed to parse exchange amount: $value", e)
            ExchangeAmount(0.0, DEFAULT_CURRENCY_UNIT)
        }
    }
}