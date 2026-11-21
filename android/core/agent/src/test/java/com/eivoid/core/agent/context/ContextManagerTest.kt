package com.eivoid.core.agent.context

import com.eivoid.core.ai.ChatMessage
import com.eivoid.core.ai.Role
import com.eivoid.core.ai.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextManagerTest {

    private val manager = ContextManager()

    private fun message(role: Role, text: String) = ChatMessage(role = role, content = text)

    @Test
    fun `a short history is kept whole`() {
        val messages = listOf(
            message(Role.SYSTEM, "system"),
            message(Role.USER, "hello"),
            message(Role.ASSISTANT, "hi"),
        )

        val trimmed = manager.trim(messages, maxTokens = 100_000)

        assertEquals(3, trimmed.size)
    }

    @Test
    fun `the system prompt always survives`() {
        val messages = buildList {
            add(message(Role.SYSTEM, "system"))
            repeat(40) { add(message(Role.USER, "x".repeat(500))) }
        }

        val trimmed = manager.trim(messages, maxTokens = 500)

        assertEquals(Role.SYSTEM, trimmed.first().role)
        assertTrue(trimmed.size < messages.size)
    }

    @Test
    fun `old turns are dropped until the request fits`() {
        val messages = buildList {
            add(message(Role.SYSTEM, "system"))
            repeat(20) { add(message(Role.USER, "x".repeat(1_000))) }
        }

        val trimmed = manager.trim(messages, maxTokens = 1_000)

        assertTrue("kept ${trimmed.size} messages", trimmed.size < messages.size)
        assertTrue(manager.estimateTokens(trimmed) <= 1_200)
    }

    @Test
    fun `a tool result keeps the call that asked for it`() {
        val call = ToolCall(
            id = "call_1",
            name = "read_file",
            arguments = kotlinx.serialization.json.buildJsonObject { },
        )
        val messages = listOf(
            message(Role.SYSTEM, "system"),
            message(Role.USER, "read the file"),
            ChatMessage(Role.ASSISTANT, "", toolCalls = listOf(call)),
            message(Role.TOOL, "file contents"),
        )

        val trimmed = manager.trim(messages, maxTokens = 10)

        assertTrue(trimmed.any { it.role == Role.ASSISTANT && it.toolCalls.isNotEmpty() })
        assertTrue(trimmed.any { it.role == Role.TOOL })
    }

    @Test
    fun `the estimate grows with the text`() {
        val short = manager.estimateTokens(listOf(message(Role.USER, "hi")))
        val long = manager.estimateTokens(listOf(message(Role.USER, "x".repeat(4_000))))

        assertTrue(long > short)
    }
}
