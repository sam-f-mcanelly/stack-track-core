package com.bitcointracker.core.cache

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransactionType
import java.lang.IllegalArgumentException
import javax.inject.Inject
import javax.inject.Singleton

// TODO simplify the logic for each one
@Singleton
class TransactionMetadataCache @Inject constructor(){
    var transactionCount: Int = 0
    var assetToAmountHeld: MutableMap<String, ExchangeAmount> = mutableMapOf()

    fun update(transactions: List<NormalizedTransaction>) {
        assetToAmountHeld = mutableMapOf()
        transactions.forEach {
            try {
                val asset = it.assetAmount.unit.uppercase()
                if (it.type == NormalizedTransactionType.BUY) {
                    assetToAmountHeld[asset] = assetToAmountHeld.getOrDefault(
                        asset,
                        ExchangeAmount(0.0, asset)
                    ) + it.assetAmount
                } else if (it.type == NormalizedTransactionType.SELL) {
                    assetToAmountHeld[asset] = assetToAmountHeld.getOrDefault(
                        asset,
                        ExchangeAmount(0.0, asset)
                    ) - it.assetAmount
                }
            } catch(e: IllegalArgumentException) {
                println("Error updating the transaction cache when adding the transaction: \n $it")
            }
        }
        transactionCount = transactions.size
        println("Updated transaction count: $transactionCount")
        println("Updated assetToAmountHeld map: $assetToAmountHeld")
    }

    fun getAssetAmount(asset: String): ExchangeAmount =
        assetToAmountHeld.getOrDefault(asset.uppercase(), ExchangeAmount(0.0, asset))

    fun getAllAssetAmounts(): List<ExchangeAmount> =
        assetToAmountHeld.values.toList().also { println("getting all asset amounts: $it") }
}