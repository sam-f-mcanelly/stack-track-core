package com.bitcointracker

import com.bitcointracker.dagger.component.AppComponent
import com.bitcointracker.dagger.component.DaggerAppComponent
import com.bitcointracker.model.api.QuickLookData
import com.bitcointracker.util.jackson.ExchangeAmountDeserializer
import com.bitcointracker.util.jackson.ExchangeAmountSerializer
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
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

// TODO: move routes into their own files
fun Application.module(appComponent: AppComponent) {
    val rawDataRouteHandler = appComponent.getRawDataRouteHandler()
    val metadataRouteHandler = appComponent.getMetadataRouteHandler()
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
        post("/api/upload") {
            rawDataRouteHandler.handleFileUpload(call)
        }
        get("/api/download") {
            rawDataRouteHandler.handleFileDownload(call)
        }
        get("/api/data") {
            rawDataRouteHandler.handleGetAllTransactions(call)
        }
        get("/api/portfolio_value/{fiat}") {
            metadataRouteHandler.getPortfolioValue(call)
        }
        get("/api/accumulation/asset/{asset}/days/{days}") {
            metadataRouteHandler.getAccumulationHistory(call)
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