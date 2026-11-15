package app.tantal.keyboard

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/** A copied text or image; [source] tells copies of the same clipboard image apart. */
data class Clip(
    val id: Long,
    val text: String,
    val hidden: Boolean,
    val image: File? = null,
    val mime: String = "",
    val pinned: Boolean = false,
    val source: String = "",
)

private const val HISTORY_MAX = 20
private const val IMAGE_MAX_BYTES = 20L shl 20

/**
 * Recent clips live in memory and the cache; pinned ones are kept in the app's own storage until
 * they are unpinned or deleted. Pinned clips come first.
 */
class ClipStore(context: Context) {
    val items = mutableStateListOf<Clip>()
    private val prefs = context.getSharedPreferences("clipboard", Context.MODE_PRIVATE)
    private val cache = File(context.cacheDir, "clips")
    private val kept = File(context.filesDir, "clips")
    private var nextId = 1L

    init {
        cache.deleteRecursively()
        cache.mkdirs()
        kept.mkdirs()
        val saved = runCatching { JSONArray(prefs.getString("pinned", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until saved.length()) {
            val o = saved.getJSONObject(i)
            val image = o.optString("image").takeIf { it.isNotEmpty() }?.let { File(kept, it) }
            if (image != null && !image.exists()) continue
            items += Clip(nextId++, o.optString("text"), o.optBoolean("hidden"), image, o.optString("mime"), pinned = true)
        }
        val used = items.mapNotNull { it.image?.name }.toSet()
        kept.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }

    fun bySource(source: String) = items.firstOrNull { it.source == source }

    fun addText(text: String, hidden: Boolean): Clip {
        items.firstOrNull { it.pinned && it.image == null && it.text == text }?.let { return it }
        items.removeAll { !it.pinned && it.image == null && it.text == text }
        return insert(Clip(nextId++, text, hidden))
    }

    /** Copies the image while the clipboard still grants access to it; null if it can't be read. */
    suspend fun addImage(resolver: ContentResolver, uri: Uri, mime: String, source: String, hidden: Boolean): Clip? {
        val file = File(cache, "${nextId}_${System.nanoTime()}")
        val copied = withContext(Dispatchers.IO) {
            runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { out ->
                        val buffer = ByteArray(64 shl 10)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > IMAGE_MAX_BYTES) error("too large")
                            out.write(buffer, 0, n)
                        }
                    }
                } != null
            }.getOrDefault(false)
        }
        if (!copied) {
            file.delete()
            return null
        }
        bySource(source)?.let {
            file.delete()
            return it
        }
        return insert(Clip(nextId++, "", hidden, file, mime, source = source))
    }

    private fun insert(clip: Clip): Clip {
        items.add(items.count { it.pinned }, clip)
        while (items.count { !it.pinned } > HISTORY_MAX) {
            val last = items.last { !it.pinned }
            items.remove(last)
            last.image?.delete()
        }
        return clip
    }

    fun pin(clip: Clip, on: Boolean) {
        val index = items.indexOfFirst { it.id == clip.id }.takeIf { it >= 0 } ?: return
        var image = clip.image
        if (image != null) {
            val target = File(if (on) kept else cache, image.name)
            if (image.renameTo(target)) image = target
        }
        items.removeAt(index)
        val moved = clip.copy(pinned = on, image = image)
        items.add(items.count { it.pinned }, moved)
        save()
    }

    fun delete(clip: Clip) {
        items.removeAll { it.id == clip.id }
        clip.image?.delete()
        if (clip.pinned) save()
    }

    fun update(clip: Clip, text: String) {
        val index = items.indexOfFirst { it.id == clip.id }.takeIf { it >= 0 } ?: return
        if (text.isBlank()) return delete(clip)
        items[index] = clip.copy(text = text)
        if (clip.pinned) save()
    }

    private fun save() {
        val array = JSONArray()
        for (clip in items.filter { it.pinned }) {
            array.put(
                JSONObject()
                    .put("text", clip.text)
                    .put("hidden", clip.hidden)
                    .put("image", clip.image?.name.orEmpty())
                    .put("mime", clip.mime),
            )
        }
        prefs.edit().putString("pinned", array.toString()).apply()
    }
}

/**
 * The field being edited inside the keyboard: while it is set, the keys type into it instead of the
 * app. Every change bumps [revision] so the view redraws.
 */
class ClipEditor(view: View, val clip: Clip) : BaseInputConnection(view, true) {
    private val text: Editable = SpannableStringBuilder(clip.text).also { Selection.setSelection(it, it.length) }
    var revision by mutableIntStateOf(0)
        private set

    val value get() = text.toString()
    val cursor get() = Selection.getSelectionEnd(text).coerceIn(0, text.length)

    override fun getEditable() = text

    private fun <T> changed(result: T): T {
        revision++
        return result
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int) = changed(super.commitText(text, newCursorPosition))
    override fun setComposingText(text: CharSequence?, newCursorPosition: Int) =
        changed(super.setComposingText(text, newCursorPosition))
    override fun finishComposingText() = changed(super.finishComposingText())
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) =
        changed(super.deleteSurroundingText(beforeLength, afterLength))
    override fun setSelection(start: Int, end: Int) = changed(super.setSelection(start, end))
    override fun performEditorAction(actionCode: Int) = true

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val start = Selection.getSelectionStart(text)
        val end = Selection.getSelectionEnd(text)
        val from = minOf(start, end)
        val to = maxOf(start, end)
        when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> when {
                from != to -> text.delete(from, to)
                from > 0 -> text.delete(Character.offsetByCodePoints(text, from, -1), from)
            }
            KeyEvent.KEYCODE_DPAD_LEFT ->
                Selection.setSelection(text, if (from > 0) Character.offsetByCodePoints(text, from, -1) else 0)
            KeyEvent.KEYCODE_DPAD_RIGHT ->
                Selection.setSelection(text, if (to < text.length) Character.offsetByCodePoints(text, to, 1) else to)
            KeyEvent.KEYCODE_ENTER -> text.replace(from, to, "\n")
        }
        return changed(true)
    }
}

