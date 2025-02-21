package com.bitcointracker.service.routes

import com.bitcointracker.core.TransactionRepository
import com.bitcointracker.core.parser.UniversalFileLoader
import com.bitcointracker.model.api.PaginatedResponse
import com.bitcointracker.model.api.PaginationParams
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
import io.ktor.server.routing.post
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
    suspend fun handleFileUpload(call: ApplicationCall) {
        try {
            println("Content-Length: ${call.request.headers["Content-Length"]}")
            println("Content-Type: ${call.request.headers["Content-Type"]}")

            val multipart = call.receiveMultipart()
            val files = mutableListOf<ByteArray>()

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        println("Receiving file: ${part.originalFileName}")
                        println("Content type: ${part.contentType}")

                        val fileBytes = try {
                            part.streamProvider().use { input ->
                                input.readBytes()
                            }
                        } catch (e: Exception) {
                            println("Error reading file: ${e.message}")
                            null
                        }

                        if (fileBytes != null && fileBytes.isNotEmpty()) {
                            files.add(fileBytes)
                            println("Successfully read ${fileBytes.size} bytes")
                        } else {
                            println("Received empty file")
                        }
                    }
                    is PartData.FormItem -> {
                        println("Received form item: ${part.name} = ${part.value}")
                    }
                    else -> {
                        println("Received unknown part type: ${part::class.simpleName}")
                    }
                }
                part.dispose()
            }

            if (files.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "No valid files received")
                return
            }

            loadInput(files.map { it.toString(Charsets.UTF_8) })
            call.respond(HttpStatusCode.OK, "Files uploaded and stored successfully.")
        } catch (ex: Exception) {
            println("Upload failed with exception: ${ex::class.simpleName}")
            println(ex.stackTraceToString())
            call.respond(HttpStatusCode.InternalServerError, "Upload failed: ${ex.localizedMessage}")
        }
    }

    suspend fun handleFileDownload(call: ApplicationCall) {
        try {
            val date = Date()
            val transactions = transactionRepository.getAllTransactions()
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

    suspend fun handleGetAllTransactions(call: ApplicationCall) {
        try {
            // Extract pagination parameters from query parameters
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 10
            val sortBy = call.parameters["sortBy"] ?: "timestamp"
            val sortOrder = call.parameters["sortOrder"] ?: "desc"

            val paginationParams = PaginationParams(page, pageSize, sortBy, sortOrder)

            val response = getTransactions(paginationParams)
            call.respond(response)
        } catch (ex: Exception) {
            println(ex)
            call.respond(HttpStatusCode.InternalServerError, "load failed: ${ex.localizedMessage}")
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

    suspend fun getTransactions(params: PaginationParams): PaginatedResponse<NormalizedTransaction> {
        val allTransactions = transactionRepository.getAllTransactions()

        // Sort transactions based on parameters
        val sortedTransactions = when (params.sortBy) {
            "transactionAmountFiat.amount" -> allTransactions.sortedBy {
                if (params.sortOrder == "desc") -it.transactionAmountFiat.amount else it.transactionAmountFiat.amount
            }
            "fee.amount" -> allTransactions.sortedBy {
                if (params.sortOrder == "desc") -it.fee.amount else it.fee.amount
            }
            "assetAmount.amount" -> allTransactions.sortedBy {
                if (params.sortOrder == "desc") -it.assetAmount.amount else it.assetAmount.amount
            }
            "assetValueFiat.amount" -> allTransactions.sortedBy {
                if (params.sortOrder == "desc") -it.assetValueFiat.amount else it.assetValueFiat.amount
            }
            "timestamp" -> allTransactions.sortedBy {
                if (params.sortOrder == "desc") -it.timestamp.time else it.timestamp.time
            }
            else -> allTransactions.sortedBy {
                @Suppress("UNCHECKED_CAST")
                val value = it::class.members.find { member -> member.name == params.sortBy }?.call(it) as? Comparable<Any>
                if (params.sortOrder == "desc") value else value
            }
        }

        // Calculate pagination
        val total = sortedTransactions.size
        val startIndex = (params.page - 1) * params.pageSize
        val endIndex = minOf(startIndex + params.pageSize, total)

        // Get the paginated subset
        val paginatedTransactions = if (startIndex < total) {
            sortedTransactions.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        return PaginatedResponse(
            data = paginatedTransactions,
            total = total,
            page = params.page,
            pageSize = params.pageSize
        )
    }
}