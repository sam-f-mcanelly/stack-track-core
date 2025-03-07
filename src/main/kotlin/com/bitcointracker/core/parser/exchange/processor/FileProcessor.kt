package com.bitcointracker.core.parser.exchange.processor

import com.bitcointracker.model.api.transaction.NormalizedTransaction
import javax.inject.Qualifier

/**
 * Interface for processing specific types of cryptocurrency transaction files.
 * Each implementation handles loading and normalizing a specific file format.
 */
interface FileProcessor {
    /**
     * Process file contents and return normalized transactions.
     *
     * @param fileLines The lines of the file to process
     * @return List of normalized transactions
     */
    fun processFile(fileLines: List<String>): List<NormalizedTransaction>
}