package app.tantal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private val en = languages.first { it.tag == "en" }
    private val ru = languages.first { it.tag == "ru" }

    @Test
    fun rowsFillTheWidth() {
        for (language in listOf(en, ru)) {
            val placed = place(layout(Mode.Letters, language, Field.Text, numberRow = true), 1056f, 100f, 20f, List(5) { 1f })
            for ((row, keys) in placed.groupBy { it.row }) {
                if (language == en && row == 2) continue
                assertEquals(0f, keys.first().x, 0.01f)
                assertEquals(1056f, keys.last().x + keys.last().width, 0.01f)
            }
        }
    }

    @Test
    fun russianKeysMatchGboard() {
        // Pixels measured on Gboard: 1056 px between the side margins.
        val placed = place(layout(Mode.Letters, ru, Field.Text, numberRow = true), 1056f, 126f, 23f, List(5) { 1f })
        fun width(c: Char) = placed.first { it.key.code == c.code }.width
        assertEquals(85f, width('й'), 1f)
        assertEquals(92f, width('1'), 1.5f)
        assertEquals(80f, width('я'), 1f)
        assertEquals(107f, placed.first { it.key.code == SHIFT }.width, 1f)
        assertEquals(414f, placed.first { it.key.code == SPACE }.width, 1f)
    }

    @Test
    fun everyPointHitsTheNearestKey() {
        val placed = place(layout(Mode.Letters, en, Field.Text, numberRow = false), 1000f, 100f, 20f, List(4) { 1f })
        val a = placed.first { it.key.code == 'a'.code }
        assertEquals('a'.code, placed.at(1f, a.centerY)!!.key.code)
        assertEquals('q'.code, placed.at(1f, -50f)!!.key.code)
        val w = placed.first { it.key.code == 'w'.code }
        assertEquals('w'.code, placed.at(w.x - 4f, w.centerY)!!.key.code)
        assertEquals('q'.code, placed.at(w.x - 8f, w.centerY)!!.key.code)
        assertEquals(ENTER, placed.at(999f, 10_000f)!!.key.code)
    }

    @Test
    fun numberRowIsShorter() {
        val rows = layout(Mode.Letters, ru, Field.Text, numberRow = true)
        val placed = place(rows, 1100f, 100f, 20f, rowScales(rows.size, numberRow = true, pad = false))
        assertEquals(94f, placed.first { it.key.code == '1'.code }.height, 0.01f)
        assertEquals(114f, placed.first { it.key.code == 'й'.code }.y, 0.01f)
        assertEquals(106f, placed.first { it.key.code == SPACE }.height, 0.01f)
    }

    @Test
    fun shiftTurnsDigitsIntoSymbols() {
        assertEquals("@", shifted("2"))
        assertEquals(")", shifted("0"))
        assertEquals("Й", shifted("й"))
        assertEquals("?123", shifted("?123"))
        assertEquals("Русский", shifted("Русский"))
    }

    @Test
    fun previousWordComesFromTheText() {
        assertNull(previousWord(""))
        assertNull(previousWord("Hi there. "))
        assertEquals("there", previousWord("Hi there "))
        assertEquals("don't", previousWord("I don't"))
        assertEquals("", previousWord("Hi, "))
    }

    @Test
    fun suggestionsKeepTheTypedCase() {
        assertEquals("Hello", Shape.of("Hel").apply("hello"))
        assertEquals("HELLO", Shape.of("HEL").apply("hello"))
        assertEquals("hello", Shape.of("hel").apply("hello"))
        assertEquals("Hello", Shape.of("H").apply("hello"))
    }

    @Test
    fun proximityListsNeighbours() {
        val keys = listOf(KeyBox('a'.code, 0, 0, 100, 100), KeyBox('b'.code, 100, 0, 100, 100), KeyBox('c'.code, 900, 0, 100, 100))
        val chars = proximityChars(1000, 100, 100, keys)
        val first = chars.copyOfRange(0, 16).filter { it > 0 }
        assertTrue('a'.code in first && 'b'.code in first && 'c'.code !in first)
    }
}
