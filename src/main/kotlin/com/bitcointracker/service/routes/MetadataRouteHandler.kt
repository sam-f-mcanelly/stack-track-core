package com.bitcointracker.service.routes

import com.bitcointracker.model.api.QuickLookData
import com.bitcointracker.service.BackendService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.coroutines.launch
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
    fun getAssetHoldings(call: ApplicationCall) {
        call.application.launch {
            try {
                val assetInput = call.parameters["asset"] ?: return@launch call.respondText(
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
    }

    /**
     * Retrieves the total portfolio value in the specified fiat currency.
     * Currently only supports USD as the currency.
     *
     * @param call The application call containing the fiat currency parameter
     * @throws Exception if there's an error retrieving the portfolio value
     */
    fun getPortfolioValue(call: ApplicationCall) {
        call.application.launch {
            try {
                val fiatInput = call.parameters["fiat"] ?: return@launch call.respondText(
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
    }

    /**
     * Retrieves the accumulation history for a specific cryptocurrency asset over a specified number of days.
     * Returns a QuickLookData object containing the accumulation data and formatted display information.
     *
     * @param call The application call containing the asset and days parameters
     * @throws Exception if there's an error retrieving the accumulation history
     */
    fun getAccumulationHistory(call: ApplicationCall) {
        call.application.launch {
            try {
                val tokenInput = call.parameters["asset"] ?: return@launch call.respondText(
                    "Missing or malformed token",
                    status = HttpStatusCode.BadRequest
                )

                val dayInput = call.parameters["days"]?.toIntOrNull() ?: return@launch call.respondText(
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
}
