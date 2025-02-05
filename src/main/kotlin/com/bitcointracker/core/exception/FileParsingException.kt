package com.bitcointracker.core.exception

class FileParsingException(val fileName: String, message: String = "Failed to parse file $fileName", cause: Throwable? = null) : RuntimeException(message, cause)