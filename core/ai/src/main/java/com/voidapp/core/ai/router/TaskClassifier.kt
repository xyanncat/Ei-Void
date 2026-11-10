package com.voidapp.core.ai.router

/** What the user asked the agent to do. */
enum class TaskKind {
    /** Read-only: explain, review, analyse. */
    EXPLAIN,
    REVIEW,

    /** Change existing code. */
    EDIT,

    /** Change the shape of existing code. */
    REFACTOR,

    /** Create something new. */
    SCAFFOLD,
    ;

    val isComplex: Boolean get() = this == REFACTOR || this == SCAFFOLD
}

/**
 * Labels a task from its wording. The wording is the only signal available:
 * there is no model in front of the classifier (that would cost a round trip
 * before every answer).
 */
class TaskClassifier {

    fun classify(task: String): TaskKind {
        val text = task.lowercase()

        return when {
            matches(text, SCAFFOLD_WORDS) -> TaskKind.SCAFFOLD
            matches(text, REFACTOR_WORDS) -> TaskKind.REFACTOR
            matches(text, REVIEW_WORDS) -> TaskKind.REVIEW
            matches(text, EXPLAIN_WORDS) -> TaskKind.EXPLAIN
            else -> TaskKind.EDIT
        }
    }

    private fun matches(text: String, words: List<String>) = words.any { text.contains(it) }

    private companion object {
        val SCAFFOLD_WORDS = listOf(
            "scaffold", "bootstrap", "set up a new", "create a new project",
            "create a new module", "new module", "from scratch", "initialise",
            "initialize", "stub out",
        )
        val REFACTOR_WORDS = listOf(
            "refactor", "restructure", "rename", "extract", "migrate",
            "move the", "split the", "rename the", "clean up",
        )
        val REVIEW_WORDS = listOf(
            "review", "audit", "look for bugs", "check for", "find bugs",
            "critique", "verify the",
        )
        val EXPLAIN_WORDS = listOf(
            "explain", "how does", "how do", "what is", "what does", "why does",
            "why is", "describe", "summarise", "summarize", "walk me through",
        )
    }
}
