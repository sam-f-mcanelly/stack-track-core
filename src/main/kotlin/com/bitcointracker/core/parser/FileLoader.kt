package com.bitcointracker.core.parser

interface FileLoader<T> {

    fun readCsvs(fileContents: List<String>): List<T>
        = fileContents.map { readCsv(it) }
            .flatMap { it }
            .toList()

    fun readCsv(file: String): List<T>
}