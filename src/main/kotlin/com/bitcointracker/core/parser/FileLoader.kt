package com.bitcointracker.core.parser

/**
 * Interface for loading and parsing files into domain objects.
 *
 * This interface is intended to handle CSV files, with methods
 * to process entire lists of CSV files (each represented as a list of lines)
 * or individual files (as a single list of lines).
 *
 * @param T the type of object produced by parsing the file
 */
interface FileLoader<T> {

    /**
     * Reads and parses multiple CSV files.
     *
     * Each file is represented as a list of string lines.
     *
     * @param fileLines A list where each element represents one file's lines.
     * @return A combined list of all parsed objects from all files.
     */
    fun readCsvs(fileLines: List<List<String>>): List<T>
            = fileLines.map { readCsv(it) }
        .flatMap { it }
        .toList()

    /**
     * Reads and parses a single CSV file.
     *
     * @param lines The lines of the file.
     * @return A list of parsed objects from the file.
     */
    fun readCsv(lines: List<String>): List<T>
}