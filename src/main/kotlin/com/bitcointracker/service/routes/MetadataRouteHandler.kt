package com.bitcointracker.service.routes

import com.bitcointracker.model.api.QuickLookData
import com.bitcointracker.service.BackendService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import javax.inject.Inject

class MetadataRouteHandler @Inject constructor(
    private val service: BackendService
) {

    suspend fun getAssetHoldings(call: ApplicationCall) {
        try {
            val assetInput = call.parameters["asset"] ?: return call.respondText(
                "Missing or malformed asset",
                status = HttpStatusCode.BadRequest
            )

            val assetHoldings = service.getAssetHoldings(assetInput, "USD") // TODO: support other currencies

            call.respond(assetHoldings)
        } catch (e: Exception) {
            println("Failed to load asset holdings!")
            println(e.localizedMessage)
            println(e.stackTrace)
        }
    }

    suspend fun getPortfolioValue(call: ApplicationCall) {
        try {
            val fiatInput = call.parameters["fiat"] ?: return call.respondText(
                "Missing or malformed fiat",
                status = HttpStatusCode.BadRequest
            )

            println("Getting portfolio value for USD")
            val value = service.getPortfolioValue("USD") // TODO: support other currencies
            println("portfolio value: $value")

            call.respond(value)
        } catch (e: Exception) {
            println("Failed to load portfolio value!")
            println(e.localizedMessage)
            println(e.stackTrace)
        }
    }
    suspend fun getAccumulationHistory(call: ApplicationCall) {
        try {
            val tokenInput = call.parameters["asset"] ?: return call.respondText(
                "Missing or malformed token",
                status = HttpStatusCode.BadRequest
            )

            val dayInput = call.parameters["days"]?.toIntOrNull() ?: return call.respondText(
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
}