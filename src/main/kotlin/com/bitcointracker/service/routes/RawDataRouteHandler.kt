package com.bitcointracker.service.routes

import com.bitcointracker.core.chart.BitcoinDataRepository
import com.bitcointracker.core.database.TransactionRepository
import com.bitcointracker.core.parser.UniversalFileLoader
import com.bitcointracker.model.api.PaginatedResponse
import com.bitcointracker.model.api.PaginationParams
import com.bitcointracker.model.api.transaction.NormalizedTransaction
import com.bitcointracker.model.api.transaction.NormalizedTransactionType
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
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.UUID
import jakarta.inject.Inject
import kotlin.collections.chunked

class RawDataRouteHandler @Inject constructor(
    private val fileLoader: UniversalFileLoader,
    private val transactionRepository: TransactionRepository,
    private val bitcoinDataRepository: BitcoinDataRepository,
    private val objectMapper: ObjectMapper
) {

    /**
     * Handles file upload requests for transaction data.
     * Processes multipart form data, reads uploaded files, and stores the transaction data.
     *
     * @param call The ApplicationCall containing the multipart request
     * @throws Exception If file processing or storage fails
     */
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

    /**
     * Handles requests to download transaction data as a JSON file.
     * Retrieves all transactions from the repository and streams them as a JSON response.
     *
     * @param call The ApplicationCall for the download request
     * @throws Exception If data retrieval or serialization fails
     */
    suspend fun handleFileDownload(call: ApplicationCall) {
        try {
            val date = Instant.now()
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
            call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
        }
    }

    /**
     * Handles requests to retrieve all transactions with pagination and sorting options.
     * Supports customizable page size, sort field, and sort order.
     *
     * @param call The ApplicationCall containing pagination parameters
     * @throws Exception If transaction retrieval fails
     */
    suspend fun handleGetTransactions(call: ApplicationCall) {
        try {
            println("handleGetTransactions(call = $call)")
            // Extract pagination parameters from query parameters
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 10
            val sortBy = call.parameters["sortBy"] ?: "timestamp"
            val sortOrder = call.parameters["sortOrder"] ?: "desc"
            val assets: List<String>? = call.parameters["assets"]?.split(",")
            val types: List<NormalizedTransactionType>? = call
                .parameters["types"]
                ?.split(",")
                ?.map {
                    NormalizedTransactionType.valueOf(it)
                }

            val paginationParams = PaginationParams(
                page,
                pageSize,
                sortBy,
                sortOrder,
                assets,
                types,
            )

            val response = getTransactions(paginationParams)
            call.respond(response)
        } catch (ex: Exception) {
            println(ex)
            call.respond(HttpStatusCode.InternalServerError, "load failed: ${ex.localizedMessage}")
        }
    }

    /**
     * Handles requests to retrieve sell transactions for a specific year.
     * Validates the year parameter and returns matching transactions.
     *
     * @param call The ApplicationCall containing the year parameter
     * @throws Exception If parameter validation or transaction retrieval fails
     */
    suspend fun handleGetSellTransactionsByYear(call: ApplicationCall) {
        try {
            val year = call.parameters["year"]?.toIntOrNull()
            if (year == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid year parameter")
                return
            }

            // Get current year to validate input
            val currentYear = java.time.Year.now().value
            if (year < 2009 || year > currentYear) { // 2009 is when Bitcoin started
                call.respond(HttpStatusCode.BadRequest, "Year must be between 2009 and $currentYear")
                return
            }

            val transactions = transactionRepository.getSellTransactionsByYear(year)
            call.respond(transactions)
        } catch (ex: Exception) {
            println(ex)
            call.respond(
                HttpStatusCode.InternalServerError,
                "Failed to retrieve sell transactions: ${ex.localizedMessage}"
            )
        }
    }

    /**
     * Processes and loads input data from multiple files in parallel.
     * Parses the files and stores transactions in batches for better performance.
     *
     * @param input List of file contents as strings
     * @throws Exception If file parsing or database insertion fails
     */
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

        // Flatten the results, filter, and insert into database
        val allTransactions = parsedTransactions.flatten().map { transaction ->
            when {
                // Case 1: Both conditions need fixing - handle both missing asset value AND transaction amount
                transaction.assetValueFiat.amount < 0 &&
                        transaction.assetAmount.amount > 0 &&
                        transaction.transactionAmountFiat.amount < 0 -> {
                    val bitcoinPrice = bitcoinDataRepository.findByDate(transaction.timestamp)?.close
                    val updatedAssetValue = bitcoinPrice ?: transaction.assetValueFiat
                    val calculatedTransactionAmount = updatedAssetValue * transaction.assetAmount.amount
                    transaction.copy(
                        assetValueFiat = updatedAssetValue,
                        transactionAmountFiat = calculatedTransactionAmount
                    )
                }

                // Case 2: Missing asset value only - look up Bitcoin price
                transaction.assetValueFiat.amount < 0 -> {
                    val bitcoinPrice = bitcoinDataRepository.findByDate(transaction.timestamp)?.close
                    transaction.copy(assetValueFiat = bitcoinPrice ?: transaction.assetValueFiat)
                }

                // Case 3: Missing transaction amount only - calculate from asset value and amount
                transaction.assetAmount.amount > 0 && transaction.transactionAmountFiat.amount < 0 -> {
                    val calculatedTransactionAmount = transaction.assetValueFiat * transaction.assetAmount.amount
                    transaction.copy(transactionAmountFiat = calculatedTransactionAmount)
                }

                else -> transaction
            }
        }

        println("Adding files to the database in chunks")
        val batchSize = 1000
        allTransactions.chunked(batchSize).forEach { batch ->
            transactionRepository.addTransactions(batch)
        }

        println("Successfully processed ${allTransactions.size} transactions")
    }

    /**
     * Retrieves and sorts transactions based on pagination parameters.
     * Supports sorting by various transaction fields and custom page sizes.
     *
     * @param params PaginationParams containing page number, size, and sort options
     * @return PaginatedResponse containing the requested subset of transactions
     * @throws Exception If transaction retrieval or sorting fails
     */
    suspend fun getTransactions(params: PaginationParams): PaginatedResponse<NormalizedTransaction> {
        println("getTransactions(params = $params)")
        val allTransactions = transactionRepository.getFilteredTransactions(
            types = params.types,
            assets = params.assets,
        )

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
            "timestamp" -> if (params.sortOrder == "desc") {
                allTransactions.sortedByDescending { it.timestamp }
            } else {
                allTransactions.sortedBy { it.timestamp }
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
