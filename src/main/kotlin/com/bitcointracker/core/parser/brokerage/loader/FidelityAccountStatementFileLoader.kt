package com.bitcointracker.core.parser.brokerage.loader

import com.bitcointracker.core.parser.FileLoader
import com.bitcointracker.model.internal.transaction.fidelity.FidelityTransaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import jakarta.inject.Inject

/**
 * FileLoader for Fidelity account statements.
 * Parses the CSV format from Fidelity brokerage statements.
 */
class FidelityAccountStatementFileLoader @Inject constructor(): FileLoader<FidelityTransaction> {

    companion object {
        private val logger = LoggerFactory.getLogger(FidelityAccountStatementFileLoader::class.java)

        private const val DATE_FORMAT = "MM/dd/yyyy"
        private const val MIN_COLUMNS = 14

        // Column indices
        private const val RUN_DATE_INDEX = 0
        private const val ACCOUNT_INDEX = 1
        private const val ACCOUNT_NUMBER_INDEX = 2
        private const val ACTION_INDEX = 3
        private const val SYMBOL_INDEX = 4
        private const val DESCRIPTION_INDEX = 5
        private const val TYPE_INDEX = 6
        private const val QUANTITY_INDEX = 7
        private const val PRICE_INDEX = 8
        private const val COMMISSION_INDEX = 9
        private const val FEES_INDEX = 10
        private const val ACCRUED_INTEREST_INDEX = 11
        private const val AMOUNT_INDEX = 12
        private const val SETTLEMENT_DATE_INDEX = 13
    }

    /**
     * Reads CSV data and parses into FidelityTransaction objects.
     *
     * @param lines CSV file lines
     * @return List of parsed FidelityTransaction objects
     */
    override fun readCsv(lines: List<String>): List<FidelityTransaction> {
        val dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.ENGLISH)

        return lines
            .drop(1) // Skip header row
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    parseTransaction(line, dateFormatter)
                } catch (e: Exception) {
                    logger.warn("Failed to parse line: $line", e)
                    null
                }
            }
    }

    /**
     * Parses a single CSV line into a FidelityTransaction.
     *
     * @param line The CSV line to parse
     * @param dateFormatter DateTimeFormatter for parsing dates
     * @return Parsed FidelityTransaction
     */
    private fun parseTransaction(line: String, dateFormatter: DateTimeFormatter): FidelityTransaction {
        val columns = parseCsvLine(line)

        if (columns.size < MIN_COLUMNS) {
            throw IllegalArgumentException("Line has ${columns.size} columns, expected at least $MIN_COLUMNS")
        }

        return FidelityTransaction(
            runDate = parseDateWithoutTime(columns[RUN_DATE_INDEX], dateFormatter),
            account = cleanString(columns[ACCOUNT_INDEX]),
            accountNumber = cleanString(columns[ACCOUNT_NUMBER_INDEX]),
            action = cleanString(columns[ACTION_INDEX]),
            symbol = getNonEmptyStringOrNull(columns, SYMBOL_INDEX),
            description = cleanString(columns[DESCRIPTION_INDEX]),
            type = cleanString(columns[TYPE_INDEX]),
            quantity = parseDoubleOrNull(columns[QUANTITY_INDEX]),
            price = parseDoubleOrNull(columns[PRICE_INDEX]),
            commission = parseDoubleOrNull(columns[COMMISSION_INDEX]),
            fees = parseDoubleOrNull(columns[FEES_INDEX]),
            accruedInterest = parseDoubleOrNull(columns[ACCRUED_INTEREST_INDEX]),
            amount = parseDouble(columns[AMOUNT_INDEX]),
            settlementDate = parseDateWithoutTime(columns[SETTLEMENT_DATE_INDEX], dateFormatter)
        )
    }

    /**
     * Parses a CSV line handling quoted fields.
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && (i == 0 || line[i-1] == ',') -> {
                    inQuotes = true
                }
                char == '"' && inQuotes && (i == line.length - 1 || line[i+1] == ',') -> {
                    inQuotes = false
                }
                char == ',' && !inQuotes -> {
                    result.add(currentField.toString())
                    currentField.clear()
                }
                else -> {
                    currentField.append(char)
                }
            }
            i++
        }
        result.add(currentField.toString())
        return result
    }

    /**
     * Parses a date string to Instant.
     */
    private fun parseDateWithoutTime(dateString: String, formatter: DateTimeFormatter): Instant {
        return try {
            LocalDate.parse(cleanString(dateString), formatter)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
        } catch (e: DateTimeParseException) {
            logger.error("Failed to parse date: $dateString", e)
            throw e
        }
    }

    /**
     * Parses a string to Double, returning null if empty or invalid.
     */
    private fun parseDoubleOrNull(value: String): Double? {
        val cleaned = cleanString(value)
        return if (cleaned.isEmpty()) null else cleaned.toDoubleOrNull()
    }

    /**
     * Parses a string to Double, throwing exception if invalid.
     */
    private fun parseDouble(value: String): Double {
        return cleanString(value).toDouble()
    }

    /**
     * Cleans a string by removing quotes and trimming whitespace.
     */
    private fun cleanString(value: String): String {
        return value.trim().removeSurrounding("\"")
    }

    /**
     * Gets a non-empty string from the columns array at the specified index, or null.
     */
    private fun getNonEmptyStringOrNull(columns: List<String>, index: Int): String? {
        return if (index < columns.size) {
            val cleaned = cleanString(columns[index])
            if (cleaned.isNotBlank()) cleaned else null
        } else null
    }
}