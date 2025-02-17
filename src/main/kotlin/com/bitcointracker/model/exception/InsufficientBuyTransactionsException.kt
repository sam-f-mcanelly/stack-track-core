package com.bitcointracker.model.exception

/**
 * Exception thrown when there are not enough buy transactions to cover a sell amount.
 */
class InsufficientBuyTransactionsException(message: String) : Exception(message)