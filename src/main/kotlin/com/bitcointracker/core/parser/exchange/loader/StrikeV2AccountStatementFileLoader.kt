package com.bitcointracker.core.parser.exchange.loader

import com.bitcointracker.core.parser.FileLoader
import com.bitcointracker.model.internal.transaction.strike.StrikeV2Transaction
import com.bitcointracker.model.internal.transaction.strike.StrikeV2TransactionType
import java.text.SimpleDateFormat
import javax.inject.Inject

/**
 * FileLoader for Strike V2 monthly statements.
 * Parses the new CSV format introduced in 2025.
 */
class StrikeV2AccountStatementFileLoader @Inject constructor(): FileLoader<StrikeV2Transaction> {

    companion object {
        private const val DATE_FORMAT = "MMM dd yyyy HH:mm:ss"
        private const val MIN_COLUMNS = 11

        // Column indices
        private const val REFERENCE_INDEX = 0
        private const val DATE_INDEX = 1
        private const val TYPE_INDEX = 2
        private const val AMOUNT_USD_INDEX = 3
        private const val FEE_USD_INDEX = 4
        private const val AMOUNT_BTC_INDEX = 5
        private const val FEE_BTC_INDEX = 6
        private const val BTC_PRICE_INDEX = 7
        private const val COST_BASIS_INDEX = 8
        private const val DESTINATION_INDEX = 9
        private const val DESCRIPTION_INDEX = 10
        private const val NOTE_INDEX = 11
    }

    /**
     * Reads CSV data and parses into StrikeV2Transaction objects.
     *
     * @param lines CSV file lines
     * @return List of parsed StrikeV2Transaction objects
     */
    override fun readCsv(lines: List<String>): List<StrikeV2Transaction> {
        val dateFormatter = SimpleDateFormat(DATE_FORMAT)

        return lines
            .filter { it.split(",").size >= MIN_COLUMNS }
            .map { line -> parseTransaction(line, dateFormatter) }
    }

    /**
     * Parses a single CSV line into a StrikeV2Transaction.
     *
     * @param line The CSV line to parse
     * @param dateFormatter SimpleDateFormat for parsing dates
     * @return Parsed StrikeV2Transaction
     */
    private fun parseTransaction(line: String, dateFormatter: SimpleDateFormat): StrikeV2Transaction {
        val columns = line.split(",")

        return StrikeV2Transaction(
            reference = columns[REFERENCE_INDEX],
            date = dateFormatter.parse(columns[DATE_INDEX]),
            type = StrikeV2TransactionType.fromString(columns[TYPE_INDEX]),
            amountUsd = columns[AMOUNT_USD_INDEX].toDoubleOrNull(),
            feeUsd = columns[FEE_USD_INDEX].toDoubleOrNull(),
            amountBtc = columns[AMOUNT_BTC_INDEX].toDoubleOrNull(),
            feeBtc = columns[FEE_BTC_INDEX].toDoubleOrNull(),
            btcPrice = columns[BTC_PRICE_INDEX].toDoubleOrNull(),
            costBasisUsd = columns[COST_BASIS_INDEX].toDoubleOrNull(),
            destination = getNonEmptyStringOrNull(columns, DESTINATION_INDEX),
            description = getNonEmptyStringOrNull(columns, DESCRIPTION_INDEX),
            note = if (columns.size > NOTE_INDEX) getNonEmptyStringOrNull(columns, NOTE_INDEX) else null
        )
    }

    /**
     * Gets a non-empty string from the columns array at the specified index, or null
     *
     * @param columns The array of column values
     * @param index The index to check
     * @return The non-empty string or null
     */
    private fun getNonEmptyStringOrNull(columns: List<String>, index: Int): String? =
        if (index < columns.size && columns[index].isNotBlank()) columns[index] else null
}