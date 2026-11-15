package app.tantal.keyboard

import android.content.Context
import com.android.inputmethod.keyboard.ProximityInfo
import com.android.inputmethod.latin.BinaryDictionary
import com.android.inputmethod.latin.DicTraverseSession
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils
import java.util.Locale

private const val MAX_WORD = 48
private const val MAX_RESULTS = 18
private const val MAX_PROXIMITY = 16
private const val GRID_WIDTH = 32
private const val GRID_HEIGHT = 16
private const val KIND_MASK = 0xFF
private const val KIND_WHITELIST = 3
private const val KIND_SHORTCUT = 7
private const val FLAG_AUTOCORRECT = 0x10000000

// LatinIME's "modest" auto-correction threshold.
private const val AUTOCORRECT_THRESHOLD = 0.185f

class Suggestions(val words: List<String>, val autocorrect: Boolean, val typedIsWord: Boolean) {
    companion object {
        val None = Suggestions(emptyList(), autocorrect = false, typedIsWord = false)
    }
}

/** A typed letter with the touch point the engine weighs against the key positions. */
class Tap(val code: Int, val x: Int, val y: Int)

/** One main dictionary from LatinIME, mapped straight from the APK. */
class Dictionary(context: Context, locale: Locale) : AutoCloseable {
    private var dict: Long
    private var session: Long
    private val options = IntArray(5).also { it[4] = 1000 }
    private val codes = IntArray(MAX_WORD)
    private val xs = IntArray(MAX_WORD)
    private val ys = IntArray(MAX_WORD)
    private val times = IntArray(MAX_WORD)
    private val pointers = IntArray(MAX_WORD)
    private val count = IntArray(1)
    private val outCodes = IntArray(MAX_WORD * MAX_RESULTS)
    private val outScores = IntArray(MAX_RESULTS)
    private val outSpaces = IntArray(MAX_RESULTS)
    private val outKinds = IntArray(MAX_RESULTS)
    private val outConfidence = IntArray(1)
    private val weight = FloatArray(1)

    init {
        context.assets.openFd("main_${locale.language}.dict").use { fd ->
            dict = BinaryDictionary.openNative(context.applicationInfo.sourceDir, fd.startOffset, fd.length, false)
            session = DicTraverseSession.setDicTraverseSessionNative(locale.toString(), fd.length)
        }
        DicTraverseSession.initDicTraverseSessionNative(session, dict, null, 0)
    }

    fun isWord(word: String): Boolean =
        word.isNotEmpty() && (probability(word) >= 0 || probability(word.lowercase()) >= 0)

    private fun probability(word: String) = BinaryDictionary.getProbabilityNative(dict, word.codePoints().toArray())

    /**
     * Candidates for [taps] after [previous], or next-word predictions when nothing is typed.
     * [previous] is null at the start of a sentence.
     */
    fun suggest(taps: List<Tap>, previous: String?, proximity: Long, limit: Int): Suggestions {
        if (dict == 0L || proximity == 0L || taps.size >= MAX_WORD) return Suggestions.None
        val size = taps.size
        codes.fill(-1)
        taps.forEachIndexed { i, tap ->
            codes[i] = tap.code
            xs[i] = tap.x
            ys[i] = tap.y
            times[i] = 0
            pointers[i] = 0
        }
        val prev = arrayOf(previous?.codePoints()?.toArray() ?: IntArray(0), null, null)
        val sentenceStart = booleanArrayOf(previous == null, false, false)
        weight[0] = -1f
        BinaryDictionary.getSuggestionsNative(
            dict, proximity, session, xs, ys, times, pointers, codes, size, options,
            prev, sentenceStart, 1, count, outCodes, outScores, outSpaces, outKinds, outConfidence, weight,
        )
        val typed = String(codes, 0, size)
        val shape = Shape.of(typed)
        val words = ArrayList<String>()
        var first = -1
        var firstWord = IntArray(0)
        for (i in 0 until count[0]) {
            val start = i * MAX_WORD
            var len = 0
            while (len < MAX_WORD && outCodes[start + len] != 0) len++
            if (len == 0) continue
            val word = shape.apply(String(outCodes, start, len))
            if (word == typed || word in words) continue
            if (first < 0) {
                first = i
                firstWord = outCodes.copyOfRange(start, start + len)
            }
            words += word
            if (words.size == limit) break
        }
        if (size == 0) return Suggestions(words, autocorrect = false, typedIsWord = false)
        val typedIsWord = isWord(typed)
        val kind = if (first >= 0) outKinds[first] else 0
        val autocorrect = first >= 0 && !typedIsWord && size > 1 &&
                kind and KIND_MASK != KIND_SHORTCUT && shape != Shape.Upper && taps.none { Character.isDigit(it.code) } &&
                (kind and KIND_MASK == KIND_WHITELIST || kind and FLAG_AUTOCORRECT != 0 &&
                        BinaryDictionaryUtils.calcNormalizedScoreNative(
                            codes.copyOf(size), firstWord, outScores[first],
                        ) >= AUTOCORRECT_THRESHOLD)
        return Suggestions(words, autocorrect, typedIsWord)
    }

