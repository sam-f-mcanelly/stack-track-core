package com.bitcointracker.external.client

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import javax.inject.Inject

class CoinbaseClient @Inject constructor(
    private val client: OkHttpClient,
    private val gson: Gson,
) {
    fun getCurrentPrice(crypto: String, currency: String = "USD"): Double? {
        val url = "https://api.coinbase.com/v2/prices/$crypto-$currency/spot"
        val request = Request.Builder()
                .url(url)
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val responseBody = response.body?.string()
            return responseBody?.let { parsePrice(it) }
        }
    }

    private fun parsePrice(json: String): Double? {
        val jsonObject: JsonObject = gson.fromJson(json, JsonObject::class.java)
        val price = jsonObject.getAsJsonObject("data").get("amount").asString
        return price.toDoubleOrNull()
    }
}