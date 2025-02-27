package com.bitcointracker.integration

import com.bitcointracker.module
import com.bitcointracker.dagger.component.DaggerAppComponent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileUploadIntegrationTest {

    companion object {
        // Define a constant for test file path
        // This could be replaced with an actual file in your project
        const val TEST_FILE_PATH = "src/test/resources/test-data.json"
    }

    private lateinit var testFile: File

    @BeforeEach
    fun setUp() {
        // Create a test file with some sample data if it doesn't exist
        testFile = File(TEST_FILE_PATH)
        if (!testFile.exists()) {
            testFile.parentFile.mkdirs()
            testFile.writeText("""
                {
                    "items": [
                        {"id": 1, "name": "Test Item 1", "amount": 100.50},
                        {"id": 2, "name": "Test Item 2", "amount": 200.75}
                    ]
                }
            """.trimIndent())
        }
    }

    @Test
    @Disabled("Need a test file")
    fun testFileUpload() = testApplication {
        // Set up the application with your DI component
        val appComponent = DaggerAppComponent.create()
        application {
            module(appComponent)
        }

        // Create a multipart form request with the test file
        val response = client.post("/api/data/upload") {
            setBody(MultiPartFormDataContent(
                formData {
                    append("file", testFile.readBytes(), Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"${testFile.name}\"")
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    })
                }
            ))
        }

        // Assert the response status is OK
        assertEquals(HttpStatusCode.OK, response.status)

        // Parse the response body if needed (assuming your API returns some confirmation message)
        val responseText = response.bodyAsText()
        assertTrue(responseText.isNotEmpty(), "Response body should not be empty")

        // Additional assertions could be added here based on your API's behavior
        // For example, verifying data was properly stored in the repository
    }

    @Test
    fun testFileUploadWithInvalidFile() = testApplication {
        // Set up the application with your DI component
        val appComponent = DaggerAppComponent.create()
        application {
            module(appComponent)
        }

        // Create an empty file for testing error scenarios
        val emptyFile = File("src/test/resources/empty.json")
        emptyFile.parentFile.mkdirs()
        emptyFile.writeText("")

        // Create a multipart form request with the empty file
        val response = client.post("/api/data/upload") {
            setBody(MultiPartFormDataContent(
                formData {
                    append("file", emptyFile.readBytes(), Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"${emptyFile.name}\"")
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    })
                }
            ))
        }

        // Depending on your API's behavior, you might expect a different status code for invalid files
        // This assertion might need to be adjusted
        assertTrue(
            response.status == HttpStatusCode.BadRequest ||
                    response.status == HttpStatusCode.InternalServerError,
            "Expected error status code for invalid file"
        )

        // Clean up the test file
        emptyFile.delete()
    }
}