package com.voidapp.core.agent

import com.voidapp.core.ai.ToolCall

/** Everything the engine reports while it works. */
sealed interface AgentEvent {
    /** The engine picked a tier. [reason] explains why. */
    data class Thinking(val tier: String, val reason: String) : AgentEvent

    /** A piece of the assistant's answer. */
    data class Token(val text: String) : AgentEvent

    data class ToolCallStarted(val call: ToolCall) : AgentEvent

    data class ToolCallFinished(val call: ToolCall, val result: ToolResult) : AgentEvent

    /** The plan the engine works through. */
    data class PlanUpdated(val steps: List<String>) : AgentEvent

    /** The run is over. */
    data class Finished(val summary: String) : AgentEvent

    /** The run stopped early. */
    data class Failed(val message: String) : AgentEvent
}
