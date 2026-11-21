package com.coderwise.libs.network

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.logging.Logger

actual fun localDevServerUrl(port: Int): String = "http://10.0.2.2:$port"

internal actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Android, block)

// Logcat, where Android developers look; the default logger writes to stdout.
internal actual val platformLogger: Logger = object : Logger {
    override fun log(message: String) {
        Log.d("HttpClient", message)
    }
}
