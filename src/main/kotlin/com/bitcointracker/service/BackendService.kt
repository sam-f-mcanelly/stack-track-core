package com.bitcointracker.service

import com.bitcointracker.core.NormalizedTransactionAnalyzer
import com.bitcointracker.core.local.UniversalFileLoader
import com.bitcointracker.external.client.CoinbaseClient
import com.bitcointracker.model.transaction.normalized.NormalizedTransaction
import java.io.File
import javax.inject.Inject

class BackendService @Inject constructor(
    private val fileLoader: UniversalFileLoader,
) {
    fun process(): String {
        return "Hello from MyService"
    }

    fun generateObjects(folder: String): List<NormalizedTransaction> {
        return fileLoader.loadFiles(listFilesInDirectory(folder))
    }

    fun listFilesInDirectory(directoryPath: String): List<File> {
        val directory = File(directoryPath)

        // Check if the path is a directory
        if (!directory.isDirectory) {
            throw IllegalArgumentException("The provided path is not a directory.")
        }

        // Recursive function to list files
        return listFilesRecursively(directory)
    }

    fun listFilesRecursively(directory: File): List<File> {
        val files = mutableListOf<File>()

        // Get all files and directories in the current directory
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                // Recursively list files in the subdirectory
                files.addAll(listFilesRecursively(file))
            } else {
                files.add(file)
            }
        }

        return files
    }
}