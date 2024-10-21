package com.bitcointracker

import com.bitcointracker.core.TransactionCache
import com.bitcointracker.dagger.component.AppComponent
import com.bitcointracker.dagger.component.DaggerAppComponent
import com.bitcointracker.model.frontend.QuickLookData
import com.bitcointracker.model.jackson.ExchangeAmountDeserializer
import com.bitcointracker.model.jackson.ExchangeAmountSerializer
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.jackson.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.ByteArrayInputStream
import java.util.Date
import java.util.UUID

fun main() {
    val appComponent = DaggerAppComponent.create()

    embeddedServer(Netty, port = 9090) {
        module(appComponent)
    }.start(wait = true)
}

fun Application.module(appComponent: AppComponent) {
    val service = appComponent.getBackendService()
    val gson = appComponent.getGson()
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            registerModule(
                KotlinModule.Builder()
                    .withReflectionCacheSize(512)
                    .configure(KotlinFeature.NullToEmptyCollection, false)
                    .configure(KotlinFeature.NullToEmptyMap, false)
                    .configure(KotlinFeature.NullIsSameAsDefault, false)
                    .configure(KotlinFeature.SingletonSupport, false)
                    .configure(KotlinFeature.StrictNullChecks, false)
                    .build()
            )
            val module = SimpleModule().apply {
                addSerializer(ExchangeAmount::class.java, ExchangeAmountSerializer())
                addDeserializer(ExchangeAmount::class.java, ExchangeAmountDeserializer())
            }
            registerModule(module)
        }
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        anyHost()
    }

    routing {
        post("/api/upload") {
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
        get("/api/download") {
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
        get("/api/data") {
            try {
                // Load items for the specific page
                val items = service.getTransactions()

                println("Cache size: ${TransactionCache.getAllTransactions().size}")

                call.respond(items)
            } catch (ex: Exception) {
                println(ex)
                call.respond(HttpStatusCode.InternalServerError, "load failed: ${ex.localizedMessage}")
            }
        }
        get("/api/accumulation/token/{token}/days/{days}") {
            try {
                val tokenInput = call.parameters["token"] ?: return@get call.respondText(
                    "Missing or malformed token",
                    status = HttpStatusCode.BadRequest
                )

                val dayInput = call.parameters["days"]?.toIntOrNull() ?: return@get call.respondText(
                    "Days parameter must be a number",
                    status = HttpStatusCode.BadRequest
                )

                println("Fetching accumulation data for $tokenInput - $dayInput days")

                val data = service.getAccumulation(dayInput, tokenInput)
                call.respond(
                    QuickLookData(
                        title = "$tokenInput Accumulation",
                        value = "${data.last()}  $tokenInput",
                        data = service.getAccumulation(dayInput, tokenInput)
                    )
                )
            } catch (ex: Exception) {
                println(ex)
            }
        }
        get("/api/profit_statement") {
            try {
                call.respond(service.getProfitStatement())
            } catch (ex: Exception) {
                println(ex)
            }
        }
    }
}