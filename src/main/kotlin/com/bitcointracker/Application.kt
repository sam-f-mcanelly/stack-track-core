package com.bitcointracker

import com.bitcointracker.core.TransactionCache
import com.bitcointracker.dagger.component.AppComponent
import com.bitcointracker.dagger.component.DaggerAppComponent
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

fun main() {
    val appComponent = DaggerAppComponent.create()

    embeddedServer(Netty, port = 9090) {
        module(appComponent)
    }.start(wait = true)
}

fun Application.module(appComponent: AppComponent) {
    val service = appComponent.getBackendService()
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
        get("/api/data") {
            try {
                // Load items for the specific page
                val items = service.getTransactions()

                println("Cache size: ${TransactionCache.getAllTransactions().size}")

                call.respond(items)
            } catch (ex: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "load failed: ${ex.localizedMessage}")
            }
        }
        get("/api/portfolio_value") {
            try {
                val fiatGain = service.getFiatGain(30, "USD", "BTC", )
                call.respond(fiatGain)
            } catch (ex: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "analysis failed: ${ex.localizedMessage}")
            }
        }
        get("/api/profit_statement") {
            try {
                call.respond(service.getProfitStatement())
            } catch (ex: Exception) {

            }
        }
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
                call.respond(HttpStatusCode.InternalServerError,"Upload failed: ${ex.localizedMessage}")
            }
        }
    }
}