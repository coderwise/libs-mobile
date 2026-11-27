package com.coderwise.libs.ollama

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * [client] needs the HttpTimeout plugin installed (a reply can take minutes, so the per-request limits set here
 * replace its defaults) and must be able to send JSON bodies (ContentNegotiation with JSON).
 */
class KtorOllamaApi(private val client: HttpClient, baseUrl: String = DEFAULT_BASE_URL) : OllamaApi {
    private val baseUrl = baseUrl.trimEnd('/')
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun models(): List<String> {
        val response = client.get("$baseUrl/api/tags")
        response.requireSuccess()
        return json.decodeFromString(TagsResponse.serializer(), response.bodyAsText()).models.map { it.name }
    }

    override suspend fun chat(model: String, messages: List<OllamaMessage>, tools: List<OllamaTool>): OllamaMessage {
        val response = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(model, messages, stream = false, tools = tools.ifEmpty { null }))
            // A model that is still loading sends nothing for a long time, so the idle (socket) timeout must be long too.
            timeout {
                requestTimeoutMillis = CHAT_TIMEOUT_MILLIS
                socketTimeoutMillis = CHAT_TIMEOUT_MILLIS
            }
        }
        response.requireSuccess()
        val body = json.decodeFromString(ChatResponse.serializer(), response.bodyAsText())
        val message = body.message ?: throw OllamaException(body.error ?: "Ollama returned no message")
        return message.copy(usage = body.usage())
    }

    override suspend fun stream(
        model: String,
        messages: List<OllamaMessage>,
        tools: List<OllamaTool>,
        think: Boolean,
        onPartial: (thinking: String, text: String) -> Unit,
    ): OllamaTurn = try {
        streamOnce(model, messages, tools, think, onPartial)
    } catch (e: ThinkingUnsupported) {
        streamOnce(model, messages, tools, false, onPartial)
    }

    private suspend fun streamOnce(
        model: String,
        messages: List<OllamaMessage>,
        tools: List<OllamaTool>,
        think: Boolean,
        onPartial: (thinking: String, text: String) -> Unit,
    ): OllamaTurn {
        val thinking = StringBuilder()
        val text = StringBuilder()
        val calls = mutableListOf<OllamaToolCall>()
        var answeredBy = ""
        var usage: OllamaUsage? = null
        try {
            client.preparePost("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest(model, messages, stream = true, think = think.takeIf { it }, tools = tools.ifEmpty { null }))
                // No overall limit, and the socket may be quiet while a model loads or reasons.
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = CHAT_TIMEOUT_MILLIS
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val body = response.bodyAsText().trim()
                    if (tools.isNotEmpty() && "does not support tools" in body) throw OllamaToolsUnsupportedException(model)
                    // A model that can't reason rejects the request that asks it to.
                    if (think && "think" in body) throw ThinkingUnsupported()
                    throw OllamaException("Ollama request failed (${response.status.value}): ${body.ifEmpty { "empty response" }}")
                }
                val channel = response.bodyAsChannel()
                while (true) {
                    val line = channel.readLine() ?: break
                    if (line.isBlank()) continue
                    val chunk = json.decodeFromString(ChatResponse.serializer(), line)
                    chunk.error?.let { throw OllamaException("Ollama: $it") }
                    if (chunk.model.isNotBlank()) answeredBy = chunk.model
                    if (chunk.totalDurationNanos != null) usage = chunk.usage()
                    chunk.message?.thinking?.let { thinking.append(it) }
                    chunk.message?.content?.let { text.append(it) }
                    chunk.message?.toolCalls?.let { calls += it }
                    onPartial(thinking.toString(), text.toString())
                }
            }
        } catch (e: HttpRequestTimeoutException) {
            throw OllamaException("Ollama stopped responding for '$model'; is the model still loading?", e)
        } catch (e: SocketTimeoutException) {
            throw OllamaException("Ollama stopped responding for '$model'; is the model still loading?", e)
        }
        return OllamaTurn(text.toString(), thinking.toString().takeIf { it.isNotEmpty() }, calls, answeredBy.ifBlank { model }, usage)
    }

    /** The model's own limit, from the `<architecture>.context_length` entry of its details; null when Ollama doesn't say. */
    override suspend fun contextLength(model: String): Int? = try {
        val response = client.post("$baseUrl/api/show") {
            contentType(ContentType.Application.Json)
            setBody(ShowRequest(model))
        }
        if (!response.status.isSuccess()) null
        else json.decodeFromString(ShowResponse.serializer(), response.bodyAsText()).modelInfo.entries
            .firstOrNull { it.key.endsWith(".context_length") }?.value?.jsonPrimitive?.intOrNull
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** A chat request with no messages makes Ollama load the model without generating anything. */
    override suspend fun load(model: String) {
        val response = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(model, emptyList(), stream = false))
            timeout {
                requestTimeoutMillis = CHAT_TIMEOUT_MILLIS
                socketTimeoutMillis = CHAT_TIMEOUT_MILLIS
            }
        }
        if (!response.status.isSuccess()) throw OllamaException("Ollama could not load '$model' (${response.status.value})")
    }

    private suspend fun HttpResponse.requireSuccess() {
        if (!status.isSuccess()) throw OllamaException("Ollama ${status.value}: ${bodyAsText().trim()}")
    }

    private fun ChatResponse.usage() =
        OllamaUsage(promptEvalCount ?: 0, evalCount ?: 0, (totalDurationNanos ?: 0) / 1_000_000)

    private class ThinkingUnsupported : Exception()

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:11434"
        private const val CHAT_TIMEOUT_MILLIS = 600_000L
    }
}
