package com.gpsplotting.core

import kotlin.math.abs

/**
 * Keeps rise / run / grade in sync.
 * The field being edited is never overwritten.
 *
 * [Anchor] remembers whether the user is working grade-first (grade + rise or grade + run)
 * or rise/run-first (rise + run → grade).
 */
object GradeTripleSync {

    enum class Field { Rise, Run, Grade }

    enum class Anchor {
        /** Infer from which pair is complete on this keystroke. */
        Auto,
        /** Grade % is the value held fixed when rise or run changes. */
        GradeFirst,
        /** Rise and run define grade when either changes. */
        RiseRunFirst,
    }

    /**
     * When [locked] is non-empty, only the single unlocked field (not being held fixed) is recalculated.
     * Locked fields keep their string values exactly.
     */
    fun syncWithLocks(
        riseStr: String,
        runStr: String,
        gradeStr: String,
        editing: Field,
        locked: Set<Field>,
        anchor: Anchor = Anchor.Auto,
    ): Triple<String, String, String> {
        if (locked.isEmpty()) {
            return sync(riseStr, runStr, gradeStr, editing, anchor)
        }
        val r = parseDoubleLenient(riseStr)
        val ru = parseDoubleLenient(runStr)
        val g = parseDoubleLenient(gradeStr)
        val effective = if (anchor == Anchor.Auto) inferAnchor(editing, r, ru, g) else anchor
        val solveTarget = fieldToSolve(editing, locked, effective, r, ru, g)
            ?: return Triple(riseStr, runStr, gradeStr)
        val (sr, sru, sg) = when (solveTarget) {
            Field.Rise -> Triple(null, ru, g)
            Field.Run -> Triple(r, null, g)
            Field.Grade -> Triple(r, ru, null)
        }
        if (listOf(sr, sru, sg).count { it != null } != 2) {
            return Triple(riseStr, runStr, gradeStr)
        }
        return try {
            val s = ElevationGrade.solve(sr, sru, sg)
            Triple(
                if (Field.Rise in locked) riseStr else if (solveTarget == Field.Rise) fmt(s.riseFt) else riseStr,
                if (Field.Run in locked) runStr else if (solveTarget == Field.Run) fmt(s.runFt) else runStr,
                if (Field.Grade in locked) gradeStr else if (solveTarget == Field.Grade) fmt(s.gradePercent) else gradeStr,
            )
        } catch (_: Exception) {
            Triple(riseStr, runStr, gradeStr)
        }
    }

    fun sync(
        riseStr: String,
        runStr: String,
        gradeStr: String,
        editing: Field,
        anchor: Anchor = Anchor.Auto,
    ): Triple<String, String, String> {
        val r = parseDoubleLenient(riseStr)
        val ru = parseDoubleLenient(runStr)
        val g = parseDoubleLenient(gradeStr)
        val effective = if (anchor == Anchor.Auto) inferAnchor(editing, r, ru, g) else anchor
        return try {
            when (editing) {
                Field.Rise -> syncEditingRise(riseStr, runStr, gradeStr, r, ru, g, effective)
                Field.Run -> syncEditingRun(riseStr, runStr, gradeStr, r, ru, g, effective)
                Field.Grade -> syncEditingGrade(riseStr, runStr, gradeStr, r, ru, g, effective)
            }
        } catch (_: Exception) {
            Triple(riseStr, runStr, gradeStr)
        }
    }

    /** Call after each edit to remember grade-first vs rise/run-first workflow. */
    fun resolveAnchor(current: Anchor, editing: Field, riseStr: String, runStr: String, gradeStr: String): Anchor {
        val r = parseDoubleLenient(riseStr)
        val ru = parseDoubleLenient(runStr)
        val g = parseDoubleLenient(gradeStr)
        if (r == null && ru == null && g == null) return Anchor.Auto
        return when (editing) {
            Field.Grade -> if (g != null) Anchor.GradeFirst else current
            Field.Rise -> when {
                g != null && ru == null -> Anchor.GradeFirst
                g == null && r != null && ru != null -> Anchor.RiseRunFirst
                else -> current
            }
            Field.Run -> when {
                g != null && r == null -> Anchor.GradeFirst
                g == null && r != null && ru != null -> Anchor.RiseRunFirst
                else -> current
            }
        }
    }

    private fun inferAnchor(editing: Field, r: Double?, ru: Double?, g: Double?): Anchor =
        resolveAnchor(Anchor.Auto, editing, fmtOrEmpty(r), fmtOrEmpty(ru), fmtOrEmpty(g))

    private fun fmtOrEmpty(v: Double?): String = v?.let { fmt(it) }.orEmpty()

