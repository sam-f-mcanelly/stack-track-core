package com.bitcointracker.core.cache

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransactionType
import javax.inject.Inject

// TODO simplify the logic for each one
class TransactionMetadataCache @Inject constructor(){
    var transactionCount: Int = 0
    var assetToAmountHeld: MutableMap<String, ExchangeAmount> = mutableMapOf()

    fun update(transactions: List<NormalizedTransaction>) {
        assetToAmountHeld = mutableMapOf()
        transactions.forEach {
            val asset = it.assetAmount.unit
            if (it.type == NormalizedTransactionType.BUY) {
                assetToAmountHeld[asset] = assetToAmountHeld.getOrDefault(asset, ExchangeAmount(0.0, it.transactionAmountFiat.unit)) + it.transactionAmountFiat
            } else if (it.type == NormalizedTransactionType.SELL) {
                assetToAmountHeld[asset] = assetToAmountHeld.getOrDefault(asset, ExchangeAmount(0.0, it.transactionAmountFiat.unit)) - it.transactionAmountFiat
            }
        }
        transactionCount = transactions.size
        println("Updated transaction count: $transactionCount")
    }
}