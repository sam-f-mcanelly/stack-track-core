package com.bitcointracker.service.routes

import com.bitcointracker.service.BackendService
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import java.io.ByteArrayInputStream
import java.util.*
import javax.inject.Inject

class RawDataRouteHandler @Inject constructor(
    private val service: BackendService,
    private val gson: Gson
) {
    suspend fun handleFileUpload(call: ApplicationCall) {
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

            service.loadInput(files.map { it.toString(Charsets.UTF_8) })
            call.respond(HttpStatusCode.OK, "Files uploaded and stored successfully.")
        } catch (ex: Exception) {
            println(ex)
            call.respond(HttpStatusCode.InternalServerError,"Upload failed: ${ex.localizedMessage}")
        }
    }

    suspend fun handleFileDownload(call: ApplicationCall) {
        try {
            val date = Date()
            val transactions = service.getTransactions()
            val serializedTransactions = gson.toJson(transactions)
            val jsonFileName = "items-$date-${UUID.randomUUID().toString().substring(0, 6)}.json"  // Unique filename
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
            val items = service.getTransactions()

            call.respond(items)
        } catch (ex: Exception) {
            println(ex)
            call.respond(HttpStatusCode.InternalServerError, "load failed: ${ex.localizedMessage}")
        }
    }

}