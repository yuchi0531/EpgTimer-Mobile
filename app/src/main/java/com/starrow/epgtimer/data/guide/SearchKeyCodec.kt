package com.starrow.epgtimer.data.guide

const val DISABLED_PREFIX = "^!{999}"
const val CASE_SENSITIVE_PREFIX = "C!{999}"
const val DURATION_PREFIX = "D!"
private const val DURATION_OPEN = "D!{1"
const val NOTE_PREFIX = ":note:"

private const val MAX_DURATION_MINUTES = 9999

data class ParsedAndKey(
    val disabled: Boolean,
    val caseSensitive: Boolean,
    val durationMin: Int,
    val durationMax: Int,
    val plain: String,
)

data class ParsedNotKey(
    val note: String,
    val plain: String,
)

private fun durationPrefix(durationMin: Int, durationMax: Int): String {
    val min = durationMin.coerceIn(0, MAX_DURATION_MINUTES)
    val max = durationMax.coerceIn(0, MAX_DURATION_MINUTES)
    if (min == 0 && max == 0) return ""
    return "$DURATION_PREFIX{${(10000 + min) * 10000 + max}}"
}

fun parseAndKey(raw: String): ParsedAndKey {
    var rest = raw
    var disabled = false
    var caseSensitive = false
    var durationMin = 0
    var durationMax = 0
    if (rest.startsWith(DISABLED_PREFIX)) {
        disabled = true
        rest = rest.substring(DISABLED_PREFIX.length)
    }
    if (rest.startsWith(CASE_SENSITIVE_PREFIX)) {
        caseSensitive = true
        rest = rest.substring(CASE_SENSITIVE_PREFIX.length)
    }
    if (rest.startsWith(DURATION_OPEN)) {
        val body = rest.substring(2)
        val valid = body.length >= 11 && body[0] == '{' && body[10] == '}' &&
            body.substring(1, 10).all { it.isDigit() }
        if (valid) {
            val value = body.substring(1, 10).toInt()
            durationMin = value / 10000 % 10000
            durationMax = value % 10000
            rest = body.substring(11)
        }
    }
    return ParsedAndKey(disabled, caseSensitive, durationMin, durationMax, rest)
}

fun buildAndKey(
    plain: String,
    disabled: Boolean,
    caseSensitive: Boolean,
    durationMin: Int,
    durationMax: Int,
): String = buildString {
    if (disabled) append(DISABLED_PREFIX)
    if (caseSensitive) append(CASE_SENSITIVE_PREFIX)
    append(durationPrefix(durationMin, durationMax))
    append(plain)
}

private fun escapeNote(note: String): String = buildString {
    for (c in note) {
        when (c) {
            '\\' -> append("\\\\")
            ' ' -> append("\\s")
            '　' -> append("\\m")
            else -> append(c)
        }
    }
}

private fun unescapeNote(note: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < note.length) {
        val c = note[i]
        if (c == '\\' && i + 1 < note.length) {
            when (note[i + 1]) {
                '\\' -> {
                    out.append('\\')
                    i += 2
                }
                's' -> {
                    out.append(' ')
                    i += 2
                }
                'm' -> {
                    out.append('　')
                    i += 2
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

private fun isNoteSeparator(c: Char): Boolean = c == ' ' || c == '　'

fun parseNotKey(raw: String): ParsedNotKey {
    if (!raw.startsWith(NOTE_PREFIX)) return ParsedNotKey("", raw)
    val body = raw.substring(NOTE_PREFIX.length)
    val separator = body.indexOfFirst { isNoteSeparator(it) }
    val note = if (separator < 0) body else body.substring(0, separator)
    val plain = if (separator < 0) "" else body.substring(separator + 1)
    return ParsedNotKey(unescapeNote(note), plain)
}

fun buildNotKey(note: String, plain: String): String =
    if (note.isEmpty()) plain else "$NOTE_PREFIX${escapeNote(note)} $plain"
