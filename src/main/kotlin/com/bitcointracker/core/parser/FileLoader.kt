package com.bitcointracker.core.parser

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

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

    /**
     * Parses a date string into an Instant using the provided formatter.
     * Assumes the date string represents a date at the start of day in UTC.
     *
     * @param dateString The date string to parse
     * @param formatter The DateTimeFormatter to use
     * @return Instant representing the parsed date
     */
    fun parseDate(dateString: String, formatter: DateTimeFormatter): Instant {
        return try {
            // Try to parse as LocalDateTime first (if time is included)
            LocalDateTime.parse(dateString, formatter)
                .atZone(ZoneOffset.UTC)
                .toInstant()
        } catch (e: DateTimeParseException) {
            try {
                // Fall back to LocalDate (date only) and assume start of day UTC
                LocalDate.parse(dateString, formatter)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
            } catch (e2: DateTimeParseException) {
                // If both fail, try parsing with zone information
                ZonedDateTime.parse(dateString, formatter)
                    .toInstant()
            }
        }
    }
}