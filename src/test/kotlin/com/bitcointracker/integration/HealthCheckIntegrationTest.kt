package com.bitcointracker.integration

import com.bitcointracker.module
import com.bitcointracker.dagger.component.DaggerAppComponent
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthCheckIntegrationTest {
    
    @Test
    fun testHealthEndpoint() = testApplication() {
        val appComponent = DaggerAppComponent.create()
        application {
            module(appComponent)
        }
        
        client.get("/health").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("OK", bodyAsText())
        }
    }
} 