package com.bitcointracker.util.local

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of ResourceProvider for classpath resources
 */
@Singleton
class ClasspathResourceProvider @Inject constructor() {
    companion object {
        private val logger = LoggerFactory.getLogger(ClasspathResourceProvider::class.java)
    }

    fun findResourceFiles(directoryPath: String, extension: String): List<BufferedReader> {
        val classLoader = javaClass.classLoader
        val dirUrl = classLoader.getResource(directoryPath)
            ?: throw IllegalArgumentException("Directory not found in classpath: $directoryPath. " +
                    "Make sure the path is relative to src/main/resources.")

        return when (dirUrl.protocol) {
            "file" -> {
                // Handle file system resources (development mode)
                val directory = File(dirUrl.toURI())
                if (!directory.exists() || !directory.isDirectory) {
                    throw IllegalArgumentException("Not a valid directory: $directoryPath")
                }

                directory.listFiles { _, name ->
                    name.endsWith(extension, ignoreCase = true)
                }?.map { file ->
                    val resourcePath = "$directoryPath/${file.name}"
                    val inputStream = classLoader.getResourceAsStream(resourcePath)
                        ?: throw IllegalArgumentException("Resource not found: $resourcePath")
                    BufferedReader(inputStream.reader())
                } ?: emptyList()
            }
            "jar" -> {
                // Handle JAR resources (production mode)
                val jarConnection = dirUrl.openConnection() as java.net.JarURLConnection
                val jarFile = jarConnection.jarFile
                val entries = jarFile.entries()
                val result = mutableListOf<BufferedReader>()

                val dirWithSlash = if (directoryPath.endsWith("/")) directoryPath else "$directoryPath/"

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.startsWith(dirWithSlash)
                        && entry.name.endsWith(extension, ignoreCase = true)) {
                        val inputStream = classLoader.getResourceAsStream(entry.name)
                            ?: continue
                        result.add(BufferedReader(inputStream.reader()))
                    }
                }

                if (result.isEmpty()) {
                    logger.warn("No files with extension $extension found in $directoryPath")
                }

                result
            }
            else -> {
                logger.warn("Unsupported protocol: ${dirUrl.protocol} for resource $directoryPath")
                emptyList()
            }
        }
    }
}
