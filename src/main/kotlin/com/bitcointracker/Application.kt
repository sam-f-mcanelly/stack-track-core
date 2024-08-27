package com.bitcointracker

import com.bitcointracker.dagger.component.DaggerAppComponent
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val appComponent = DaggerAppComponent.create()
    val myService = appComponent.getBackendService()

    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            json()
        }
        routing {
            get("/api/service") {
                call.respondText(myService.process(), ContentType.Text.Plain)
            }
            get("/api/objects") {
                val folder = call.request.queryParameters["folder"]
                call.respond(myService.generateObjects(folder!!))
            }
        }
    }.start(wait = true)
}