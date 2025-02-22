package com.bitcointracker.service.routes

import com.bitcointracker.model.api.QuickLookData
import com.bitcointracker.service.BackendService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import javax.inject.Inject

/**
 * Handler for cryptocurrency metadata-related HTTP routes.
 * Provides endpoints for retrieving asset holdings, portfolio values, and accumulation history.
 *
 * @property service Backend service for processing cryptocurrency data
 */
class MetadataRouteHandler @Inject constructor(
    private val service: BackendService
) {

    /**
     * Retrieves the holdings for a specific cryptocurrency asset.
     * Currently only supports USD as the currency.
     *
     * @param call The application call containing the asset parameter
     * @throws Exception if there's an error retrieving the asset holdings
     */
    suspend fun getAssetHoldings(call: ApplicationCall) {
        val assetInput = call.parameters["asset"] ?: return call.respondText(
            "Missing or malformed asset",
            status = HttpStatusCode.BadRequest
        )

        try {
            val assetHoldings = service.getAssetHoldings(assetInput, "USD") // TODO: support other currencies

            call.respond(assetHoldings)
        } catch (e: Exception) {
            println("Failed to load asset holdings!")
            println(e.localizedMessage)
            println(e.stackTrace)
        }

    }

    /**
     * Retrieves the total portfolio value in the specified fiat currency.
     * Currently only supports USD as the currency.
     *
     * @param call The application call containing the fiat currency parameter
     * @throws Exception if there's an error retrieving the portfolio value
     */
    suspend fun getPortfolioValue(call: ApplicationCall) {
        val fiatInput = call.parameters["fiat"] ?: return call.respondText(
            "Missing or malformed fiat",
            status = HttpStatusCode.BadRequest
        )

        try {
            println("Getting portfolio value for $fiatInput")
            val value = service.getPortfolioValue(fiatInput) // TODO: support other currencies
            println("portfolio value: $value")

            call.respond(value)
        } catch (e: Exception) {
            println("Failed to load portfolio value!")
            println(e.localizedMessage)
            println(e.stackTrace)
        }

    }

    /**
     * Retrieves the accumulation history for a specific cryptocurrency asset over a specified number of days.
     * Returns a QuickLookData object containing the accumulation data and formatted display information.
     *
     * @param call The application call containing the asset and days parameters
     * @throws Exception if there's an error retrieving the accumulation history
     */
    suspend fun getAccumulationHistory(call: ApplicationCall) {
        val tokenInput = call.parameters["asset"] ?: return call.respondText(
            "Missing or malformed token",
            status = HttpStatusCode.BadRequest
        )

        val dayInput = call.parameters["days"]?.toIntOrNull() ?: return call.respondText(
            "Days parameter must be a number",
            status = HttpStatusCode.BadRequest
        )

        println("Fetching accumulation data for $tokenInput - $dayInput days")

        try {
            val data = service.getAccumulation(dayInput, tokenInput)
            call.respond(
                QuickLookData(
                    title = "$tokenInput Accumulation",
                    value = "${data.last()} $tokenInput",
                    data = data,
                )
            )
        } catch (ex: Exception) {
            println(ex)
        }

    }
}
