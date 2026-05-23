package com.gpsplotting.core

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Tape-measure style inches: whole inches plus fractions (1/16 precision).
 * Examples: `5 1/2`, `5-1/2`, `1/2`, `12`, or decimal `12.5`.
 */
object InchFraction {

    private const val DENOM = 16

    fun format(inches: Double): String {
        if (inches.isNaN() || inches.isInfinite()) return ""
        val sign = if (inches < 0) "-" else ""
        val sixteenths = (abs(inches) * DENOM).roundToLong()
        if (sixteenths == 0L) return "0"
        val whole = (sixteenths / DENOM).toInt()
        val rem = (sixteenths % DENOM).toInt()
        val parts = mutableListOf<String>()
        if (whole > 0) parts.add(whole.toString())
        if (rem != 0) parts.add(reducedFraction(rem, DENOM))
        return sign + parts.joinToString(" ")
    }

    fun parse(s: String): Double? {
        val t = s.trim()
        if (t.isEmpty()) return null
        val sign = if (t.startsWith("-")) -1.0 else 1.0
        val body = t.removePrefix("-").trim()
        if (body.isEmpty()) return null
        val tokens = body.split(Regex("""[\s]+""")).filter { it.isNotEmpty() }
        if (tokens.size > 1 && !tokens.last().contains('/')) {
            return null
        }
        var total = 0.0
        for (token in tokens) {
            val part = parseToken(token) ?: return null
            total += part
        }
        return sign * total
    }

    private fun parseToken(token: String): Double? {
        val slash = token.indexOf('/')
        if (slash < 0) return token.toDoubleOrNull()
        val num = token.substring(0, slash).toDoubleOrNull() ?: return null
        val den = token.substring(slash + 1).toDoubleOrNull() ?: return null
        if (den == 0.0) return null
        return num / den
    }

    /** Also accepts `5-1/2` and decimal inches like `12.5`. */
    fun parseLenient(s: String): Double? {
        val t = s.trim()
        if (t.isEmpty()) return null
        val hyphenFixed = t.replace(Regex("""(\d)-(\d+/\d+)"""), "$1 $2")
        if (t.contains('/') || t.contains(' ') || hyphenFixed != t) {
            return parse(hyphenFixed)
        }
        return parseDoubleLenient(t)
    }

    private fun reducedFraction(num: Int, den: Int): String {
        val g = gcd(abs(num), gcd(abs(den), DENOM))
        return "${num / g}/${den / g}"
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = y
            y = x % y
            x = t
        }
        return x
    }
}