/** The edited text with a caret, in place of the suggestion strip. */
@Composable
fun EditStrip(editor: ClipEditor, ime: TantalIme) {
    val colors = MaterialTheme.colorScheme
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val text = remember(editor.revision) { editor.value }
    val cursor = remember(editor.revision) { editor.cursor }
    Row(Modifier.fillMaxWidth().height(stripHeight * 2), verticalAlignment = Alignment.CenterVertically) {
        Tool(R.drawable.ic_close, R.string.cancel) { ime.finishEdit(save = false) }
        Box(
            Modifier
                .weight(1f)
                .height(stripHeight * 2 - 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceContainerHighest)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            BasicText(
                text,
                Modifier.fillMaxWidth().drawWithContent {
                    drawContent()
                    val rect = layout?.takeIf { cursor <= it.layoutInput.text.length }?.getCursorRect(cursor)
                    if (rect != null) {
                        drawLine(colors.primary, rect.topCenter, rect.bottomCenter, 2.dp.toPx())
                    }
                },
                style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                onTextLayout = { layout = it },
            )
        }
        Tool(R.drawable.ic_check, R.string.save) { ime.finishEdit(save = true) }
    }
}

@Composable
private fun Thumbnail(file: File, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(image, null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
    }
}

private class MenuEntry(val label: Int, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
fun ClipboardPanel(board: Board, ime: TantalIme, haptic: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val items = ime.clips.items
    var panelBounds by remember { mutableStateOf(Rect.Zero) }
    val bounds = remember { HashMap<Long, Rect>() }
    var menu by remember { mutableStateOf<Clip?>(null) }
    var notice by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2_500)
            notice = null
        }
    }

    Column(Modifier.fillMaxWidth().height(panelHeight(board.prefs))) {
        Row(Modifier.fillMaxWidth().height(stripHeight), verticalAlignment = Alignment.CenterVertically) {
            Tool(R.drawable.ic_arrow_back, R.string.back) { ime.showKeys() }
            Text(
                stringResource(R.string.clipboard),
                Modifier.padding(start = 8.dp),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f).onGloballyPositioned { panelBounds = it.boundsInWindow() }) {
            if (items.isEmpty()) {
                Text(
                    stringResource(R.string.clipboard_empty),
                    Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyVerticalGrid(
                GridCells.Fixed(2),
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items.toList(), key = { it.id }) { clip ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceContainerHighest)
                            .onGloballyPositioned { bounds[clip.id] = it.boundsInWindow() }
                            .combinedClickable(
                                onLongClick = {
                                    haptic()
                                    menu = clip
                                },
                                onClick = {
                                    haptic()
                                    if (!ime.pasteClip(clip, closePanel = true)) notice = R.string.image_not_accepted
                                },
                            ),
                    ) {
                        val image = clip.image
                        if (image != null) {
                            Thumbnail(image, Modifier.fillMaxWidth().height(96.dp))
                        } else {
                            Text(
                                if (clip.hidden) "••••••" else clip.text.trim(),
                                Modifier.padding(start = 12.dp, top = 12.dp, end = if (clip.pinned) 28.dp else 12.dp, bottom = 12.dp),
                                color = colors.onSurface,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (clip.pinned) {
                            Icon(
                                painterResource(R.drawable.ic_push_pin_fill1),
                                stringResource(R.string.pinned),
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(16.dp),
                                tint = if (image != null) colors.primary else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            notice?.let {
                Text(
                    stringResource(it),
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.inverseSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = colors.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val open = menu
            val visible = remember { MutableTransitionState(false) }
            visible.targetState = open != null
            if (open != null) {
                // A tap outside the menu closes it.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menu = null },
                )
                val item = bounds[open.id] ?: Rect.Zero
                val menuWidth = with(density) { 200.dp.toPx() }
                val menuHeight = with(density) { (8 * 2 + 48 * 3).dp.toPx() }
                val x = (item.center.x - panelBounds.left - menuWidth / 2).coerceIn(0f, (panelBounds.width - menuWidth).coerceAtLeast(0f))
                val below = item.bottom - panelBounds.top
                val y = if (below + menuHeight <= panelBounds.height) below else (item.top - panelBounds.top - menuHeight).coerceAtLeast(0f)
                val entries = listOf(
                    MenuEntry(if (open.pinned) R.string.unpin else R.string.pin) { ime.clips.pin(open, !open.pinned) },
                    MenuEntry(R.string.edit, enabled = open.image == null && !open.hidden) { ime.startEdit(open) },
                    MenuEntry(R.string.delete) { ime.clips.delete(open) },
                )
                ClipMenu(visible, Offset(x, y), entries) { menu = null }
            }
        }
    }
}

// The look of the overflow menus in our other apps, drawn inside the keyboard instead of a popup window.
@Composable
private fun ClipMenu(visible: MutableTransitionState<Boolean>, at: Offset, entries: List<MenuEntry>, onDismiss: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible,
        Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) },
        enter = scaleIn(motion.fastSpatialSpec(), 0.8f, TransformOrigin(0.5f, 0f)) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(motion.fastEffectsSpec(), 0.8f, TransformOrigin(0.5f, 0f)) + fadeOut(motion.fastEffectsSpec()),
    ) {
        Surface(
            Modifier.width(200.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                for (entry in entries) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable(enabled = entry.enabled) {
                                onDismiss()
                                entry.onClick()
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(entry.label),
                            color = if (entry.enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
