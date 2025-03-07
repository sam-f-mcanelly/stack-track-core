package com.bitcointracker.core.parser.exchange

import com.bitcointracker.core.exception.UnsupportedFileTypeException
import com.bitcointracker.model.internal.file.FileType
import com.bitcointracker.model.internal.file.NormalizedFile
import org.slf4j.LoggerFactory
import javax.inject.Inject

/**
 * A mapper class responsible for normalizing different types of cryptocurrency transaction files.
 * This class can process various file formats from different cryptocurrency platforms (Strike, Coinbase)
 * and normalize them into a standard internal format.
 *
 * The mapper identifies the file type by searching through the first few lines of the file
 * for known header patterns. Once identified, it processes the file accordingly.
 *
 * @constructor Creates a new FileContentNormalizingMapper instance with dependency injection
 */
class FileContentLoader @Inject constructor() {
    companion object {
        private val logger = LoggerFactory.getLogger(FileContentLoader::class.java)
        private const val SEARCH_LENGTH = 5

        /**
         * Known header patterns for different file types.
         * These constants represent the expected header formats for various transaction file types
         * from different platforms.
         */
        private const val STRIKE_ANNUAL_TRANSACTIONS_HEADER = "Transaction ID,Initiated Time (UTC),Completed Time (UTC),Transaction Type,State,Amount 1,Currency 1,Amount 2,Currency 2,BTC Price,Balance 1,Currency 1,Balance - BTC,Destination,Description"
        private const val STRIKE_MONTHLY_TRANSACTIONS_COLUMNS = "Transaction ID,Initiated Date (UTC),Initiated Time (UTC),Completed Time (UTC),Completed Time (UTC),Transaction Type,State,Amount 1,Currency 1,Fee 1,Amount 2,Currency 2,Fee 2,BTC Price,Balance 1,Currency 1,Balance - BTC,Destination,Description"
        private const val STRIKE_MONTHLY_TRANSACTIONS_COLUMNS_V2 = "Transaction ID,Initiated Date (UTC),Initiated Time (UTC),Completed Date (UTC),Completed Time (UTC),Transaction Type,State,Amount 1,Currency 1,Fee 1,Amount 2,Currency 2,Fee 2,BTC Price,Balance 1,Currency 1,Balance - BTC,Destination,Description"
        private const val STRIKE_MONTHLY_TRANSACTIONS_COLUMNS_V3 = "Transaction ID,Initiated Date (UTC),Initiated Time (UTC),Completed Date (UTC),Completed Time (UTC),Transaction Type,State,Amount 1,Currency 1,Fee 1,Amount 2,Currency 2,Fee 2,BTC Price,Balance 1,Currency 1,Balance - BTC,Destination,Description,Note"
        private const val STRIKE_MONTHLY_V2_TRANSACTIONS_HEADER = "Reference,Date & Time (UTC),Transaction Type,Amount USD,Fee USD,Amount BTC,Fee BTC,BTC Price,Cost Basis (USD),Destination,Description,Note"
        private const val COINBASE_FILLS_TRANSACTIONS_COLUMNS = "portfolio,trade id,product,side,created at,size,size unit,price,fee,total,price/fee/total unit"
        private const val COINBASE_ANNUAL_TRANSACTIONS_COLUMNS = "ID,Timestamp,Transaction Type,Asset,Quantity Transacted,Price Currency,Price at Transaction,Subtotal,Total (inclusive of fees and/or spread),Fees and/or Spread,Notes"
    }


    /**
     * Normalizes the contents of a cryptocurrency transaction file into a standardized format.
     *
     * This function attempts to identify the type of file by matching its header against known patterns,
     * then processes the file contents accordingly. It searches through the first [SEARCH_LENGTH] lines
     * of the file to find a matching header pattern.
     *
     * @param contents The raw string contents of the file to be normalized
     * @return [com.bitcointracker.model.internal.file.NormalizedFile] containing the identified file type and processed contents
     * @throws com.bitcointracker.core.exception.UnsupportedFileTypeException if the file type cannot be determined from the header
     *
     * @see com.bitcointracker.model.internal.file.FileType for supported file types
     * @see com.bitcointracker.model.internal.file.NormalizedFile for the structure of the normalized output
     */
    fun normalizeFile(contents: String): NormalizedFile {
        val lines = contents.split("\n")

        return (0 until SEARCH_LENGTH)
            .asSequence()
            .mapNotNull { index ->
                val trimmedLine = lines[index].trim()
                logger.info("Searching line $index")
                logger.info("Trimmed line: $trimmedLine")

                identifyFileType(trimmedLine)?.let { fileType ->
                    logger.info("$fileType HEADER FOUND")
                    NormalizedFile(fileType, lines.drop(index + 1))
                }
            }
            .firstOrNull() ?: throw UnsupportedFileTypeException(contents)
    }

    /**
     * Identifies the file type based on header pattern.
     *
     * @param header The header line to match
     * @return FileType if a match is found, null otherwise
     */
    private fun identifyFileType(header: String): FileType? =
        when (header) {
            STRIKE_ANNUAL_TRANSACTIONS_HEADER -> FileType.STRIKE_ANNUAL
            STRIKE_MONTHLY_TRANSACTIONS_COLUMNS,
            STRIKE_MONTHLY_TRANSACTIONS_COLUMNS_V2,
            STRIKE_MONTHLY_TRANSACTIONS_COLUMNS_V3 -> FileType.STRIKE_MONTHLY
            STRIKE_MONTHLY_V2_TRANSACTIONS_HEADER -> FileType.STRIKE_MONTHLY_V2
            COINBASE_FILLS_TRANSACTIONS_COLUMNS -> FileType.COINBASE_PRO_FILLS
            COINBASE_ANNUAL_TRANSACTIONS_COLUMNS -> FileType.COINBASE_ANNUAL
            else -> null
        }
}