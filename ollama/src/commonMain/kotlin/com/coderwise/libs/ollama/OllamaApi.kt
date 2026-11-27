package com.coderwise.libs.ollama

/** A failed call to Ollama; the message says what it reported. */
open class OllamaException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** The model rejected the `tools` field. */
class OllamaToolsUnsupportedException(val model: String) : OllamaException("Model '$model' does not support tools")

/** The part of the Ollama REST API the coderwise apps use. */
interface OllamaApi {
    /** Names of the installed models. */
    suspend fun models(): List<String>

    /** One non-streamed chat turn: returns the assistant's message, which may ask for [tools] to be called. */
    suspend fun chat(model: String, messages: List<OllamaMessage>, tools: List<OllamaTool> = emptyList()): OllamaMessage

    /**
     * One streamed chat turn, so a slow model keeps the connection alive. [onPartial] gets the reasoning and the
     * reply written so far. With [think] a model that can reason does so first (a model that cannot is asked again
     * without). Throws [OllamaToolsUnsupportedException] if the model rejects [tools].
     */
    suspend fun stream(
        model: String,
        messages: List<OllamaMessage>,
        tools: List<OllamaTool> = emptyList(),
        think: Boolean = false,
        onPartial: (thinking: String, text: String) -> Unit = { _, _ -> },
    ): OllamaTurn {
        val message = chat(model, messages, tools)
        return OllamaTurn(message.content, message.thinking, message.toolCalls.orEmpty(), model, message.usage)
    }

    /** The model's context window in tokens, if known. */
    suspend fun contextLength(model: String): Int? = null

    /** Loads [model] without generating anything. */
    suspend fun load(model: String) {}
}
