package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GradeTripleSyncTest {

    @Test
    fun `does not overwrite field being edited when two others are known`() {
        val (rise, _, _) = GradeTripleSync.sync("", "100", "10", GradeTripleSync.Field.Rise)
        assertEquals("", rise)
    }

    @Test
    fun `fills grade when rise and run entered`() {
        val (_, _, grade) = GradeTripleSync.sync("10", "100", "", GradeTripleSync.Field.Run)
        assertEquals("10", grade)
    }

    @Test
    fun `grade first then run calculates rise`() {
        var anchor = GradeTripleSync.Anchor.Auto
        anchor = GradeTripleSync.resolveAnchor(anchor, GradeTripleSync.Field.Grade, "", "", "10")
        val (rise, _, _) = GradeTripleSync.sync("", "100", "10", GradeTripleSync.Field.Run, anchor)
        assertEquals("10", rise)
    }

    @Test
    fun `grade first then rise calculates run`() {
        var anchor = GradeTripleSync.Anchor.Auto
        anchor = GradeTripleSync.resolveAnchor(anchor, GradeTripleSync.Field.Grade, "", "", "10")
        val (_, run, _) = GradeTripleSync.sync("10", "", "10", GradeTripleSync.Field.Rise, anchor)
        assertEquals("100", run)
    }

    @Test
    fun `recalculates grade when run edited after rise run first`() {
        var anchor = GradeTripleSync.resolveAnchor(
            GradeTripleSync.Anchor.Auto,
            GradeTripleSync.Field.Run,
            "10",
            "100",
            "",
        )
        val (_, _, grade) = GradeTripleSync.sync("10", "200", "10", GradeTripleSync.Field.Run, anchor)
        assertEquals("5", grade)
    }

    @Test
    fun `recalculates rise when run edited after grade first`() {
        var anchor = GradeTripleSync.Anchor.GradeFirst
        anchor = GradeTripleSync.resolveAnchor(anchor, GradeTripleSync.Field.Grade, "", "", "10")
        val (rise, _, _) = GradeTripleSync.sync("20", "200", "10", GradeTripleSync.Field.Run, anchor)
        assertEquals("20", rise)
    }

    @Test
    fun `recalculates run when grade edited with rise set`() {
        val (_, run, _) = GradeTripleSync.sync("10", "100", "5", GradeTripleSync.Field.Grade, GradeTripleSync.Anchor.GradeFirst)
        assertEquals("200", run)
    }

    @Test
    fun `locked run recalculates only rise when grade changes`() {
        val locked = setOf(GradeTripleSync.Field.Run)
        val (rise, run, grade) = GradeTripleSync.syncWithLocks(
            "5",
            "10",
            "5",
            GradeTripleSync.Field.Grade,
            locked,
            GradeTripleSync.Anchor.GradeFirst,
        )
        assertEquals("10", run)
        assertEquals("5", grade)
        assertEquals("0.5", rise)
    }

    @Test
    fun `locked run and rise does not overwrite grade being edited`() {
        val locked = setOf(GradeTripleSync.Field.Run, GradeTripleSync.Field.Rise)
        val (rise, run, grade) = GradeTripleSync.syncWithLocks(
            "10",
            "100",
            "6",
            GradeTripleSync.Field.Grade,
            locked,
        )
        assertEquals("100", run)
        assertEquals("10", rise)
        assertEquals("6", grade)
    }
}
