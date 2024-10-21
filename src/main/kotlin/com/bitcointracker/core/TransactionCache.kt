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
        this.transactions.addAll(newTransactions)
        transactionsByType = this.transactions.groupBy { it.type }
        transactionsById = this.transactions.associateBy { it.id }
        transactionsByAsset = this.transactions.groupBy { it.assetAmount.unit }
    }

    fun getAllTransactions(): List<NormalizedTransaction> {
        println("getAllTransactions")
        return transactions.toList()
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


    fun getTransactionById(id: String): NormalizedTransaction? = transactionsById[id]
}