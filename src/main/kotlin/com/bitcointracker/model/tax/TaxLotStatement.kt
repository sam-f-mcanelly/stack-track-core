package com.bitcointracker.model.tax

import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction

data class TaxLotStatement (
    val id: String,
    val buyTransaction: NormalizedTransaction,
    val sellTransaction: NormalizedTransaction,
    val taxType: TaxType,
    val holdingTimeInDays: Long,
    val isPartialSell: Boolean,
    val percentOfLotSold: Double,
    val spent: ExchangeAmount,
    val returned: ExchangeAmount,
    val profit: ExchangeAmount,
) {
    override fun toString(): String {
        return """
            id: $id,
            buyTransaction: $buyTransaction,
            sellTransaction: $sellTransaction,
            taxType: $taxType,
            holdingTimeInDays: $holdingTimeInDays,
            isPartialSell: $isPartialSell,
            percentOfLotSold: $percentOfLotSold,
            spent: $spent,
            returned: $returned,
            profit: $profit
        """.trimIndent()
    }
}