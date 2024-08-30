package com.bitcointracker.core

import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.bitcointracker.model.transaction.normalized.NormalizedTransactionType

object TransactionCache {

    private var transactions: MutableSet<NormalizedTransaction> = mutableSetOf()
    private var transactionsByType: Map<NormalizedTransactionType, List<NormalizedTransaction>> = mutableMapOf()
    private var transactionsById: Map<String, NormalizedTransaction> = mutableMapOf()
    private var transactionsByAsset: Map<String, List<NormalizedTransaction>> = mutableMapOf()

    // TODO allow for adding
    fun addTransactions(newTransactions: List<NormalizedTransaction>) {
        transactionsByType = newTransactions.groupBy { it.type }
        transactionsById = newTransactions.associateBy { it.id }
        transactionsByAsset = newTransactions.groupBy { it.assetAmount.unit }
        this.transactions.addAll(newTransactions)
    }

    fun getAllTransactions(): List<NormalizedTransaction> {
        println("getAllTransactions")
        return transactions.toMutableList()
    }

    fun clearAllTransactions() = transactions.clear()

    fun getTransactionsByType(vararg types: NormalizedTransactionType): List<NormalizedTransaction>
        = types.flatMap {
            transactionsByType.getOrDefault(it, listOf())
        }

    fun getTransactionsByAsset(vararg assets: String): List<NormalizedTransaction>
         = assets.flatMap {
            transactionsByAsset.getOrDefault(it, listOf())
        }


    fun getTransactionById(id: String) = transactionsById[id]
}