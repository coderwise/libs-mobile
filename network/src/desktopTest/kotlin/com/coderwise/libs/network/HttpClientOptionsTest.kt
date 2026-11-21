package com.coderwise.libs.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpClientOptionsTest {

    private val requests = mutableListOf<String>()
    private val logLines = mutableListOf<String>()
    private val logger = object : Logger {
        override fun log(message: String) {
            logLines += message
        }
    }

    private fun client(options: HttpClientOptions, statuses: MutableList<HttpStatusCode> = mutableListOf()) =
        HttpClient(MockEngine { request ->
            requests += request.url.toString()
            respond(
                content = """{"name":"Reykjavik","population":140000}""",
                status = statuses.removeFirstOrNull() ?: HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { applyOptions(options, logger) }

    @Serializable
    private data class Place(val name: String)

    @Test
    fun `relative requests go to the base URL`() = runTest {
        client(HttpClientOptions(baseUrl = "https://api.example.com/v1/")).get("places")
        assertEquals("https://api.example.com/v1/places", requests.single())
    }

    @Test
    fun `absolute requests ignore the base URL`() = runTest {
        client(HttpClientOptions(baseUrl = "https://api.example.com/")).get("https://other.example.org/x")
        assertEquals("https://other.example.org/x", requests.single())
    }

    @Test
    fun `json ignores fields the model doesn't have`() = runTest {
        val place: Place = client(HttpClientOptions()).get("https://api.example.com/place").body()
        assertEquals(Place("Reykjavik"), place)
    }

    @Test
    fun `nothing is logged by default`() = runTest {
        client(HttpClientOptions()).get("https://api.example.com/place")
        assertTrue(logLines.isEmpty())
    }

    @Test
    fun `logging keeps the authorization header out`() = runTest {
        client(HttpClientOptions(logLevel = LogLevel.HEADERS)).get("https://api.example.com/place") {
            header(HttpHeaders.Authorization, "Bearer secret-token")
        }
        assertTrue(logLines.isNotEmpty())
        assertFalse(logLines.any { "secret-token" in it })
    }

    @Test
    fun `the log filter picks which calls are logged`() = runTest {
        val options = HttpClientOptions(
            logLevel = LogLevel.INFO,
            logFilter = { it.url.host == "api.example.com" },
        )
        client(options).get("https://files.example.com/download?token=abc")
        assertTrue(logLines.isEmpty())
    }

    @Test
    fun `too many requests is retried when asked`() = runTest {
        val statuses = mutableListOf(HttpStatusCode.TooManyRequests, HttpStatusCode.TooManyRequests)
        val response = client(HttpClientOptions(retriesOnTooManyRequests = 3), statuses).get("https://api.example.com/place")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(3, requests.size)
    }

    @Test
    fun `too many requests is not retried by default`() = runTest {
        val statuses = mutableListOf(HttpStatusCode.TooManyRequests)
        val response = client(HttpClientOptions(), statuses).get("https://api.example.com/place")
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(1, requests.size)
    }

    @Test
    fun `a dev server on this machine is localhost on desktop`() {
        assertEquals("http://localhost:8080", localDevServerUrl())
    }
}
