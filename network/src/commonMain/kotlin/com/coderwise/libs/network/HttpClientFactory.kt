package com.coderwise.libs.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** How [createHttpClient] sets up a client. The defaults suit a client that only calls absolute URLs. */
data class HttpClientOptions(
    /**
     * Where relative requests and Ktor `Resources` go, for example `https://api.example.com`. Null
     * means every request names its own host. [localDevServerUrl] gives a server on this machine.
     */
    val baseUrl: String? = null,
    /**
     * How much of each call to log. Off by default: URLs and bodies can carry user data, so turn it
     * on only in debug builds. The `Authorization` header is never logged.
     */
    val logLevel: LogLevel = LogLevel.NONE,
    /** Which calls to log, when logging is on. All of them by default. */
    val logFilter: ((HttpRequestBuilder) -> Boolean)? = null,
    /**
     * How many times to retry a request the server turned away with 429 Too Many Requests, waiting
     * longer each time (exponentially, up to 10 seconds). 0 turns retrying off.
     */
    val retriesOnTooManyRequests: Int = 0,
    /** How JSON bodies are read and written. Lenient, and ignores fields it doesn't know. */
    val json: Json = DefaultJson,
) {
    companion object {
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

/**
 * A JSON client on this platform's engine: Android's on Android, Darwin on iOS, the Java 11 client
 * on desktop, and the browser's fetch on the web. [configure] runs last, for anything app-specific.
 */
fun createHttpClient(
    options: HttpClientOptions = HttpClientOptions(),
    configure: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = platformHttpClient {
    applyOptions(options)
    configure()
}

internal fun HttpClientConfig<*>.applyOptions(options: HttpClientOptions, logger: Logger = platformLogger) {
    install(ContentNegotiation) { json(options.json) }
    install(Resources)
    if (options.logLevel != LogLevel.NONE) {
        install(Logging) {
            this.logger = logger
            level = options.logLevel
            sanitizeHeader { it == HttpHeaders.Authorization }
            options.logFilter?.let { filter(it) }
        }
    }
    if (options.retriesOnTooManyRequests > 0) {
        install(HttpRequestRetry) {
            maxRetries = options.retriesOnTooManyRequests
            retryIf { _, response -> response.status == HttpStatusCode.TooManyRequests }
            exponentialDelay(base = 2.0, maxDelayMs = 10_000L)
        }
    }
    options.baseUrl?.let { base -> defaultRequest { url(base) } }
}

/**
 * The address of a server running on the development machine, as this device reaches it: the
 * Android emulator sees the host as `10.0.2.2`, everything else as `localhost`.
 */
expect fun localDevServerUrl(port: Int = 8080): String

internal expect fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

internal expect val platformLogger: Logger
