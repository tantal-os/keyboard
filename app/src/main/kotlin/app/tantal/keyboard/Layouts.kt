package app.tantal.keyboard

/** Takes room in a row but is not a key: the inset of a short row. */
const val SPACER = 0
const val SHIFT = -1
const val LANGUAGE = -4
const val SYMBOLS = -2
const val LETTERS = -3
const val DELETE = -5
const val EMOJI = -6
const val MORE_SYMBOLS = -7
const val ENTER = '\n'.code
const val SPACE = ' '.code

/** [width] is in key units; 0 makes the key take what the row leaves. */
class Key(
    val code: Int,
    val label: String = String(Character.toChars(code)),
    val width: Float = 1f,
    val more: String = "",
    val hint: String = "",
)

enum class Mode { Letters, Symbols, MoreSymbols, Numbers, Phone, Emoji }

enum class Field { Text, Email, Uri }

/** [side] is the width of shift and backspace, in letter widths of their row. */
class Language(
    val tag: String,
    val name: String,
    val space: String,
    val rows: List<String>,
    val side: Float,
    val more: Map<Char, String>,
)

val languages = listOf(
    Language(
        "en", "English (US)", "English", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), 1.5f,
        mapOf(
            'e' to "èéêëēę", 'y' to "ý", 'u' to "ùúûüū", 'i' to "ìíîïī", 'o' to "òóôöõøœō",
            'a' to "àáâäãåāæ", 's' to "ßśš", 'l' to "ł", 'z' to "žźż", 'c' to "çćč", 'n' to "ñń",
        ),
    ),
    Language(
        "ru", "Русский", "Русский", listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю"), 1.3f,
        mapOf('е' to "ё", 'ь' to "ъ"),
    ),
)

// Shift turns the number row into the symbols above the digits on a US keyboard.
private const val DIGITS = "1234567890"
private const val SHIFTED_DIGITS = "!@#$%^&*()"

fun shifted(label: String): String {
    if (label.codePointCount(0, label.length) != 1) return label
    val digit = DIGITS.indexOf(label[0])
    return if (digit >= 0) SHIFTED_DIGITS[digit].toString() else label.uppercase()
}

private const val PERIOD_MORE = "?!,;:'\"-()/@#&%"
private val symbolMore = mapOf(
    '1' to "¹½⅓¼⅛", '2' to "²⅔", '3' to "³¾⅜", '4' to "⁴", '5' to "⅝", '7' to "⅞", '0' to "ⁿ∅",
    '$' to "₽€£¥¢₹₩", '#' to "№", '-' to "–—·", '+' to "±", '(' to "<{[", ')' to ">}]",
    '*' to "†‡★", '"' to "“”„«»", '\'' to "‘’‚‹›", '!' to "¡", '?' to "¿", '%' to "‰", '=' to "≠≈∞",
)

private fun chars(row: String, more: Map<Char, String>, hints: Boolean = false) = row.mapIndexed { i, c ->
    val hint = if (hints && i < 10) "${(i + 1) % 10}" else ""
    Key(c.code, more = hint + more[c].orEmpty(), hint = hint)
}

