package com.bitcointracker.core.mapper

import com.bitcointracker.model.internal.file.FileType
import com.bitcointracker.model.internal.file.NormalizedFile
import javax.inject.Inject

class FileContentNormalizingMapper @Inject constructor() {
    companion object {
        private const val SEARCH_LENGTH = 10
        private const val STRIKE_ANNUAL_TRANSACTIONS_HEADER = "Transaction ID,Initiated Time (UTC),Completed Time (UTC),Transaction Type,State,Amount 1,Currency 1,Amount 2,Currency 2,BTC Price,Balance 1,Currency 1,Balance - BTC,Destination,Description"
        private const val STRIKE_MONTHLY_TRANSACTIONS_COLUMNS = "Transaction ID,Initiated Date (UTC),Initiated Time (UTC),Completed Time (UTC),Completed Time (UTC),Transaction Type,State,Amount 1,Currency 1,Fee 1,Amount 2,Currency 2,Fee 2,BTC Price,Balance 1,Currency 1,Balance - BTC,Destination,Description"
        private const val STRIKE_MONTHLY_TRANSACTIONS_COLUMNS_V2 = "Transaction ID,Initiated Date (UTC),Initiated Time (UTC),Completed Date (UTC),Completed Time (UTC),Transaction Type,State,Amount 1,Currency 1,Fee 1,Amount 2,Currency 2,Fee 2,BTC Price,Balance 1,Currency 1,Balance - BTC,Destination,Description"
        private const val COINBASE_FILLS_TRANSACTIONS_COLUMNS = "portfolio,trade id,product,side,created at,size,size unit,price,fee,total,price/fee/total unit"
        private const val COINBASE_ANNUAL_TRANSACTIONS_COLUMNS = "ID,Timestamp,Transaction Type,Asset,Quantity Transacted,Price Currency,Price at Transaction,Subtotal,Total (inclusive of fees and/or spread),Fees and/or Spread,Notes"
    }

    fun normalizeFile(contents: String): NormalizedFile {
        val lines = contents.split("\n")
        var fileType: FileType? = null
        for (i in 0 until SEARCH_LENGTH){
            val trimmedLine = lines[i].trim()
            if (trimmedLine == STRIKE_ANNUAL_TRANSACTIONS_HEADER) {
                println("STRIKE ANNUAL TRANSACTIONS HEADER FOUND")
                fileType = FileType.STRIKE_ANNUAL
            } else if (trimmedLine == STRIKE_MONTHLY_TRANSACTIONS_COLUMNS || trimmedLine == STRIKE_MONTHLY_TRANSACTIONS_COLUMNS_V2) {
                println("STRIKE MONTHLY TRANSACTIONS HEADER FOUND")
                fileType = FileType.STRIKE_MONTHLY
            } else if (trimmedLine == COINBASE_FILLS_TRANSACTIONS_COLUMNS) {
                println("COINBASE PRO FILLS TRANSACTIONS HEADER FOUND")
                fileType = FileType.COINBASE_PRO_FILLS
            } else if (trimmedLine == COINBASE_ANNUAL_TRANSACTIONS_COLUMNS) {
                println("COINBASE ANNUAL TRANSACTIONS HEADER FOUND")
                fileType = FileType.COINBASE_ANNUAL
            }

            fileType?.let {
                return NormalizedFile(
                    fileType,
                    lines.drop(i + 1)
                )
            }
        }

        throw RuntimeException("Unsupported file type!")
    }

}
