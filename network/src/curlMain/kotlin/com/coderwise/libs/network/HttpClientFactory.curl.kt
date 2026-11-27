package com.coderwise.libs.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.curl.Curl
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.Logger

actual fun localDevServerUrl(port: Int): String = "http://localhost:$port"

internal actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Curl, block)

internal actual val platformLogger: Logger = Logger.DEFAULT
