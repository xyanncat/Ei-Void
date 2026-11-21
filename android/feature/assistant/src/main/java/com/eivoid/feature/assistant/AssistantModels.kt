package com.eivoid.feature.assistant

/** Who wrote a message in the assistant conversation. */
enum class AssistantRole { USER, ASSISTANT }

/** One turn in the assistant conversation. */
data class AssistantMessage(
    val id: String,
    val role: AssistantRole,
    val text: String,
)

/** A ready-made prompt shown as a suggestion chip and in Explore. */
data class AssistantTask(val emoji: String, val label: String)

/**
 * Two rows of four prompts, in the order they appear on the home screen.
 */
val TASK_ROWS: List<List<AssistantTask>> = listOf(
    listOf(
        AssistantTask("📅", "Track deadlines"),
        AssistantTask("💳", "Check subscriptions"),
        AssistantTask("🧾", "Plan a payment"),
        AssistantTask("🧠", "Summarize a repo"),
    ),
    listOf(
        AssistantTask("🗣️", "Learn a language"),
        AssistantTask("📰", "Track industry news"),
        AssistantTask("✉️", "Draft an email"),
        AssistantTask("💻", "Review my code"),
    ),
)

/** Every task in reading order, used by the task picker and Explore. */
val ALL_TASKS: List<AssistantTask> get() = TASK_ROWS.flatten()

/**
 * The assistant's brief. It is deliberately short: the provider tiers are
 * small, so the prompt spends its words on behaviour, not persona.
 */
const val ASSISTANT_SYSTEM_PROMPT: String =
    "You are Ei Void, a concise and helpful assistant inside the Ei Void app. " +
        "Answer in short, clear paragraphs and get to the point. " +
        "If the user asks about reminders or code, explain that Projects and " +
        "Library live in the left drawer, and that reminders can be added from " +
        "the plus button in the ask bar. Never invent account details and never " +
        "ask for an API key."
