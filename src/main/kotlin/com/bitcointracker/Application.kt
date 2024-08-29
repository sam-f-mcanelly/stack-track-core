package com.bitcointracker

import com.bitcointracker.core.TransactionCache
import com.bitcointracker.dagger.component.AppComponent
import com.bitcointracker.dagger.component.DaggerAppComponent
import com.fasterxml.jackson.databind.SerializationFeature
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
            registerModule(KotlinModule())
        }
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        anyHost()
    }

    routing {
        get("/api/items") {
            println("Calling backend service")
            try {
                call.respondText { service.getTransactions()[0].toString() }
            } catch (ex: Exception) {
                call.respondText { "Internal failure ${ex.localizedMessage}" }
            }
        }
        get("/api/data") {
            println("Paginated loading of items")
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 50

            try {
                // Load items for the specific page
                val items = service
                    .getTransactions()
                    .drop((page - 1) * pageSize)
                    .take(pageSize)

                println("Cache size: ${TransactionCache.getAllTransactions().size}")
                println("Returning ${items.size} items")

                call.respond(items)
            } catch (ex: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "load failed: ${ex.localizedMessage}")
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