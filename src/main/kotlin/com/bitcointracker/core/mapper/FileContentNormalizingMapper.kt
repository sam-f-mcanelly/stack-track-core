package com.bitcointracker.core.mapper

import com.bitcointracker.core.exception.UnsupportedFileTypeException
import com.bitcointracker.model.internal.file.FileType
import com.bitcointracker.model.internal.file.NormalizedFile
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
class FileContentNormalizingMapper @Inject constructor() {
    companion object {
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
     * @return [NormalizedFile] containing the identified file type and processed contents
     * @throws UnsupportedFileTypeException if the file type cannot be determined from the header
     *
     * @see FileType for supported file types
     * @see NormalizedFile for the structure of the normalized output
     */
    fun normalizeFile(contents: String): NormalizedFile {
        println("\n Searching for a matching header in the file \n")
        val lines = contents.split("\n")
        var fileType: FileType? = null
        for (i in 0 until SEARCH_LENGTH) {
            println("Searching line $i")
            val trimmedLine = lines[i].trim()
            println("Trimmed line: $trimmedLine")
            println("File type: $fileType")
            if (fileType != null) {
                return NormalizedFile(
                    fileType,
                    lines.drop(i)
                )
            }
            fileType = when (trimmedLine) {
                STRIKE_ANNUAL_TRANSACTIONS_HEADER -> {
                    println("STRIKE ANNUAL TRANSACTIONS HEADER FOUND")
                    FileType.STRIKE_ANNUAL
                }
                STRIKE_MONTHLY_TRANSACTIONS_COLUMNS, STRIKE_MONTHLY_TRANSACTIONS_COLUMNS_V2, STRIKE_MONTHLY_TRANSACTIONS_COLUMNS_V3 -> {
                    println("STRIKE MONTHLY TRANSACTIONS HEADER FOUND")
                    FileType.STRIKE_MONTHLY
                }
                COINBASE_FILLS_TRANSACTIONS_COLUMNS -> {
                    println("COINBASE PRO FILLS TRANSACTIONS HEADER FOUND")
                    FileType.COINBASE_PRO_FILLS
                }
                COINBASE_ANNUAL_TRANSACTIONS_COLUMNS -> {
                    println("COINBASE ANNUAL TRANSACTIONS HEADER FOUND")
                    FileType.COINBASE_ANNUAL
                }
                else -> {
                    println("No valid header in $trimmedLine")
                    null
                }
            }
        }

        throw UnsupportedFileTypeException(contents)
    }
}
