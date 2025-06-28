package com.bitcointracker.external.client

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import jakarta.inject.Inject

/**
 * Client for interacting with the Coinbase API to fetch cryptocurrency prices.
 *
 * This client provides functionality to:
 * 1. Fetch current spot prices for cryptocurrencies
 * 2. Parse Coinbase API responses
 * 3. Handle error cases with fallback values
 *
 * Example usage:
 * ```
 * val client = CoinbaseClient(okHttpClient, objectMapper)
 * val btcPrice = client.getCurrentPrice("BTC", "USD")
 * ```
 *
 * @property client OkHttpClient instance for making HTTP requests
 * @property objectMapper Jackson instance for JSON parsing
 * @constructor Creates a CoinbaseClient with the specified HTTP client and JSON parser
 */
class CoinbaseClient @Inject constructor(
    private val client: OkHttpClient,
    private val objectMapper: ObjectMapper,
) {
    /**
     * Fetches the current spot price for a cryptocurrency in the specified currency.
     *
     * Makes a GET request to the Coinbase API endpoint:
     * https://api.coinbase.com/v2/prices/{crypto}-{currency}/spot
     *
     * @param crypto The cryptocurrency code (e.g., "BTC", "ETH")
     * @param currency The fiat currency code for the price (defaults to "USD")
     * @return The current price as a Double, or 59000.0 if the request fails
     *
     * @throws IOException if there's a network error (caught internally)
     *
     * Example Response:
     * ```json
     * {
     *   "data": {
     *     "base": "BTC",
     *     "currency": "USD",
     *     "amount": "50000.00"
     *   }
     * }
     * ```
     */
    fun getCurrentPrice(crypto: String, currency: String = "USD"): Double? {
        val url = "https://api.coinbase.com/v2/prices/$crypto-$currency/spot"
        println("Coinbase URL: $url")
        val request = Request.Builder()
                .url(url)
                .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val responseBody = response.body?.string()
                return responseBody?.let { parsePrice(it) }
                    .also { println("Price of $crypto: $it $currency") }
            }
        } catch (ex: Exception) {
            println("Failure getting current price for $crypto from coinbase")
            return 59000.0
        }
    }

    /**
     * Retrieves the historical spot price for a cryptocurrency on a specific date using the Coinbase API.
     *
     * This function queries the Coinbase API v2 spot price endpoint with a date parameter to get
     * historical cryptocurrency prices. The endpoint returns the spot price that was valid on
     * the specified date.
     *
     * @param crypto The cryptocurrency symbol (e.g., "BTC", "ETH", "LTC")
     * @param currency The target currency for the price (defaults to "USD")
     * @param date The date for which to retrieve the price in YYYY-MM-DD format (UTC timezone)
     * @return The historical price as a Double, or null if the request fails or data is unavailable
     *
     * @throws IOException If there's a network error or the API returns an unexpected response
     *
     * @sample
     * ```
     * // Get Bitcoin price on January 1, 2024
     * val btcPrice = getHistoricalPrice("BTC", "USD", "2024-01-01")
     *
     * // Get Ethereum price on December 25, 2023 in EUR
     * val ethPrice = getHistoricalPrice("ETH", "EUR", "2023-12-25")
     * ```
     *
     * @see getCurrentPrice For getting current real-time prices
     * @see parsePrice For the JSON parsing implementation
     *
     * Note: This endpoint does not require authentication. Date must be in YYYY-MM-DD format
     * and represents UTC timezone. Historical data availability may vary depending on when
     * the cryptocurrency was first listed on Coinbase.
     */
    fun getHistoricalPrice(crypto: String = "BTC", currency: String = "USD", date: String): Double? {
        val url = "https://api.coinbase.com/v2/prices/$crypto-$currency/spot?date=$date"
        println("Coinbase Historical URL: $url")
        val request = Request.Builder()
            .url(url)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val responseBody = response.body?.string()
                return responseBody?.let { parsePrice(it) }
                    .also { println("Historical price of $crypto on $date: $it $currency") }
            }
        } catch (ex: Exception) {
            println("Failure getting historical price for $crypto from coinbase on $date")
            return null
        }
    }

    /**
     * Parses the price from a Coinbase API JSON response.
     *
     * @param json The JSON string from the Coinbase API response
     * @return The parsed price as a Double, or null if parsing fails
     *
     * Expected JSON structure:
     * ```json
     * {
     *   "data": {
     *     "amount": "50000.00"
     *   }
     * }
     * ```
     */
    private fun parsePrice(json: String): Double? {
        val jsonNode = objectMapper.readTree(json)
        val price = jsonNode.path("data").path("amount").asText()
        return price.toDoubleOrNull()
    }
}