    override fun close() {
        if (session != 0L) DicTraverseSession.releaseDicTraverseSessionNative(session)
        if (dict != 0L) BinaryDictionary.closeNative(dict)
        session = 0
        dict = 0
    }
}

/** Letter case of a typed word, carried over to the suggestions. */
enum class Shape {
    Lower, Capital, Upper;

    fun apply(word: String) = when (this) {
        Lower -> word
        Capital -> word.replaceFirstChar { it.titlecase() }
        Upper -> word.uppercase()
    }

    companion object {
        fun of(word: String): Shape {
            val letters = word.filter { it.isLetter() }
            return when {
                letters.length > 1 && letters.all { it.isUpperCase() } -> Upper
                letters.firstOrNull()?.isUpperCase() == true -> Capital
                else -> Lower
            }
        }
    }
}

class KeyBox(val code: Int, val x: Int, val y: Int, val width: Int, val height: Int) {
    fun distanceToEdge(px: Int, py: Int): Int {
        val dx = px - px.coerceIn(x, x + width)
        val dy = py - py.coerceIn(y, y + height)
        return dx * dx + dy * dy
    }
}

/** Builds the engine's view of the key positions; release the handle with [releaseProximity]. */
fun proximity(width: Int, height: Int, keyWidth: Int, keyHeight: Int, keys: List<KeyBox>): Long {
    val letters = keys.filter { it.code > ' '.code }
    if (width == 0 || height == 0 || letters.isEmpty()) return 0
    val chars = proximityChars(width, height, keyWidth, letters)
    return ProximityInfo.setProximityInfoNative(
        width, height, GRID_WIDTH, GRID_HEIGHT, keyWidth, keyHeight, chars, letters.size,
        IntArray(letters.size) { letters[it].x }, IntArray(letters.size) { letters[it].y },
        IntArray(letters.size) { letters[it].width }, IntArray(letters.size) { letters[it].height },
        IntArray(letters.size) { letters[it].code }, null, null, null,
    )
}

fun releaseProximity(handle: Long) {
    if (handle != 0L) ProximityInfo.releaseProximityInfoNative(handle)
}

fun proximityChars(width: Int, height: Int, keyWidth: Int, keys: List<KeyBox>): IntArray {
    val cellWidth = (width + GRID_WIDTH - 1) / GRID_WIDTH
    val cellHeight = (height + GRID_HEIGHT - 1) / GRID_HEIGHT
    val threshold = (keyWidth * 1.2f).toInt().let { it * it }
    val out = IntArray(GRID_WIDTH * GRID_HEIGHT * MAX_PROXIMITY) { -1 }
    for (cell in 0 until GRID_WIDTH * GRID_HEIGHT) {
        val cx = cell % GRID_WIDTH * cellWidth + cellWidth / 2
        val cy = cell / GRID_WIDTH * cellHeight + cellHeight / 2
        var n = 0
        for (key in keys) {
            if (n == MAX_PROXIMITY) break
            if (key.distanceToEdge(cx, cy) < threshold) out[cell * MAX_PROXIMITY + n++] = key.code
        }
    }
    return out
}
