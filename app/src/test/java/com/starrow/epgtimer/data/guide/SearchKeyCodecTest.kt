package com.starrow.epgtimer.data.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchKeyCodecTest {

    @Test
    fun emptyKeyHasNoPrefix() {
        val parsed = parseAndKey("")
        assertFalse(parsed.disabled)
        assertFalse(parsed.caseSensitive)
        assertEquals(0, parsed.durationMin)
        assertEquals(0, parsed.durationMax)
        assertEquals("", parsed.plain)
        assertEquals("", buildAndKey("", false, false, 0, 0))
    }

    @Test
    fun plainKeyPassesThrough() {
        val parsed = parseAndKey("ニュース")
        assertEquals("ニュース", parsed.plain)
        assertEquals("ニュース", buildAndKey("ニュース", false, false, 0, 0))
    }

    @Test
    fun disabledPrefixIsParsedAndRebuilt() {
        val raw = "^!{999}ニュース"
        val parsed = parseAndKey(raw)
        assertTrue(parsed.disabled)
        assertFalse(parsed.caseSensitive)
        assertEquals("ニュース", parsed.plain)
        assertEquals(raw, buildAndKey("ニュース", true, false, 0, 0))
    }

    @Test
    fun caseSensitivePrefixIsParsedAndRebuilt() {
        val raw = "C!{999}News"
        val parsed = parseAndKey(raw)
        assertFalse(parsed.disabled)
        assertTrue(parsed.caseSensitive)
        assertEquals("News", parsed.plain)
        assertEquals(raw, buildAndKey("News", false, true, 0, 0))
    }

    @Test
    fun durationPrefixIsParsedAndRebuilt() {
        val raw = "D!{100300050}ドラマ"
        val parsed = parseAndKey(raw)
        assertEquals(30, parsed.durationMin)
        assertEquals(50, parsed.durationMax)
        assertEquals("ドラマ", parsed.plain)
        assertEquals(raw, buildAndKey("ドラマ", false, false, 30, 50))
    }

    @Test
    fun allThreePrefixesFollowTheServerOrder() {
        val raw = "^!{999}C!{999}D!{100600000}アニメ"
        val parsed = parseAndKey(raw)
        assertTrue(parsed.disabled)
        assertTrue(parsed.caseSensitive)
        assertEquals(60, parsed.durationMin)
        assertEquals(0, parsed.durationMax)
        assertEquals("アニメ", parsed.plain)
        assertEquals(raw, buildAndKey("アニメ", true, true, 60, 0))
        assertEquals(raw, buildAndKey(parsed.plain, parsed.disabled, parsed.caseSensitive, parsed.durationMin, parsed.durationMax))
    }

    @Test
    fun zeroDurationAddsNoPrefix() {
        assertEquals("plain", buildAndKey("plain", false, false, 0, 0))
    }

    @Test
    fun durationValuesAboveTheLimitAreClamped() {
        assertEquals("D!{199999999}", buildAndKey("", false, false, 10000, 10001))
        assertEquals("D!{100009999}", buildAndKey("", false, false, -5, 10000))
    }

    @Test
    fun durationMaximumBoundaryRoundTrips() {
        val built = buildAndKey("x", false, false, 9999, 9999)
        assertEquals("D!{199999999}x", built)
        val parsed = parseAndKey(built)
        assertEquals(9999, parsed.durationMin)
        assertEquals(9999, parsed.durationMax)
        assertEquals("x", parsed.plain)
    }

    @Test
    fun prefixOrderViolationKeepsTextPlain() {
        val parsed = parseAndKey("C!{999}^!{999}ニュース")
        assertFalse(parsed.disabled)
        assertEquals("^!{999}ニュース", parsed.plain)
    }

    @Test
    fun incompleteDurationPrefixIsNotStripped() {
        val parsed = parseAndKey("D!{123}ニュース")
        assertEquals(0, parsed.durationMin)
        assertEquals("D!{123}ニュース", parsed.plain)

        val shortDigits = parseAndKey("D!{10030}ニュース")
        assertEquals(0, shortDigits.durationMin)
        assertEquals("D!{10030}ニュース", shortDigits.plain)

        val notNumeric = parseAndKey("D!{abcdefgh}ニュース")
        assertEquals(0, notNumeric.durationMin)
        assertEquals("D!{abcdefgh}ニュース", notNumeric.plain)
    }

    @Test
    fun durationOnlyKeyHasEmptyPlain() {
        val parsed = parseAndKey("D!{100300050}")
        assertEquals(30, parsed.durationMin)
        assertEquals(50, parsed.durationMax)
        assertEquals("", parsed.plain)
    }

    @Test
    fun notKeyWithoutNoteIsUnchanged() {
        assertEquals(ParsedNotKey("", "単発"), parseNotKey("単発"))
        assertEquals("単発", buildNotKey("", "単発"))
    }

    @Test
    fun notKeyWithNoteIsParsedAndRebuilt() {
        val raw = ":note:この 単発"
        val parsed = parseNotKey(raw)
        assertEquals("この", parsed.note)
        assertEquals("単発", parsed.plain)
        assertEquals(raw, buildNotKey("この", "単発"))
    }

    @Test
    fun notKeyNoteEscapesAndRestoresEverySpecialCharacter() {
        val note = "a\\b c　d"
        val built = buildNotKey(note, "kw")
        assertEquals(":note:a\\\\b\\sc\\md kw", built)
        val parsed = parseNotKey(built)
        assertEquals(note, parsed.note)
        assertEquals("kw", parsed.plain)
    }

    @Test
    fun notKeyNoteWithoutPlainKeepsTrailingSpace() {
        val parsed = parseNotKey(":note:メモ ")
        assertEquals("メモ", parsed.note)
        assertEquals("", parsed.plain)
    }

    @Test
    fun notKeyWithoutSeparatorIsAllNote() {
        val parsed = parseNotKey(":note:メモ")
        assertEquals("メモ", parsed.note)
        assertEquals("", parsed.plain)
    }

    @Test
    fun notKeyNoteContainingEscapedSpaceIsRestored() {
        val parsed = parseNotKey(":note:こ\\sこ 単発")
        assertEquals("こ こ", parsed.note)
        assertEquals("単発", parsed.plain)
    }

    @Test
    fun notKeyEmptyInput() {
        assertEquals(ParsedNotKey("", ""), parseNotKey(""))
        assertEquals("", buildNotKey("", ""))
    }

    @Test
    fun roundTripOfArbitraryKeys() {
        data class Case(val plain: String, val disabled: Boolean, val caseSensitive: Boolean)

        val cases = listOf(
            Case("ニュース", false, false),
            Case("映画", true, false),
            Case("abc", false, true),
            Case("", true, true),
        )
        cases.forEach { case ->
            val built = buildAndKey(case.plain, case.disabled, case.caseSensitive, 0, 0)
            val parsed = parseAndKey(built)
            assertEquals(case.disabled, parsed.disabled)
            assertEquals(case.caseSensitive, parsed.caseSensitive)
            assertEquals(case.plain, parsed.plain)
        }
    }
}
