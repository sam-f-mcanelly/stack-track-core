package com.bitcointracker.core.database

import com.bitcointracker.model.api.transaction.NormalizedTransactionType
import com.bitcointracker.model.api.transaction.TransactionSource
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object TransactionTable : Table("normalized_transactions") {
    val id = varchar("id", 255).uniqueIndex()
    val transactionSource = enumerationByName("source", 50, TransactionSource::class)
    val type = enumerationByName("type", 50, NormalizedTransactionType::class)

    // Fiat Transaction Amount
    val transactionAmountFiatValue = double("transaction_amount_fiat_value")
    val transactionAmountFiatUnit = varchar("transaction_amount_fiat_unit", 50)

    // Fee
    val feeFiatValue = double("fee_fiat_value")
    val feeFiatUnit = varchar("fee_fiat_unit", 50)

    // Asset Amount
    val assetAmountValue = double("asset_amount_value")
    val assetAmountUnit = varchar("asset_amount_unit", 50)

    // Asset Value in Fiat
    val assetValueFiatValue = double("asset_value_fiat_value")
    val assetValueFiatUnit = varchar("asset_value_fiat_unit", 50)

    val timestamp = timestamp("timestamp")
    val timestampText = varchar("timestamp_text", 255)
    val address = varchar("address", 255).default("")
    val notes = text("notes").default("")
    val filedWithIRS = bool("filed_with_irs").default(false)

    override val primaryKey = PrimaryKey(id)
}