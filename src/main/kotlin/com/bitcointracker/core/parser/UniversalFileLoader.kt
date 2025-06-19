package com.bitcointracker.core.parser

import com.bitcointracker.core.exception.FileParsingException
import com.bitcointracker.core.exception.UnsupportedFileTypeException
import com.bitcointracker.core.parser.processor.FileProcessor
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Universal file loader for cryptocurrency transaction files.
 * Uses a map of file processors to handle different file types.
 */
class UniversalFileLoader @Inject constructor(
    private val fileContentLoader: FileContentLoader,
    private val fileProcessors: Map<String, @JvmSuppressWildcards FileProcessor>
) {
    companion object {
        private const val LOADING_FILE_MESSAGE = "Loading file: %s"

        private val logger = LoggerFactory.getLogger(UniversalFileLoader::class.java)
    }

    /**
     * Load and process multiple files.
     *
     * @param files List of files to process
     * @return List of normalized transactions from all files
     */
    fun loadFiles(files: List<File>): List<NormalizedTransaction> =
        files.flatMap { loadFile(it) }

    /**
     * Load and process a single file.
     *
     * @param file The file to process
     * @return List of normalized transactions from the file
     * @throws FileParsingException if the file cannot be parsed
     */
    private fun loadFile(file: File): List<NormalizedTransaction> {
        logger.info(LOADING_FILE_MESSAGE.format(file.absolutePath))
        return try {
            loadFromFileContents(loadLocalFile(file))
        } catch (e: Exception) {
            throw FileParsingException(file.name, cause = e)
        }
    }

    /**
     * Load and process file contents.
     *
     * @param contents The contents of the file
     * @return List of normalized transactions
     */
    fun loadFromFileContents(contents: String): List<NormalizedTransaction> {
        val normalizedFile = fileContentLoader.normalizeFile(contents)
        val fileType = normalizedFile.fileType

        return fileProcessors[fileType.name]?.processFile(normalizedFile.fileLines)
            ?: throw UnsupportedFileTypeException("Unsupported file type: $fileType")
    }

    /**
     * Read the contents of a local file.
     *
     * @param file The file to read
     * @return The contents of the file as a string
     */
    fun loadLocalFile(file: File): String =
        file.useLines { lines -> lines.joinToString("\n") }
}
