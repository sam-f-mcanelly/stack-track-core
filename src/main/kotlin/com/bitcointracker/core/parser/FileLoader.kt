package com.bitcointracker.core.parser

interface FileLoader<T> {

    fun readCsvs(fileLines: List<List<String>>): List<T>
        = fileLines.map { readCsv(it) }
            .flatMap { it }
            .toList()

    fun readCsv(lines: List<String>): List<T>
}