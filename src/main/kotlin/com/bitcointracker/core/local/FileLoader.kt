package com.bitcointracker.core.local

interface FileLoader<T> {

    fun readCsvs(files: List<String>): List<T>
        = files.map { readCsv(it) }
            .flatMap { it }
            .toList()

    fun readCsv(file: String): List<T>
}