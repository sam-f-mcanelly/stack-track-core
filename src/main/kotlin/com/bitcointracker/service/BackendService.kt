package com.bitcointracker.service

import com.bitcointracker.core.local.UniversalFileLoader
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import org.slf4j.LoggerFactory
import javax.inject.Inject

interface IBackendService {
    fun loadInput(input: List<String>)
    fun returnHelloWorld(): String
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
    }

    override fun returnHelloWorld(): String {
        println("Hello world you fuck")
        return "Hello, world!"
    }
}