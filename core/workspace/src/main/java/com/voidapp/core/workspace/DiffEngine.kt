package com.voidapp.core.workspace

enum class DiffType { ADDED, REMOVED, UNCHANGED }

data class DiffLine(
    val type: DiffType,
    val text: String,
)

/**
 * A small LCS-based line diff. Good enough for the workspace editor and small
 * enough to run on the phone without a native dependency.
 */
object DiffEngine {

    fun lineDiff(old: String, new: String): List<DiffLine> {
        val before = old.asLines()
        val after = new.asLines()

        val lcs = Array(before.size + 1) { IntArray(after.size + 1) }
        for (i in before.indices.reversed()) {
            for (j in after.indices.reversed()) {
                lcs[i][j] = if (before[i] == after[j]) {
                    lcs[i + 1][j + 1] + 1
                } else {
                    maxOf(lcs[i + 1][j], lcs[i][j + 1])
                }
            }
        }

        val result = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < before.size && j < after.size) {
            when {
                before[i] == after[j] -> {
                    result += DiffLine(DiffType.UNCHANGED, before[i])
                    i++
                    j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> {
                    result += DiffLine(DiffType.REMOVED, before[i])
                    i++
                }
                else -> {
                    result += DiffLine(DiffType.ADDED, after[j])
                    j++
                }
            }
        }
        while (i < before.size) {
            result += DiffLine(DiffType.REMOVED, before[i])
            i++
        }
        while (j < after.size) {
            result += DiffLine(DiffType.ADDED, after[j])
            j++
        }
        return result
    }

    /**
     * A single-hunk unified diff. One hunk covering the whole file is less
     * pretty than a multi-hunk diff and much easier to verify.
     */
    fun unifiedDiff(old: String, new: String, path: String = "file"): String {
        val lines = lineDiff(old, new)
        val oldCount = lines.count { it.type != DiffType.ADDED }
        val newCount = lines.count { it.type != DiffType.REMOVED }

        return buildString {
            appendLine("--- a/$path")
            appendLine("+++ b/$path")
            appendLine("@@ -1,$oldCount +1,$newCount @@")
            lines.forEach { line ->
                append(
                    when (line.type) {
                        DiffType.ADDED -> "+"
                        DiffType.REMOVED -> "-"
                        DiffType.UNCHANGED -> " "
                    },
                )
                appendLine(line.text)
            }
        }
    }

    private fun String.asLines(): List<String> = if (isEmpty()) emptyList() else lines()
}
