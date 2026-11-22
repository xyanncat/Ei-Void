package com.eivoid.feature.assistant

/** Who wrote a message in the assistant conversation. */
enum class AssistantRole { USER, ASSISTANT }

/** One turn in the assistant conversation. */
data class AssistantMessage(
    val id: String,
    val role: AssistantRole,
    val text: String,
)

/** The line icon used by a task card. Kept as a semantic value, not a glyph. */
enum class AssistantTaskIcon {
    CALENDAR,
    RECEIPT,
    CODE,
    EDIT,
    NEWS,
    LANGUAGE,
}

/**
 * A ready-made prompt. The label is what the product shows; the prompt is what
 * lands in the ask bar, so the card can stay short without losing intent.
 */
data class AssistantTask(
    val label: String,
    val description: String,
    val prompt: String,
    val icon: AssistantTaskIcon,
)

/** The four capabilities on the assistant home. */
val HOME_TASKS: List<AssistantTask> = listOf(
    AssistantTask(
        label = "Plan my day",
        description = "Turn due dates and priorities into a plan.",
        prompt = "Help me plan my day around my reminders and priorities.",
        icon = AssistantTaskIcon.CALENDAR,
    ),
    AssistantTask(
        label = "Review bills",
        description = "Renewals, subscriptions and due dates.",
        prompt = "Review my upcoming bills and subscriptions and flag anything that needs attention.",
        icon = AssistantTaskIcon.RECEIPT,
    ),
    AssistantTask(
        label = "Review code",
        description = "Summarize a repository or review a change.",
        prompt = "Review the active project and call out bugs, risky changes and missing tests.",
        icon = AssistantTaskIcon.CODE,
    ),
    AssistantTask(
        label = "Draft a message",
        description = "Write a clear email, reply or update.",
        prompt = "Draft a concise, professional message for me.",
        icon = AssistantTaskIcon.EDIT,
    ),
)

/** The long-form list used by the task picker and Explore. */
val ALL_TASKS: List<AssistantTask> = HOME_TASKS + listOf(
    AssistantTask(
        label = "Catch up on news",
        description = "Summarize what changed in a field you follow.",
        prompt = "Summarize the most important recent developments in the field I follow.",
        icon = AssistantTaskIcon.NEWS,
    ),
    AssistantTask(
        label = "Learn something",
        description = "Get a short, structured explanation.",
        prompt = "Teach me the essentials of a topic in a short, structured lesson.",
        icon = AssistantTaskIcon.LANGUAGE,
    ),
)

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
