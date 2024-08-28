package com.bitcointracker

import com.bitcointracker.dagger.component.AppComponent
import com.bitcointracker.dagger.component.DaggerAppComponent
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.serialization.kotlinx.json.json
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
    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        anyHost()
    }

    routing {
        get("/api/data") {
            println("Calling backend service")
            try {
                call.respondText { appComponent.getBackendService().returnHelloWorld() }
            } catch (ex: Exception) {
                call.respondText { "Internal failure ${ex.localizedMessage}" }
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

                call.respond(appComponent.getBackendService().loadInput(files.map { it.toString(Charsets.UTF_8) }))
            } catch (ex: Exception) {
                call.respondText { "Upload failed: ${ex.localizedMessage}" }
            }
        }
    }
}