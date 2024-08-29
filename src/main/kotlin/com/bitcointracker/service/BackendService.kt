package com.bitcointracker.service

import com.bitcointracker.core.TransactionCache
import com.bitcointracker.core.parser.UniversalFileLoader
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import com.fasterxml.jackson.databind.ObjectMapper
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
        println("Input Received: \n${input.first()}")

        println("Parsing transactions...")
        val transactions = input.flatMap {
            fileLoader.loadFromFileContents("annual transactions", it.split("\n").drop(1).joinToString("\n"))
        }
        println("Parsed transactions: \n")
        println(transactions.joinToString(","))
        val mapper: ObjectMapper = jacksonObjectMapper()
        val exchangeAmount = ExchangeAmount(100.0, "USD")
        val jsonString = mapper.writeValueAsString(exchangeAmount)
        println("Jackson Test of exchange amount: \n")
        println(jsonString)
        println("\n\nJackson test of transaction: \n")
        println(mapper.writeValueAsString(transactions[0]))

        TransactionCache.addTransactions(transactions)
    }

    override fun getTransactions(): List<NormalizedTransaction> {
        println("Hello world you fuck")
        return TransactionCache.getAllTransactions()
    }
}