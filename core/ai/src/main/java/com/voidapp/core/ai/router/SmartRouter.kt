package com.voidapp.core.ai.router

import com.voidapp.core.ai.AiProvider
import com.voidapp.core.ai.Model
import com.voidapp.core.ai.Tier

/** A model the user can reach right now. */
data class Candidate(
    val provider: AiProvider,
    val model: Model,
)

/** The provider and model a run should use. */
data class Route(
    val providerId: String,
    val model: Model,
    /** Why this model was chosen; shown in the UI. */
    val reason: String,
)

/**
 * Picks the model for a task.
 *
 * The user's default model wins, except for complex tasks (refactor, scaffold)
 * where the router upgrades to a stronger model. Free models are preferred
 * when the task does not need more.
 */
class SmartRouter(
    private val classifier: TaskClassifier = TaskClassifier(),
    /** The model id the user set in Settings, if any. */
    private val defaultModelId: () -> String? = { null },
) {

    fun classify(task: String): TaskKind = classifier.classify(task)

    /** The model to use, or null when no provider is reachable. */
    fun pick(task: String, candidates: List<Candidate>): Route? {
        if (candidates.isEmpty()) return null

        val kind = classify(task)
        val ranked = rank(kind, candidates)

        val userDefault = defaultModelId()
        val defaultCandidate = ranked.firstOrNull { it.model.id == userDefault }
        val best = ranked.first()

        val chosen = when {
            defaultCandidate == null -> best
            // A complex task may need more than the default model.
            kind.isComplex && best.model.tier == Tier.STRONG -> best
            else -> defaultCandidate
        }

        return Route(
            providerId = chosen.provider.id,
            model = chosen.model,
            reason = reasonFor(chosen, kind, best, defaultCandidate),
        )
    }

    /** The next candidate after a provider failed. */
    fun next(task: String, candidates: List<Candidate>, failed: Set<String>): Route? {
        val kind = classify(task)
        return rank(kind, candidates)
            .firstOrNull { it.provider.id !in failed }
            ?.let {
                Route(
                    providerId = it.provider.id,
                    model = it.model,
                    reason = "fallback after ${failed.joinToString()}",
                )
            }
    }

    private fun rank(kind: TaskKind, candidates: List<Candidate>): List<Candidate> {
        val preferred = preferredTier(kind)
        val tierOrder = when (preferred) {
            Tier.FAST -> listOf(Tier.FAST, Tier.STANDARD, Tier.STRONG)
            Tier.STANDARD -> listOf(Tier.STANDARD, Tier.STRONG, Tier.FAST)
            Tier.STRONG -> listOf(Tier.STRONG, Tier.STANDARD, Tier.FAST)
        }

        return candidates.sortedWith(
            compareBy<Candidate> { tierOrder.indexOf(it.model.tier) }
                .thenBy { if (it.model.isFree) 0 else 1 }
                .thenBy { it.model.id },
        )
    }

    private fun preferredTier(kind: TaskKind): Tier = when (kind) {
        TaskKind.EXPLAIN, TaskKind.EDIT -> Tier.FAST
        TaskKind.REVIEW, TaskKind.REFACTOR -> Tier.STANDARD
        TaskKind.SCAFFOLD -> Tier.STRONG
    }

    private fun reasonFor(
        chosen: Candidate,
        kind: TaskKind,
        best: Candidate,
        defaultCandidate: Candidate?,
    ): String = when {
        defaultCandidate == null -> "best free ${kind.name.lowercase()} model"
        chosen.model.id != defaultCandidate.model.id -> "upgraded for a ${kind.name.lowercase()} task"
        best.model.id == chosen.model.id -> "your default model"
        else -> "your default model"
    }
}
