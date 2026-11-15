package app.tantal.keyboard

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.CompositionLocalProvider
import app.tantal.theme.RobotoFlex
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val sideMargin = 4.5.dp
private val rowGap = 8.75.dp
// The system draws its own row (hide and switch buttons) over this margin; it reports only 24 dp of it.
private val bottomMargin = 43.5.dp
// Vertical sizes measured on Gboard on a Pixel 10 Pro, so both keyboards stand at the same height.
val stripHeight = 55.dp

/** Room above the keys when the strip is hidden, so the background does not end right at the top row. */
private val bareTop = 12.dp
private val overlayRoom = 56.dp

@Composable
private fun keyStyle(size: TextUnit, weight: FontWeight = FontWeight.Medium) =
    TextStyle(fontFamily = RobotoFlex, fontSize = size, fontWeight = weight)

@Composable
private fun letterStyle() = keyStyle(25.sp)
private const val LONG_PRESS_MS = 300L
private const val REPEAT_START_MS = 400L
private const val REPEAT_MS = 50L

@Composable
fun Keyboard(board: Board, ime: TantalIme) {
    val view = LocalView.current
    val haptic = {
        if (board.prefs.vibrate) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    // The transparent room on top lets previews and popups of the top rows rise above the keyboard;
    // the service keeps it out of the insets and the touchable region.
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(overlayRoom))
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { ime.keyboardTop = it.positionInWindow().y.roundToInt() }
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding()
                .padding(start = sideMargin, end = sideMargin, bottom = bottomMargin),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                when {
                    board.mode == Mode.Emoji -> EmojiPanel(board, ime, haptic)
                    board.panel == Panel.Voice -> Voice(board, ime, haptic)
                    board.panel == Panel.Clipboard -> ClipboardPanel(board, ime, haptic)
                    else -> {
                        val editing = board.editing
                        if (editing != null) {
                            EditStrip(editing, ime)
                        } else if (board.prefs.suggestions) {
                            Strip(board, ime, haptic)
                        } else {
                            Spacer(Modifier.height(bareTop))
                        }
                        Keys(board, ime, haptic)
                    }
                }
            }
        }
    }
}

@Composable
fun Tool(icon: Int, label: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = stringResource(label), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), stringResource(label), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Strip(board: Board, ime: TantalIme, haptic: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(stripHeight)) { Candidates(board, ime, haptic, MaterialTheme.colorScheme) }
}

