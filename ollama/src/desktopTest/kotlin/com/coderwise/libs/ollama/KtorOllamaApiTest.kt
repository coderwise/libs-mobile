package com.coderwise.libs.ollama

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorOllamaApiTest {
    private val requests = mutableListOf<Pair<String, String>>()

    private fun api(handler: MockRequestHandleScope.(HttpRequestData, String) -> HttpResponseData): KtorOllamaApi {
        val engine = MockEngine { request ->
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
            requests += request.url.encodedPath to body
            handler(request, body)
        }
        return KtorOllamaApi(HttpClient(engine) {
            install(HttpTimeout)
            install(ContentNegotiation) { json(Json { explicitNulls = false }) }
        }, "http://ollama/")
    }

    private fun MockRequestHandleScope.ok(body: String) =
        respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))

    @Test fun `models lists the installed names`() = runTest {
        val api = api { _, _ -> ok("""{"models":[{"name":"a:1","size":5},{"name":"b"}]}""") }
        assertEquals(listOf("a:1", "b"), api.models())
    }

    @Test fun `chat sends no stream and returns the message with usage`() = runTest {
        val api = api { _, _ ->
            ok("""{"message":{"role":"assistant","content":"hi"},"prompt_eval_count":3,"eval_count":2,"total_duration":5000000}""")
        }
        val reply = api.chat("m", listOf(OllamaMessage(OllamaMessage.USER, "yo")))
        assertEquals("hi", reply.content)
        assertEquals(OllamaUsage(3, 2, 5), reply.usage)
        assertTrue("\"stream\":false" in requests.single().second, requests.single().second)
    }

    @Test fun `stream joins chunks and reports partials`() = runTest {
        val api = api { _, _ ->
            ok(
                """{"model":"m","message":{"role":"assistant","thinking":"hm"}}
{"model":"m","message":{"role":"assistant","content":"he"}}

{"model":"m","message":{"role":"assistant","content":"llo"},"done":true,"total_duration":1000000}
""",
            )
        }
        val partials = mutableListOf<Pair<String, String>>()
        val turn = api.stream("m", listOf(OllamaMessage(OllamaMessage.USER, "x")), think = true) { t, c -> partials += t to c }
        assertEquals("hello", turn.content)
        assertEquals("hm", turn.thinking)
        assertEquals("m", turn.model)
        assertEquals("he", partials[1].second)
        assertTrue("\"think\":true" in requests.single().second)
    }

    @Test fun `stream asks again without thinking when the model cannot reason`() = runTest {
        var first = true
        val api = api { _, _ ->
            if (first) { first = false; respond("""{"error":"\"m\" does not support think"}""", HttpStatusCode.BadRequest) }
            else ok("""{"message":{"role":"assistant","content":"ok"}}""")
        }
        val turn = api.stream("m", listOf(OllamaMessage(OllamaMessage.USER, "x")), think = true)
        assertEquals("ok", turn.content)
        assertEquals(2, requests.size)
        assertTrue("think" !in requests[1].second)
    }

    @Test fun `stream reports a model that rejects tools`() = runTest {
        val api = api { _, _ -> respond("""{"error":"m does not support tools"}""", HttpStatusCode.BadRequest) }
        val tool = OllamaTool(OllamaToolFunction("t", "d", kotlinx.serialization.json.JsonObject(emptyMap())))
        assertFailsWith<OllamaToolsUnsupportedException> { api.stream("m", emptyList(), listOf(tool)) }
    }

    @Test fun `stream surfaces an error line`() = runTest {
        val api = api { _, _ -> ok("""{"error":"boom"}""") }
        assertEquals("Ollama: boom", assertFailsWith<OllamaException> { api.stream("m", emptyList()) }.message)
    }

    @Test fun `contextLength reads the architecture entry and is null on failure`() = runTest {
        assertEquals(8192, api { _, _ -> ok("""{"model_info":{"general.x":1,"llama.context_length":8192}}""") }.contextLength("m"))
        assertNull(api { _, _ -> respond("", HttpStatusCode.NotFound) }.contextLength("m"))
    }

    @Test fun `load sends an empty chat`() = runTest {
        api { _, _ -> ok("{}") }.load("m")
        assertTrue("\"messages\":[]" in requests.single().second, requests.single().second)
    }
}
