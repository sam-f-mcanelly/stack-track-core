package com.bitcointracker.unit.external.client

import com.bitcointracker.external.client.CoinbaseClient
import com.google.gson.Gson
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [CoinbaseClient] which handles price fetching from Coinbase API.
 *
 * These tests verify:
 * 1. Successful price retrieval and parsing
 * 2. Error handling for unsuccessful HTTP responses
 * 3. Error handling for malformed JSON responses
 *
 * @see CoinbaseClient
 */
class CoinbaseClientTest {
    
    @MockK
    private lateinit var mockOkHttpClient: OkHttpClient
    
    @MockK
    private lateinit var mockCall: Call
    
    @MockK
    private lateinit var mockResponse: Response
    
    @MockK
    private lateinit var mockResponseBody: ResponseBody
    
    private val gson = Gson()
    
    private lateinit var coinbaseClient: CoinbaseClient
    
    /**
     * Sets up the test environment before each test.
     * Initializes all mocks and creates a new instance of [CoinbaseClient].
     */
    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        coinbaseClient = CoinbaseClient(mockOkHttpClient, gson)
    }
    
    /**
     * Tests successful price retrieval from Coinbase API.
     * 
     * Verifies that:
     * 1. The correct URL is called
     * 2. The JSON response is properly parsed
     * 3. The price is correctly extracted and returned
     */
    @Test
    fun `getCurrentPrice returns parsed price for successful response`() {
        // Given
        val jsonResponse = """
            {
                "data": {
                    "base": "BTC",
                    "currency": "USD",
                    "amount": "50000.00"
                }
            }
        """.trimIndent()
        
        val request = Request.Builder()
            .url("https://api.coinbase.com/v2/prices/BTC-USD/spot")
            .build()
        
        every { mockOkHttpClient.newCall(match { 
            it.url.toString() == request.url.toString() 
        }) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns mockResponseBody
        every { mockResponseBody.string() } returns jsonResponse
        every { mockResponse.close() } returns Unit
        
        // When
        val result = coinbaseClient.getCurrentPrice("BTC", "USD")
        
        // Then
        assertEquals(50000.00, result)
        verify { 
            mockOkHttpClient.newCall(match {
                it.url.toString() == request.url.toString() 
            })
            mockCall.execute()
            mockResponseBody.string()
            mockResponse.close()
        }
    }
    
    /**
     * Tests error handling when the HTTP response is unsuccessful.
     * 
     * Verifies that:
     * 1. The correct URL is called
     * 2. A hardcoded fallback value is returned when the response fails
     * 3. Resources are properly cleaned up
     */
    @Test
    fun `getCurrentPrice returns hardcoded value when response is unsuccessful`() {
        // Given
        val request = Request.Builder()
            .url("https://api.coinbase.com/v2/prices/BTC-USD/spot")
            .build()
            
        every { mockOkHttpClient.newCall(match { 
            it.url.toString() == request.url.toString() 
        }) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns false
        every { mockResponse.close() } returns Unit
        
        // When
        val result = coinbaseClient.getCurrentPrice("BTC", "USD")
        
        // Then
        assertEquals(59000.0, result)
        verify { 
            mockOkHttpClient.newCall(match { 
                it.url.toString() == request.url.toString() 
            })
            mockCall.execute()
            mockResponse.close()
        }
    }
    
    /**
     * Tests error handling when the response contains malformed JSON.
     * 
     * Verifies that:
     * 1. The correct URL is called
     * 2. A hardcoded fallback value is returned when JSON parsing fails
     * 3. Resources are properly cleaned up
     */
    @Test
    fun `getCurrentPrice returns hardcoded value for malformed JSON response`() {
        // Given
        val invalidJson = "{ invalid json }"
        val request = Request.Builder()
            .url("https://api.coinbase.com/v2/prices/BTC-USD/spot")
            .build()
            
        every { mockOkHttpClient.newCall(match { 
            it.url.toString() == request.url.toString() 
        }) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns mockResponseBody
        every { mockResponseBody.string() } returns invalidJson
        every { mockResponse.close() } returns Unit
        
        // When
        val result = coinbaseClient.getCurrentPrice("BTC", "USD")
        
        // Then
        assertEquals(59000.0, result)
        verify { 
            mockOkHttpClient.newCall(match { 
                it.url.toString() == request.url.toString() 
            })
            mockCall.execute()
            mockResponseBody.string()
            mockResponse.close()
        }
    }
}