    private fun fieldToSolve(
        editing: Field,
        locked: Set<Field>,
        anchor: Anchor,
        r: Double?,
        ru: Double?,
        g: Double?,
    ): Field? {
        val unlocked = Field.entries.filter { it !in locked }
        if (unlocked.isEmpty()) return null
        if (unlocked.size == 1) {
            val only = unlocked.single()
            return if (only == editing) null else only
        }
        val notEditing = unlocked.filter { it != editing }
        if (notEditing.size == 1) return notEditing.single()
        if (notEditing.isEmpty()) return null
        return when (editing) {
            Field.Grade -> when (anchor) {
                Anchor.GradeFirst -> notEditing.firstOrNull { it == Field.Rise } ?: notEditing.first()
                Anchor.RiseRunFirst -> notEditing.firstOrNull { it == Field.Run } ?: notEditing.first()
                Anchor.Auto -> when {
                    ru != null && Field.Rise in notEditing -> Field.Rise
                    r != null && Field.Run in notEditing -> Field.Run
                    else -> notEditing.first()
                }
            }
            Field.Rise -> when (anchor) {
                Anchor.GradeFirst -> notEditing.firstOrNull { it == Field.Run } ?: notEditing.first()
                else -> notEditing.firstOrNull { it == Field.Grade } ?: notEditing.first()
            }
            Field.Run -> when (anchor) {
                Anchor.GradeFirst -> notEditing.firstOrNull { it == Field.Rise } ?: notEditing.first()
                else -> notEditing.firstOrNull { it == Field.Grade } ?: notEditing.first()
            }
        }
    }

    private fun syncEditingRise(
        riseStr: String,
        runStr: String,
        gradeStr: String,
        r: Double?,
        ru: Double?,
        g: Double?,
        anchor: Anchor,
    ): Triple<String, String, String> = when {
        anchor == Anchor.GradeFirst && r != null && g != null -> {
            val s = ElevationGrade.solve(r, null, g)
            Triple(riseStr, fmt(s.runFt), gradeStr)
        }
        r != null && ru != null && abs(ru) > 1e-12 && anchor == Anchor.RiseRunFirst -> {
            val s = ElevationGrade.solve(r, ru, null)
            Triple(riseStr, runStr, fmt(s.gradePercent))
        }
        else -> syncTwoOfThree(riseStr, runStr, gradeStr, Field.Rise)
    }

    private fun syncEditingRun(
        riseStr: String,
        runStr: String,
        gradeStr: String,
        r: Double?,
        ru: Double?,
        g: Double?,
        anchor: Anchor,
    ): Triple<String, String, String> = when {
        anchor == Anchor.GradeFirst && ru != null && g != null -> {
            val s = ElevationGrade.solve(null, ru, g)
            Triple(fmt(s.riseFt), runStr, gradeStr)
        }
        r != null && ru != null && abs(ru) > 1e-12 && anchor == Anchor.RiseRunFirst -> {
            val s = ElevationGrade.solve(r, ru, null)
            Triple(riseStr, runStr, fmt(s.gradePercent))
        }
        else -> syncTwoOfThree(riseStr, runStr, gradeStr, Field.Run)
    }

    private fun syncEditingGrade(
        riseStr: String,
        runStr: String,
        gradeStr: String,
        r: Double?,
        ru: Double?,
        g: Double?,
        anchor: Anchor,
    ): Triple<String, String, String> = when {
        anchor == Anchor.GradeFirst && r != null && g != null -> {
            val s = ElevationGrade.solve(r, null, g)
            Triple(riseStr, fmt(s.runFt), gradeStr)
        }
        anchor == Anchor.GradeFirst && ru != null && g != null -> {
            val s = ElevationGrade.solve(null, ru, g)
            Triple(fmt(s.riseFt), runStr, gradeStr)
        }
        r != null && ru != null && abs(ru) > 1e-12 && anchor == Anchor.RiseRunFirst -> {
            val s = ElevationGrade.solve(r, ru, null)
            Triple(riseStr, runStr, fmt(s.gradePercent))
        }
        else -> syncTwoOfThree(riseStr, runStr, gradeStr, Field.Grade)
    }

    private fun syncTwoOfThree(
        riseStr: String,
        runStr: String,
        gradeStr: String,
        editing: Field,
    ): Triple<String, String, String> {
        val r = parseDoubleLenient(riseStr)
        val ru = parseDoubleLenient(runStr)
        val g = parseDoubleLenient(gradeStr)
        if (listOf(r, ru, g).count { it != null } != 2) {
            return Triple(riseStr, runStr, gradeStr)
        }
        val s = ElevationGrade.solve(r, ru, g)
        return Triple(
            fieldValue(Field.Rise, editing, r, riseStr, fmt(s.riseFt)),
            fieldValue(Field.Run, editing, ru, runStr, fmt(s.runFt)),
            fieldValue(Field.Grade, editing, g, gradeStr, fmt(s.gradePercent)),
        )
    }

    private fun fieldValue(
        field: Field,
        editing: Field,
        parsed: Double?,
        current: String,
        computed: String,
    ): String = when {
        field == editing -> current
        parsed == null -> computed
        else -> current
    }

    private fun fmt(v: Double): String {
        val r = roundFeet3(v)
        return if (r == r.toLong().toDouble()) {
            r.toLong().toString()
        } else {
            "%.4f".format(r).trimEnd('0').trimEnd('.')
        }
    }
}