/** Every row fills the width; a row shorter than the one above is inset with spacers. */
fun layout(mode: Mode, language: Language, field: Field, numberRow: Boolean, languageKey: Boolean = false): List<List<Key>> {
    val letters = mode == Mode.Letters
    val comma = when (field) {
        Field.Email -> Key('@'.code)
        Field.Uri -> Key('/'.code)
        Field.Text -> Key(','.code)
    }
    val bottom = listOf(
        Key(if (letters) SYMBOLS else LETTERS, if (letters) "?123" else "ABC", 1.5f),
        comma,
        Key(if (languageKey) LANGUAGE else EMOJI, ""),
        Key(SPACE, language.space, 0f),
        Key('.'.code, more = PERIOD_MORE),
        Key(ENTER, "", 1.5f),
    )
    return when (mode) {
        Mode.Letters -> listOfNotNull(
            if (numberRow) chars("1234567890", symbolMore) else null,
            chars(language.rows[0], language.more, hints = !numberRow),
            inset(chars(language.rows[1], language.more), language.rows[0].length),
            listOf(Key(SHIFT, "", language.side)) + chars(language.rows[2], language.more) + Key(DELETE, "", language.side),
            bottom,
        )
        Mode.Symbols -> listOf(
            chars("1234567890", symbolMore),
            chars("@#\$_&-+()/", symbolMore),
            listOf(Key(MORE_SYMBOLS, "=\\<", 1.5f)) + chars("*\"':;!?", symbolMore) + Key(DELETE, "", 1.5f),
            bottom,
        )
        Mode.MoreSymbols -> listOf(
            chars("~`|•√π÷×¶∆", symbolMore),
            chars("£¢€¥^°={}\\", symbolMore),
            listOf(Key(SYMBOLS, "?123", 1.5f)) + chars("%©®™✓[]", symbolMore) + Key(DELETE, "", 1.5f),
            bottom,
        )
        Mode.Numbers, Mode.Phone -> listOf(
            chars("123", emptyMap()) + if (mode == Mode.Phone) Key('+'.code, more = "-()") else Key('-'.code),
            chars("456", emptyMap()) + Key(SPACE, "␣"),
            chars("789", emptyMap()) + Key(DELETE, ""),
            listOf(
                if (mode == Mode.Phone) Key('*'.code, more = "#,;") else Key(','.code),
                Key('0'.code, more = if (mode == Mode.Phone) "+" else ""),
                Key('.'.code, more = if (mode == Mode.Phone) "" else ","),
                Key(ENTER, ""),
            ),
        )
        Mode.Emoji -> emptyList()
    }.map { row ->
        val fixed = row.sumOf { it.width.toDouble() }.toFloat()
        row.map { if (it.width == 0f) Key(it.code, it.label, 10 - fixed, it.more, it.hint) else it }
    }
}

private fun inset(row: List<Key>, units: Int): List<Key> {
    if (row.size >= units) return row
    val spacer = Key(SPACER, "", (units - row.size) / 2f)
    return listOf(spacer) + row + spacer
}

/** A key on screen: [x]..[y] is what is drawn, the hit box reaches the middle of the gaps. */
class Placed(
    val key: Key,
    val row: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val hitLeft: Float,
    val hitTop: Float,
    val hitRight: Float,
    val hitBottom: Float,
    /** Width of one key unit in this row, gap included. */
    val unit: Float,
) {
    val centerX get() = x + width / 2
    val centerY get() = y + height / 2
}

// Proportions measured on Gboard on a Pixel: the gap between keys is a share of the key unit,
// the number row is a little shorter than the letter rows and the bottom row a little taller.
const val GAP_SHARE = 0.13f
const val NUMBER_ROW_SCALE = 0.94f
const val BOTTOM_ROW_SCALE = 1.06f

fun rowScales(rows: Int, numberRow: Boolean, pad: Boolean) = List(rows) {
    when {
        it == 0 && numberRow -> NUMBER_ROW_SCALE
        it == rows - 1 && !pad -> BOTTOM_ROW_SCALE
        else -> 1f
    }
}

fun place(rows: List<List<Key>>, width: Float, keyHeight: Float, rowGap: Float, scales: List<Float>): List<Placed> {
    val out = ArrayList<Placed>()
    var y = 0f
    rows.forEachIndexed { r, row ->
        val unit = width / (row.sumOf { it.width.toDouble() }.toFloat() - GAP_SHARE)
        val gap = unit * GAP_SHARE
        val height = keyHeight * scales[r]
        val first = row.indexOfFirst { it.code != SPACER }
        val last = row.indexOfLast { it.code != SPACER }
        var x = 0f
        row.forEachIndexed { i, key ->
            val w = key.width * unit - gap
            if (key.code != SPACER) {
                out += Placed(
                    key, r, x, y, w, height,
                    hitLeft = if (i == first) 0f else x - gap / 2,
                    hitTop = if (r == 0) Float.NEGATIVE_INFINITY else y - rowGap / 2,
                    hitRight = if (i == last) width else x + w + gap / 2,
                    hitBottom = if (r == rows.lastIndex) Float.POSITIVE_INFINITY else y + height + rowGap / 2,
                    unit = unit,
                )
            }
            x += key.width * unit
        }
        y += height + rowGap
    }
    return out
}

fun List<Placed>.at(x: Float, y: Float) =
    firstOrNull { x >= it.hitLeft && x < it.hitRight && y >= it.hitTop && y < it.hitBottom }
        ?: minByOrNull { (it.centerX - x) * (it.centerX - x) + (it.centerY - y) * (it.centerY - y) }
