package com.bitcointracker.service

import com.bitcointracker.core.TransactionCache
import com.bitcointracker.core.parser.UniversalFileLoader
import com.bitcointracker.model.jackson.ExchangeAmountDeserializer
import com.bitcointracker.model.jackson.ExchangeAmountSerializer
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import javax.inject.Inject

interface IBackendService {
    fun loadInput(input: List<String>)
    fun getTransactions(): List<NormalizedTransaction>
}

class BackendService @Inject constructor(
    private val fileLoader: UniversalFileLoader,
) : IBackendService {

    companion object {
        // TODO make this stupid thing work
        private val logger = LoggerFactory.getLogger(BackendService::class.java)
    }

    override fun loadInput(input: List<String>) {
        println("Parsing transactions...")
        val transactions = input.flatMap {
            fileLoader.loadFromFileContents("annual transactions", it.split("\n").drop(1).joinToString("\n"))
        }

        TransactionCache.addTransactions(transactions)
    }

    override fun getTransactions(): List<NormalizedTransaction> {
        println("Hello world you fuck")
        return TransactionCache.getAllTransactions()
    }
}