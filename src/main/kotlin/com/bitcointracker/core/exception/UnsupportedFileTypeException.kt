package com.bitcointracker.core.exception

class UnsupportedFileTypeException(private val fileContents: String, message: String = "Failed to parse file. contents:\n $fileContents", cause: Throwable? = null) : RuntimeException(message, cause)