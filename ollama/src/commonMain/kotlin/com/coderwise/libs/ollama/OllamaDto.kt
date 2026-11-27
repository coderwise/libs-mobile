package com.coderwise.libs.ollama

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/**
 * A chat message. An assistant message may carry [toolCalls]; the answer to each is a `tool` message with [toolName].
 * [thinking] is the reasoning of a thinking model and is only ever read from replies.
 */
@Serializable
data class OllamaMessage(
    val role: String,
    val content: String = "",
    val thinking: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OllamaToolCall>? = null,
    @SerialName("tool_name") val toolName: String? = null,
    /** Filled in from the response; never sent. */
    @Transient val usage: OllamaUsage? = null,
) {
    companion object {
        const val SYSTEM = "system"
        const val USER = "user"
        const val ASSISTANT = "assistant"
        const val TOOL = "tool"
    }
}

/** Token counts and time Ollama reports for one chat call. */
data class OllamaUsage(val promptTokens: Int, val outputTokens: Int, val durationMs: Long)

@Serializable
data class OllamaToolCall(val function: OllamaFunctionCall)

@Serializable
data class OllamaFunctionCall(val name: String, val arguments: JsonObject = JsonObject(emptyMap()))

/** A function the model may call. [parameters] is a JSON schema object. */
@Serializable
data class OllamaTool(val function: OllamaToolFunction) {
    @EncodeDefault
    val type: String = "function"
}

@Serializable
data class OllamaToolFunction(val name: String, val description: String, val parameters: JsonObject)

/** What the model said in one streamed turn: [toolCalls] when it asks for tools, else its reply in [content]. */
data class OllamaTurn(
    val content: String,
    val thinking: String?,
    val toolCalls: List<OllamaToolCall>,
    val model: String,
    val usage: OllamaUsage? = null,
)

// `stream` has no default on purpose: defaults are not encoded, and Ollama streams unless told otherwise.
@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean,
    /** Whether a model that can reason does so first; Ollama's default is model-specific. */
    val think: Boolean? = null,
    val tools: List<OllamaTool>? = null,
)

@Serializable
internal data class ChatResponse(
    val model: String = "",
    val message: OllamaMessage? = null,
    val error: String? = null,
    @SerialName("prompt_eval_count") val promptEvalCount: Int? = null,
    @SerialName("eval_count") val evalCount: Int? = null,
    @SerialName("total_duration") val totalDurationNanos: Long? = null,
)

@Serializable
internal data class ShowRequest(val model: String)

@Serializable
internal data class ShowResponse(@SerialName("model_info") val modelInfo: JsonObject = JsonObject(emptyMap()))

@Serializable
internal data class TagsResponse(val models: List<TagModel> = emptyList())

@Serializable
internal data class TagModel(val name: String)
