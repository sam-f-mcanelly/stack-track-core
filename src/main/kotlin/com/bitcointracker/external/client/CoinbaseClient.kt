package com.bitcointracker.external.client

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

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
 * val client = CoinbaseClient(okHttpClient, gson)
 * val btcPrice = client.getCurrentPrice("BTC", "USD")
 * ```
 *
 * @property client OkHttpClient instance for making HTTP requests
 * @property gson Gson instance for JSON parsing
 * @constructor Creates a CoinbaseClient with the specified HTTP client and JSON parser
 */
class CoinbaseClient @Inject constructor(
    private val client: OkHttpClient,
    private val gson: Gson,
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
        val jsonObject: JsonObject = gson.fromJson(json, JsonObject::class.java)
        val price = jsonObject.getAsJsonObject("data").get("amount").asString
        return price.toDoubleOrNull()
    }
}