/**
 * Main package for the Bitcoin Tracker application, a web server for tracking cryptocurrency transactions.
 *
 * This application uses several frameworks that Kotlin:
 *
 * - Ktor: A Kotlin-first web framework (similar to Spring Boot) that provides asynchronous server capabilities
 *   Think of it as Kotlin's equivalent to Spring Boot, but more lightweight and coroutine-focused
 *
 * - Dagger: A compile-time dependency injection framework (similar to Koin or Kodein)
 *   While Koin is more common in Kotlin, Dagger provides compile-time safety and better performance
 *
 * - Jackson: A JSON serialization library (similar to Kotlin Serialization)
 *   Used here with Kotlin-specific modules for better interop with Kotlin features
 *
 * - Netty: An asynchronous network application framework (similar concept to OkHttp but for servers)
 *   Provides the underlying HTTP server implementation for Ktor
 */
package com.bitcointracker

import com.bitcointracker.dagger.component.AppComponent
import com.bitcointracker.dagger.component.DaggerAppComponent
import com.bitcointracker.util.jackson.ExchangeAmountDeserializer
import com.bitcointracker.util.jackson.ExchangeAmountSerializer
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

/**
 * Entry point of the application that sets up and starts the Ktor server.
 *
 * This function:
 * 1. Creates a Dagger component for dependency injection (similar to creating a Koin module)
 * 2. Starts an embedded Netty server on port 9090
 * 3. Configures the server using the [module] function
 *
 * @see module for the detailed server configuration
 */
fun main() {
    val appComponent = DaggerAppComponent.create()

    embeddedServer(Netty, port = 3090) {
        module(appComponent)
    }.start(wait = true)
}

/**
 * Configures the Ktor application with all necessary middleware and routes.
 *
 * This function sets up:
 *
 * 1. Content Negotiation: Configures Jackson for JSON serialization with Kotlin-specific features
 *    - Similar to how you might configure Kotlin Serialization, but using Jackson instead
 *    - Adds custom serializers for [ExchangeAmount] type
 *    - Configures Kotlin-specific features like null handling and reflection cache
 *
 * 2. CORS (Cross-Origin Resource Sharing): Configures allowed HTTP methods and hosts
 *    - Enables cross-origin requests, similar to @CrossOrigin in Spring
 *
 * 3. Routes: Defines the HTTP endpoints of the application
 *    - Similar to @GetMapping and @PostMapping in Spring, but using Ktor's DSL
 *
 * The routes include:
 * - GET /health: Health check endpoint
 * - POST /api/upload: Upload cryptocurrency transaction data
 * - GET /api/download: Download transaction data
 * - GET /api/data: Retrieve all transactions
 * - GET /api/portfolio_value/{fiat}: Get portfolio value in specified fiat currency
 * - GET /api/accumulation/asset/{asset}/days/{days}: Get asset accumulation history
 *
 * @param appComponent The Dagger component providing dependencies (similar to KoinComponent in Koin)
 *                     Used to inject route handlers and services
 */
fun Application.module(appComponent: AppComponent) {
    val rawDataRouteHandler = appComponent.getRawDataRouteHandler()
    val metadataRouteHandler = appComponent.getMetadataRouteHandler()
    val taxComputationRouteHandler = appComponent.getTaxComputationRouteHandler()
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            registerModule(
                KotlinModule.Builder()
                    .withReflectionCacheSize(512)
                    .configure(KotlinFeature.NullToEmptyCollection, true)
                    .configure(KotlinFeature.NullToEmptyMap, false)
                    .configure(KotlinFeature.NullIsSameAsDefault, true)
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
        get("/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }
        post("/api/data/upload") {
            rawDataRouteHandler.handleFileUpload(call)
        }
        get("/api/data/download") {
            rawDataRouteHandler.handleFileDownload(call)
        }
        get("/api/data/transactions") {
            rawDataRouteHandler.handleGetTransactions(call)
        }
        get("/api/data/sells/{year}") {
            rawDataRouteHandler.handleGetSellTransactionsByYear(call)
        }
        get("/api/metadata/portfolio_value/{fiat}") {
            metadataRouteHandler.getPortfolioValue(call)
        }
        get("/api/metadata/accumulation/{asset}/{days}") {
            metadataRouteHandler.getAccumulationHistory(call)
        }
        post("/api/tax/request_report/{taxReportRequest}") {
            taxComputationRouteHandler.submitTaxReportRequest(call)
        }
    }
}