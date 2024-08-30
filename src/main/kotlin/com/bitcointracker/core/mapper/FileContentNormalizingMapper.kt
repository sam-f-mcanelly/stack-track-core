package com.bitcointracker.core.mapper

import com.bitcointracker.model.file.FileType
import com.bitcointracker.model.file.NormalizedFile
import javax.inject.Inject

class FileContentNormalizingMapper @Inject constructor() {
    companion object {
        private const val SEARCH_LENGTH = 10
        private const val STRIKE_ANNUAL_TRANSACTIONS_HEADER = "Transaction ID,Initiated Time (UTC),Completed Time (UTC),Transaction Type,State,Amount 1,Currency 1,Amount 2,Currency 2,BTC Price,Balance 1,Currency 1,Balance - BTC,Destination,Description"
        private const val STRIKE_MONTHLY_TRANSACTIONS_COLUMNS = "Transaction ID,Initiated Date (UTC),Initiated Time (UTC),Completed Time (UTC),Completed Time (UTC),Transaction Type,State,Amount 1,Currency 1,Fee 1,Amount 2,Currency 2,Fee 2,BTC Price,Balance 1,Currency 1,Balance - BTC,Destination,Description"
        private const val COINBASE_FILLS_TRANSACTIONS_COLUMNS = "portfolio,trade id,product,side,created at,size,size unit,price,fee,total,price/fee/total unit"
        private const val COINBASE_ANNUAL_TRANSACTIONS_COLUMNS = "ID,Timestamp,Transaction Type,Asset,Quantity Transacted,Price Currency,Price at Transaction,Subtotal,Total (inclusive of fees and/or spread),Fees and/or Spread,Notes"
    }

    fun normalizeFile(contents: String): NormalizedFile {
        val lines = contents.split("\n")
        var fileType: FileType? = null
        for (i in 0 until SEARCH_LENGTH){
            val trimmedLine = lines[i].trim()
            println("ANALYZING THE FOLLOWING LINE: ")
            println(lines[i])
            println("STRIKE_ANNUAL header")
            println(STRIKE_ANNUAL_TRANSACTIONS_HEADER)
            if (trimmedLine == STRIKE_ANNUAL_TRANSACTIONS_HEADER) {
                println("STRIKE ANNUAL TRANSACTIONS HEADER FOUND")
                fileType = FileType.STRIKE_ANNUAL
            } else if (trimmedLine == STRIKE_MONTHLY_TRANSACTIONS_COLUMNS) {
                fileType = FileType.STRIKE_MONTHLY
            } else if (trimmedLine == COINBASE_FILLS_TRANSACTIONS_COLUMNS) {
                fileType = FileType.COINBASE_PRO_FILLS
            } else if (trimmedLine == COINBASE_ANNUAL_TRANSACTIONS_COLUMNS) {
                throw RuntimeException("Unable to determine file type!")
            }

            fileType?.let {
                println("Returning a normalized file")
                println("Normalized file lines")
                println(lines.drop(i + 1).joinToString("\n"))
                return NormalizedFile(
                    fileType,
                    lines.drop(i + 1)
                )
            }
        }

        throw RuntimeException("Unsupported file type!")
    }

}