@Composable
private fun Candidates(board: Board, ime: TantalIme, haptic: () -> Unit, colors: androidx.compose.material3.ColorScheme) {
    Row(Modifier.fillMaxSize().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val clip = board.clip
        val candidates = board.candidates
        if (clip != null && candidates.none { it?.typed == true }) {
            Row(
                Modifier
                    .padding(horizontal = 4.dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer)
                    .clickable {
                        haptic()
                        ime.pasteClip(clip)
                    }
                    .padding(start = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painterResource(if (clip.image != null) R.drawable.ic_image else R.drawable.ic_content_paste),
                    null,
                    Modifier.size(18.dp),
                    tint = colors.onPrimaryContainer,
                )
                Text(
                    when {
                        clip.hidden -> "••••••"
                        clip.image != null -> stringResource(R.string.image)
                        else -> clip.text.trim().replace('\n', ' ')
                    },
                    Modifier.weight(1f, fill = false),
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.weight(1f))
        } else {
            candidates.forEachIndexed { i, candidate ->
                if (i > 0) Box(Modifier.width(1.dp).height(20.dp).background(colors.outlineVariant))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (candidate == null) {
                                Modifier
                            } else {
                                Modifier.clickable {
                                    haptic()
                                    ime.pick(candidate)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (candidate != null) {
                        val quoted = candidate.typed && candidates.any { it?.bold == true }
                        Text(
                            if (quoted) "“${candidate.word}”" else candidate.word,
                            Modifier.padding(horizontal = 4.dp),
                            color = colors.onSurface,
                            fontWeight = if (candidate.bold) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            if (candidates.isEmpty()) Spacer(Modifier.weight(1f))
        }
    }
}

private class Press(var key: Placed, var x: Float, var y: Float) {
    val startX = x
    val startY = y
    var timer: Job? = null
    var popup: Popup? = null
    var handled = false
    var steps = 0
}

/** Choices of a long-pressed key, laid out in rows above it; the bottom row comes first. */
private class Popup(val items: List<String>, val perRow: Int, val left: Float, val bottom: Float, val cell: Float, val height: Float) {
    var selected by mutableIntStateOf(0)
    val rows get() = (items.size + perRow - 1) / perRow

    fun select(x: Float, y: Float) {
        val column = ((x - left) / cell).toInt().coerceIn(0, perRow - 1)
        val row = ((bottom - y) / height).toInt().coerceIn(0, rows - 1)
        selected = (row * perRow + column).coerceAtMost(items.lastIndex)
    }
}

private val functionKeys = setOf(SHIFT, DELETE, SYMBOLS, LETTERS, MORE_SYMBOLS)

@Composable
private fun Keys(board: Board, ime: TantalIme, haptic: () -> Unit) {
    val density = LocalDensity.current
    val numberRow = board.prefs.numberRow && board.mode == Mode.Letters
    val rows = remember(board.mode, board.language, board.field, numberRow, board.prefs.languageKey) {
        layout(board.mode, board.language, board.field, numberRow, board.prefs.languageKey)
    }
    val pressed = remember { mutableStateListOf<Placed>() }
    var preview by remember { mutableStateOf<Placed?>(null) }
    var popup by remember { mutableStateOf<Popup?>(null) }
    val upper = board.shift != Shift.Off && board.mode == Mode.Letters
    val currentUpper by rememberUpdatedState(upper)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth.toFloat()
        val pad = board.mode == Mode.Numbers || board.mode == Mode.Phone
        val scales = rowScales(rows.size, numberRow, pad)
        // Every layout takes the height of the letters, so switching to symbols or digits keeps the size.
        val (keyPx, rowGapPx) = with(density) {
            val gap = rowGap.toPx()
            (keysHeight(board.prefs).toPx() - gap * (rows.size - 1)) / scales.sum() to gap
        }
        val placed = remember(rows, width, keyPx) { place(rows, width, keyPx, rowGapPx, scales) }
        val height = placed.last().let { it.y + it.height }
        LaunchedEffect(placed) { ime.keysPlaced(placed, width.toInt(), height.toInt()) }

        fun label(key: Key) = if (currentUpper) shifted(key.label) else key.label

        fun emit(p: Press) {
            p.handled = true
            ime.onKey(p.key.key.code, p.x.roundToInt(), p.y.roundToInt())
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { height.toDp() })
                .pointerInput(placed) {
                    coroutineScope {
                        val presses = HashMap<PointerId, Press>()
                        fun show(p: Press) {
                            preview = p.key.takeIf { it.key.code > SPACE && it.key.code != ENTER }
                        }
                        fun startTimer(p: Press) {
                            p.timer?.cancel()
                            val key = p.key.key
                            p.timer = launch {
                                delay(if (key.code == DELETE) REPEAT_START_MS else LONG_PRESS_MS)
                                when {
                                    key.code == DELETE -> while (true) {
                                        ime.onKey(DELETE)
                                        delay(REPEAT_MS)
                                    }
                                    key.code == LANGUAGE || key.code == SPACE && p.steps == 0 -> {
                                        p.handled = true
                                        ime.pickInputMethod()
                                    }
                                    key.code == ','.code && board.prefs.languageKey -> {
                                        p.handled = true
                                        haptic()
                                        ime.onKey(EMOJI)
                                    }
                                    key.more.isNotEmpty() -> {
                                        val items = key.more.codePoints().toArray().map {
                                            val s = String(Character.toChars(it))
                                            if (currentUpper) s.uppercase() else s
                                        }
                                        val unit = p.key.unit
                                        val perRow = minOf(items.size, (width / unit).toInt().coerceAtMost(8))
                                        val left = (p.key.centerX - perRow * unit / 2).coerceIn(0f, width - perRow * unit)
                                        p.popup = Popup(items, perRow, left, p.key.y - unit * GAP_SHARE, unit, keyPx)
                                        popup = p.popup
                                        preview = null
                                        haptic()
                                    }
                                }
                            }
                        }
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    val pos = change.position
                                    if (change.changedToDown()) {
                                        change.consume()
                                        for (other in presses.values) {
                                            if (!other.handled && other.popup == null && other.key.key.code != SPACE &&
                                                other.key.key.code !in functionKeys
                                            ) {
                                                other.timer?.cancel()
                                                emit(other)
                                            }
                                        }
                                        val key = placed.at(pos.x, pos.y) ?: continue
                                        val p = Press(key, pos.x, pos.y)
                                        presses[change.id] = p
                                        pressed += key
                                        haptic()
                                        show(p)
                                        if (key.key.code == DELETE || key.key.code == SHIFT) emit(p)
                                        startTimer(p)
                                    } else if (change.changedToUp()) {
                                        val p = presses.remove(change.id) ?: continue
                                        change.consume()
                                        p.timer?.cancel()
                                        p.x = pos.x
                                        p.y = pos.y
                                        val choice = p.popup
                                        if (choice != null) {
                                            ime.onText(choice.items[choice.selected])
                                            popup = null
                                        } else if (!p.handled) {
                                            emit(p)
                                        }
                                        pressed -= p.key
                                        if (preview == p.key) preview = null
                                    } else if (change.pressed && change.positionChanged()) {
                                        val p = presses[change.id] ?: continue
                                        change.consume()
                                        p.x = pos.x
                                        p.y = pos.y
                                        val choice = p.popup
                                        when {
                                            choice != null -> choice.select(pos.x, pos.y)
                                            p.key.key.code == SPACE && p.steps == 0 && !p.handled &&
                                                p.startY - pos.y > p.key.height * 0.7f -> {
                                                p.timer?.cancel()
                                                p.handled = true
                                                haptic()
                                                ime.startVoice()
                                            }
                                            p.key.key.code == SPACE && p.steps == 0 && !p.handled &&
                                                pos.y - p.startY > p.key.height * 0.5f -> {
                                                p.timer?.cancel()
                                                p.handled = true
                                                haptic()
                                                ime.openClipboard()
                                            }
                                            p.key.key.code == SPACE -> {
                                                val steps = ((pos.x - p.startX) / (p.key.unit * 0.6f)).toInt()
                                                if (steps != p.steps) {
                                                    p.timer?.cancel()
                                                    p.handled = true
                                                    ime.moveCursor(steps - p.steps)
                                                    p.steps = steps
                                                }
                                            }
                                            !p.handled -> {
                                                val key = placed.at(pos.x, pos.y)
                                                if (key != null && key !== p.key) {
                                                    pressed -= p.key
                                                    pressed += key
                                                    p.key = key
                                                    show(p)
                                                    startTimer(p)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            for (k in placed) KeyView(k, board, label(k.key), k in pressed, density)
            preview?.let { Preview(it, label(it.key), density) }
            popup?.let { PopupView(it, density) }
        }
    }
}

@Composable
private fun KeyView(placed: Placed, board: Board, label: String, down: Boolean, density: androidx.compose.ui.unit.Density) {
    val colors = MaterialTheme.colorScheme
    val key = placed.key
    val (background, content) = when {
        key.code == ENTER -> colors.primary to colors.onPrimary
        key.code == SHIFT && board.shift == Shift.Locked -> colors.primaryContainer to colors.onPrimaryContainer
        key.code in functionKeys -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.surfaceContainerHighest to colors.onSurface
    }
    val shape: Shape = if (key.code == ENTER) CircleShape else RoundedCornerShape(10.dp)
    val icon = when (key.code) {
        SHIFT -> when (board.shift) {
            Shift.Off -> R.drawable.ic_shift
            Shift.On -> R.drawable.ic_shift_fill1
            Shift.Locked -> R.drawable.ic_shift_lock_fill1
        }
        DELETE -> R.drawable.ic_backspace
        EMOJI -> R.drawable.ic_mood
        LANGUAGE -> R.drawable.ic_language
        ENTER -> board.enter
        else -> 0
    }
    val description = when (key.code) {
        SHIFT -> stringResource(R.string.shift)
        DELETE -> stringResource(R.string.delete)
        EMOJI -> stringResource(R.string.emoji)
        LANGUAGE -> stringResource(R.string.language)
        ENTER -> stringResource(R.string.enter)
        SPACE -> stringResource(R.string.space)
        SYMBOLS -> stringResource(R.string.symbols)
        LETTERS -> stringResource(R.string.letters)
        else -> label
    }
    with(density) {
        Box(
            Modifier
                .offset { IntOffset(placed.x.roundToInt(), placed.y.roundToInt()) }
                .size(placed.width.toDp(), placed.height.toDp())
                .clip(shape)
                .background(if (down) content.copy(alpha = 0.16f).compositeOver(background) else background)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                icon != 0 -> Icon(
                    painterResource(icon),
                    null,
                    Modifier.size(if (key.code == SHIFT || key.code == DELETE) 30.dp else 24.dp),
                    tint = content,
                )
                key.code == SPACE -> {
                    Text(label, color = colors.onSurfaceVariant, style = keyStyle(13.sp, FontWeight.Normal))
                    // Swiping down from space opens the clipboard, swiping up starts voice input.
                    Row(
                        Modifier.align(Alignment.TopStart).padding(top = 5.dp, start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_downward), null, Modifier.size(11.dp), tint = colors.onSurfaceVariant)
                        Icon(painterResource(R.drawable.ic_content_paste), null, Modifier.size(13.dp), tint = colors.onSurfaceVariant)
                    }
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_upward), null, Modifier.size(11.dp), tint = colors.onSurfaceVariant)
                        Icon(painterResource(R.drawable.ic_mic), null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                    }
                }
                key.code in functionKeys || label.length > 1 -> Text(
                    label,
                    color = content,
                    style = keyStyle(15.sp),
                )
                else -> Text(label, color = content, style = letterStyle())
            }
            if (key.code == ','.code && board.prefs.languageKey) {
                Icon(
                    painterResource(R.drawable.ic_mood),
                    null,
                    Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).size(12.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
            if (key.hint.isNotEmpty()) {
                Text(
                    key.hint,
                    Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp),
                    color = colors.onSurfaceVariant,
                    style = keyStyle(10.sp, FontWeight.Normal),
                )
            }
        }
    }
}

@Composable
private fun Preview(placed: Placed, label: String, density: androidx.compose.ui.unit.Density) {
    with(density) {
        val width = placed.width + 8.dp.toPx()
        val height = placed.height * 1.1f
        val top = placed.y + 8.dp.toPx() - height
        Surface(
            Modifier
                .offset { IntOffset((placed.centerX - width / 2).roundToInt(), top.roundToInt()) }
                .size(width.toDp(), height.toDp()),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(label, style = letterStyle().copy(fontSize = 32.sp))
            }
        }
    }
}

@Composable
private fun PopupView(popup: Popup, density: androidx.compose.ui.unit.Density) {
    val colors = MaterialTheme.colorScheme
    with(density) {
        val top = popup.bottom - popup.rows * popup.height
        Surface(
            Modifier
                .offset { IntOffset(popup.left.roundToInt(), top.roundToInt()) }
                .size((popup.perRow * popup.cell).toDp(), (popup.rows * popup.height).toDp()),
            shape = RoundedCornerShape(14.dp),
            color = colors.surfaceContainerHighest,
            shadowElevation = 4.dp,
        ) {
            Column {
                for (row in popup.rows - 1 downTo 0) {
                    Row(Modifier.height(popup.height.toDp())) {
                        for (column in 0 until popup.perRow) {
                            val index = row * popup.perRow + column
                            if (index > popup.items.lastIndex) break
                            val on = index == popup.selected
                            Box(
                                Modifier
                                    .size(popup.cell.toDp(), popup.height.toDp())
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (on) colors.primary else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    popup.items[index],
                                    color = if (on) colors.onPrimary else colors.onSurface,
                                    style = letterStyle(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val emojiIcons = listOf(
    R.drawable.ic_schedule, R.drawable.ic_mood, R.drawable.ic_emoji_people, R.drawable.ic_emoji_nature,
    R.drawable.ic_emoji_food_beverage, R.drawable.ic_emoji_transportation, R.drawable.ic_emoji_events,
    R.drawable.ic_emoji_objects, R.drawable.ic_emoji_symbols, R.drawable.ic_emoji_flags,
)

private val emojiTitles = listOf(
    R.string.emoji_recent, R.string.emoji_smileys, R.string.emoji_people, R.string.emoji_nature,
    R.string.emoji_food, R.string.emoji_travel, R.string.emoji_activities, R.string.emoji_objects,
    R.string.emoji_symbols, R.string.emoji_flags,
)

/** Groups of emoji; each entry lists the emoji and then its skin tones. */
private var emojiGroups: List<List<List<String>>>? = null

private fun emoji(context: Context) = emojiGroups ?: context.assets.open("emoji.txt").bufferedReader().readLines()
    .map { line -> line.split(' ').map { it.split(',') } }
    .also { emojiGroups = it }

private const val RECENT_MAX = 32
private const val EMOJI_COLUMNS = 8

/** One scrolling list of every section, like Gboard; the tabs follow it and jump in it. */
@Composable
private fun EmojiPanel(board: Board, ime: TantalIme, haptic: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val store = remember { context.getSharedPreferences("emoji", Context.MODE_PRIVATE) }
    val groups = remember { emoji(context) }
    var recent by remember { mutableStateOf(store.getString("recent", "").orEmpty().split(' ').filter { it.isNotEmpty() }) }
    // Recent ones stay as they were while the panel is open, so the list does not jump under the finger.
    val shownRecent = remember { recent }
    val sections = remember { listOf(shownRecent.map { listOf(it) }) + groups }
    // Index of each section's header in the grid; an empty section has a header only.
    val starts = remember { sections.runningFold(0) { at, section -> at + 1 + section.size }.dropLast(1) }
    var tones by remember { mutableStateOf<List<String>?>(null) }
    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = if (shownRecent.isEmpty()) starts[1] else 0)
    val tab by remember { derivedStateOf { starts.indexOfLast { it <= grid.firstVisibleItemIndex }.coerceAtLeast(0) } }
    val scope = rememberCoroutineScope()

    fun type(emoji: String) {
        haptic()
        ime.onText(emoji)
        recent = (listOf(emoji) + (recent - emoji)).take(RECENT_MAX)
        store.edit().putString("recent", recent.joinToString(" ")).apply()
        tones = null
    }

    Column(Modifier.fillMaxWidth().height(panelHeight(board.prefs))) {
        Row(Modifier.fillMaxWidth().height(stripHeight), verticalAlignment = Alignment.CenterVertically) {
            val variants = tones
            if (variants != null) {
                for (v in variants) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).clickable { type(v) },
                        contentAlignment = Alignment.Center,
                    ) { Text(v, fontSize = 26.sp) }
                }
                Tool(R.drawable.ic_close, R.string.close) { tones = null }
            } else {
                Tool(R.drawable.ic_arrow_back, R.string.back) { ime.onKey(LETTERS) }
                emojiIcons.forEachIndexed { i, icon ->
                    val on = i == tab
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(if (on) colors.primary else Color.Transparent)
                            .clickable(onClickLabel = stringResource(emojiTitles[i])) {
                                scope.launch { grid.animateScrollToItem(starts[i]) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(icon),
                            stringResource(emojiTitles[i]),
                            Modifier.size(22.dp),
                            tint = if (on) colors.onPrimary else colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        LazyVerticalGrid(
            GridCells.Fixed(EMOJI_COLUMNS),
            Modifier.fillMaxWidth().weight(1f),
            state = grid,
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            sections.forEachIndexed { s, section ->
                item(key = "title$s", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(emojiTitles[s]),
                        Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                items(section, key = { "$s${it[0]}" }) { entry ->
                    Box(
                        Modifier
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onLongClick = if (entry.size > 1) ({ haptic(); tones = entry }) else null,
                                onClick = { type(entry[0]) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) { Text(entry[0], fontSize = 32.sp) }
                }
            }
        }
    }
}

/** The strip, hidden when suggestions are off, and the keys: what the emoji, voice and clipboard panels fill. */
fun panelHeight(prefs: Prefs) = (if (prefs.suggestions) stripHeight else bareTop) + keysHeight(prefs)

/** Height of the letter keys. */
fun keysHeight(prefs: Prefs): Dp {
    val key = prefs.keyHeight.dp
    return (if (prefs.numberRow) key * NUMBER_ROW_SCALE + rowGap else 0.dp) + (key + rowGap) * 3 + key * BOTTOM_ROW_SCALE
}
