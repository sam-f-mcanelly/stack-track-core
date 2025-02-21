package com.bitcointracker.service.routes

import com.bitcointracker.core.TransactionRepository
import com.bitcointracker.core.parser.UniversalFileLoader
import com.bitcointracker.model.internal.transaction.normalized.NormalizedTransaction
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.collections.chunked

class RawDataRouteHandler @Inject constructor(
    private val fileLoader: UniversalFileLoader,
    private val transactionRepository: TransactionRepository,
    private val objectMapper: ObjectMapper
) {
    fun handleFileUpload(call: ApplicationCall) {
        call.application.launch {
            try {
                val multipart = call.receiveMultipart()
                val files = mutableListOf<ByteArray>()
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val fileBytes = part.streamProvider().readBytes()
                        files.add(fileBytes)
                        // You can also save the file to disk or process it further here
                    }
                    part.dispose()
                }
                loadInput(files.map { it.toString(Charsets.UTF_8) })
                call.respond(HttpStatusCode.OK, "Files uploaded and stored successfully.")
            } catch (ex: Exception) {
                println(ex)
                call.respond(HttpStatusCode.InternalServerError, "Upload failed: ${ex.localizedMessage}")
            }
        }
    }

    fun handleFileDownload(call: ApplicationCall) {
        call.application.launch {
            try {
                val date = Date()
                val transactions = getTransactions()
                val serializedTransactions = objectMapper.writeValueAsString(transactions)
                val jsonFileName =
                    "items-$date-${UUID.randomUUID().toString().substring(0, 6)}.json"  // Unique filename
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"$jsonFileName\"")
                // Convert JSON string to ByteArray and stream it as a response
                val byteArray = serializedTransactions.toByteArray(Charsets.UTF_8)
                val inputStream = ByteArrayInputStream(byteArray)
                call.respondOutputStream(
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                ) {
                    inputStream.copyTo(this)
                }
            } catch (ex: Exception) {
                println(ex)
            }
        }
    }

    fun handleGetAllTransactions(call: ApplicationCall) {
        call.application.launch {
            try {
                call.respond(getTransactions())
            } catch (ex: Exception) {
                println(ex)
                call.respond(HttpStatusCode.InternalServerError, "load failed: ${ex.localizedMessage}")
            }
        }
    }

    private suspend fun loadInput(input: List<String>) {
        // Parse files in parallel
        println("Loading files in parallel")
        val parsedTransactions = runBlocking {
            input.map { fileContent ->
                async(Dispatchers.IO) {
                    fileLoader.loadFromFileContents(fileContent)
                }
            }.awaitAll()
        }

        // Flatten the results and insert into database
        val allTransactions = parsedTransactions.flatten()

        // Optional: Process in batches if dealing with large datasets
        println("Adding files to the database in chunks")
        val batchSize = 1000
        allTransactions.chunked(batchSize).forEach { batch ->
            transactionRepository.addTransactions(batch)
        }

        println("Successfully processed ${allTransactions.size} transactions")
    }

    suspend fun getTransactions(): List<NormalizedTransaction> {
        return transactionRepository.getAllTransactions()
    }
}