package com.voidapp.core.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffEngineTest {

    @Test
    fun `line diff marks additions and removals`() {
        val diff = DiffEngine.lineDiff("a\nb\nc", "a\nB\nc\nd")

        assertEquals(
            listOf(
                DiffLine(DiffType.UNCHANGED, "a"),
                DiffLine(DiffType.REMOVED, "b"),
                DiffLine(DiffType.ADDED, "B"),
                DiffLine(DiffType.UNCHANGED, "c"),
                DiffLine(DiffType.ADDED, "d"),
            ),
            diff,
        )
    }

    @Test
    fun `identical content is all unchanged`() {
        val diff = DiffEngine.lineDiff("a\nb", "a\nb")

        assertTrue(diff.all { it.type == DiffType.UNCHANGED })
    }

    @Test
    fun `a new file is all additions`() {
        val diff = DiffEngine.lineDiff("", "one\ntwo")

        assertEquals(listOf(DiffType.ADDED, DiffType.ADDED), diff.map { it.type })
    }

    @Test
    fun `unified diff contains a header`() {
        val diff = DiffEngine.unifiedDiff("a\nb", "a\nc")

        assertTrue(diff.contains("---"))
        assertTrue(diff.contains("+++"))
        assertTrue(diff.contains("@@"))
        assertTrue(diff.contains("-b"))
        assertTrue(diff.contains("+c"))
    }
}
