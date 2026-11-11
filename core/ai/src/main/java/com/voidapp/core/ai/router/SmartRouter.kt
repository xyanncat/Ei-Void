package com.voidapp.core.ai.router

import com.voidapp.core.ai.AiProvider
import com.voidapp.core.ai.Model
import com.voidapp.core.ai.Tier
import com.voidapp.core.ai.models.ModelCatalog
import com.voidapp.core.ai.plan.Plan

/** A model the user can reach right now. */
data class Candidate(
    val provider: AiProvider,
    val model: Model,
)

/** The provider and model a run should use. */
data class Route(
    val providerId: String,
    val model: Model,
    /** Why this tier was chosen; shown in the UI. */
    val reason: String,
) {
    /** What the user sees: "Red Dwarfs", never the provider's model id. */
    val tierName: String get() = model.displayName

    /** Identifies this exact choice, so failures can be tracked per model. */
    val key: String get() = "$providerId/${model.id}"
}

/**
 * Picks the tier for a task.
 *
 * - Explain and small edits go to Neutron Stars (small, fast).
 * - Reviews and refactors go to White Dwarfs (middle).
 * - Scaffolding goes to Red Dwarfs (high).
 *
 * The subscription plan filters the candidates first: a tier the plan does
 * not include is never chosen, and the task falls to the best tier the plan
 * does include.
 */
class SmartRouter(
    private val classifier: TaskClassifier = TaskClassifier(),
    /** The tier the user chose in Settings; null means "decide for me". */
    private val preferredTier: () -> Tier? = { null },
    /** The plan that decides which tiers are reachable. */
    private val plan: () -> Plan = { Plan.FREE },
) {

    fun classify(task: String): TaskKind = classifier.classify(task)

    /** The model to use, or null when no provider is reachable. */
    fun pick(task: String, candidates: List<Candidate>): Route? {
        val reachable = reachable(candidates)
        if (reachable.isEmpty()) return null

        val kind = classify(task)
        val wanted = wantedTier(kind)
        val ranked = rank(wanted, reachable)

        val chosen = ranked.first()
        return Route(
            providerId = chosen.provider.id,
            model = chosen.model,
            reason = reasonFor(chosen, kind, wanted),
        )
    }

    /**
     * The next candidate after a model failed. [failedKeys] holds
     * `Route.key` values, so a second model on the same provider is still
     * tried before the router moves to another provider.
     */
    fun next(task: String, candidates: List<Candidate>, failedKeys: Set<String>): Route? {
        val kind = classify(task)
        val wanted = wantedTier(kind)
        return rank(wanted, reachable(candidates))
            .firstOrNull { candidateKey(it) !in failedKeys }
            ?.let {
                Route(
                    providerId = it.provider.id,
                    model = it.model,
                    reason = "fallback after ${failedKeys.joinToString()}",
                )
            }
    }

    private fun candidateKey(candidate: Candidate): String =
        "${candidate.provider.id}/${candidate.model.id}"

    /** The tier a task wants when the user has no preference. */
    fun tierFor(kind: TaskKind): Tier = when (kind) {
        TaskKind.EXPLAIN, TaskKind.EDIT -> Tier.NEUTRON_STAR
        TaskKind.REVIEW, TaskKind.REFACTOR -> Tier.WHITE_DWARF
        TaskKind.SCAFFOLD -> Tier.RED_DWARF
    }

    private fun wantedTier(kind: TaskKind): Tier {
        val current = plan()
        preferredTier()?.takeIf { current.allows(it) }?.let { return it }
        val natural = tierFor(kind)
        return if (current.allows(natural)) natural else current.bestTier
    }

    private fun reachable(candidates: List<Candidate>): List<Candidate> {
        val current = plan()
        return candidates.filter { current.allows(it.model.tier) }
    }

    private fun rank(wanted: Tier, candidates: List<Candidate>): List<Candidate> {
        val tierOrder = orderFrom(wanted)

        return candidates.sortedWith(
            compareBy<Candidate> { tierOrder.indexOf(it.model.tier) }
                .thenBy { ModelCatalog.rankOf(it.model.id, it.model.providerId) }
                .thenBy { it.model.id },
        )
    }

    /**
     * The wanted tier first, then the stronger ones (best first), then the
     * weaker ones.
     */
    private fun orderFrom(wanted: Tier): List<Tier> {
        val stronger = Tier.entries.filter { it.rank < wanted.rank }.sortedBy { it.rank }
        val weaker = Tier.entries.filter { it.rank > wanted.rank }.sortedBy { it.rank }
        return listOf(wanted) + stronger + weaker
    }

    private fun reasonFor(chosen: Candidate, kind: TaskKind, wanted: Tier): String = when {
        chosen.model.tier != wanted -> "nearest available tier for a ${kind.name.lowercase()} task"
        preferredTier() != null -> "your chosen tier"
        else -> "best fit for a ${kind.name.lowercase()} task"
    }
}
