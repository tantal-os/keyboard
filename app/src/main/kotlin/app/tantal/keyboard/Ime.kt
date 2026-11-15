package app.tantal.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.inputmethodservice.InputMethodService.Insets
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.tantal.theme.TantalTheme
import java.util.Locale

enum class Shift { Off, On, Locked }

/** What takes the place of the keys. */
enum class Panel { Keys, Voice, Clipboard }

/** A word in the suggestion strip; [typed] keeps what was typed as it is. */
class Candidate(val word: String, val bold: Boolean = false, val typed: Boolean = false)

/** What the keyboard view shows; the service changes it. */
class Board {
    var mode by mutableStateOf(Mode.Letters)
    var shift by mutableStateOf(Shift.Off)
    var language by mutableStateOf(languages[0])
    var field by mutableStateOf(Field.Text)
    var enter by mutableIntStateOf(R.drawable.ic_keyboard_return)
    var candidates by mutableStateOf<List<Candidate?>>(emptyList())
    var clip by mutableStateOf<Clip?>(null)
    var prefs by mutableStateOf(Prefs())
    var panel by mutableStateOf(Panel.Keys)
    var composing by mutableStateOf(false)
    var editing by mutableStateOf<ClipEditor?>(null)
}

private val sentenceEnd = setOf('.', '!', '?', '…')
private const val SWAPPED_PUNCTUATION = ".,!?;:)…"
private const val DOUBLE_SPACE_MS = 600L
private const val CLIP_MAX_AGE_MS = 120_000L

class TantalIme : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry get() = savedState.savedStateRegistry

    val board = Board()
    private val dictionaries = HashMap<String, Dictionary>()
    private var proximity = 0L
    private var proximityKey: Any? = null

    private val word = StringBuilder()
    private val taps = ArrayList<Tap>()
    private var previous: String? = null
    private var predict = false
    private var revert: Pair<String, String>? = null
    private var weakSpace = false
    private var lastSpace = 0L
    private var lastShift = 0L
    private var lastEdit = 0L
    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    private var hideClip = false
    val clips by lazy { ClipStore(this) }
    private var lastStamp = 0L
    private var lastClipId = 0L
    private var editor: ClipEditor? = null

    // While a clip is edited the keys type into it, and every edit goes through this.
    override fun getCurrentInputConnection(): InputConnection? = editor ?: super.getCurrentInputConnection()
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { readClip() }

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        dictionaries.values.forEach { it.close() }
        releaseProximity(proximity)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        window.window!!.decorView.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply { setContent { TantalTheme { Keyboard(board, this@TantalIme) } } }
    }

    override fun onEvaluateFullscreenMode() = false

    /** Top of the visible keyboard in the window; the room above it is for popups only. */
    var keyboardTop = 0

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val decor = window.window?.decorView ?: return
        outInsets.contentTopInsets = keyboardTop
        outInsets.visibleTopInsets = keyboardTop
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, keyboardTop, decor.width, decor.height)
    }

    override fun onCurrentInputMethodSubtypeChanged(subtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(subtype)
        currentInputConnection?.let { commitWord(it, autocorrect = false) }
        board.language = language(subtype)
        refresh()
    }

    private fun language(subtype: InputMethodSubtype?): Language {
        val tag = subtype?.languageTag?.substringBefore('-')
        return languages.firstOrNull { it.tag == tag } ?: languages.first()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val prefs = Prefs.read(this)
        board.prefs = prefs
        board.language = language(getSystemService(InputMethodManager::class.java).currentInputMethodSubtype)
        val type = info.inputType
        val variation = type and InputType.TYPE_MASK_VARIATION
        val password = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        board.field = when (variation) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> Field.Email
            InputType.TYPE_TEXT_VARIATION_URI -> Field.Uri
            else -> Field.Text
        }
        board.panel = Panel.Keys
        board.mode = when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> Mode.Numbers
            InputType.TYPE_CLASS_PHONE -> Mode.Phone
            else -> if (restarting && board.mode == Mode.Emoji) Mode.Emoji else Mode.Letters
        }
        predict = prefs.suggestions && type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                !password && board.field == Field.Text && type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0
        board.enter = enterIcon(info)
        board.shift = Shift.Off
        reset()
        hideClip = password
        clipboard.addPrimaryClipChangedListener(clipListener)
        readClip()
        updateShift()
        refresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        finishEdit(save = false)
        clipboard.removePrimaryClipChangedListener(clipListener)
        currentInputConnection?.finishComposingText()
        reset()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (word.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            currentInputConnection?.finishComposingText()
            reset()
            refresh()
        }
        if (word.isEmpty() && SystemClock.uptimeMillis() - lastEdit > 300) {
            revert = null
            updateShift()
        }
    }

    private fun reset() {
        word.clear()
        taps.clear()
        revert = null
        weakSpace = false
    }

    private fun dictionary(): Dictionary? = runCatching {
        dictionaries.getOrPut(board.language.tag) { Dictionary(this, Locale.forLanguageTag(board.language.tag)) }
    }.getOrNull()

    /** The view reports where the letter keys are, in the same pixels as the taps. */
    fun keysPlaced(placed: List<Placed>, width: Int, height: Int) {
        if (board.mode != Mode.Letters) return
        val key = listOf(board.language.tag, width, height, placed.size)
        if (key == proximityKey) return
        releaseProximity(proximity)
        val letter = placed.first { Character.isLetter(it.key.code) }
        proximity = proximity(
            width, height, letter.width.toInt(), letter.height.toInt(),
            placed.map { KeyBox(it.key.code, it.x.toInt(), it.y.toInt(), it.width.toInt(), it.height.toInt()) },
        )
        proximityKey = key
        refresh()
    }

    fun onKey(code: Int, x: Int = -1, y: Int = -1) {
        val ic = currentInputConnection ?: return
        lastEdit = SystemClock.uptimeMillis()
        if (code != DELETE) revert = null
        when (code) {
            SHIFT -> {
                val now = SystemClock.uptimeMillis()
                board.shift = when {
                    board.shift == Shift.Off -> Shift.On
                    board.shift == Shift.On && now - lastShift < 400 -> Shift.Locked
                    else -> Shift.Off
                }
                lastShift = now
            }

            SYMBOLS -> board.mode = Mode.Symbols
            MORE_SYMBOLS -> board.mode = Mode.MoreSymbols
            LETTERS -> {
                board.mode = Mode.Letters
                updateShift()
            }

            EMOJI -> {
                commitWord(ic, autocorrect = false)
                board.mode = Mode.Emoji
            }

            LANGUAGE -> {
                commitWord(ic, autocorrect = false)
                refresh()
                switchToNextInputMethod(true)
            }

            DELETE -> delete(ic)
            SPACE -> space(ic)
            ENTER -> enter(ic)
            else -> type(ic, code, x, y)
        }
    }

    private fun type(ic: android.view.inputmethod.InputConnection, code: Int, x: Int, y: Int) {
        val shifted = board.shift != Shift.Off && board.mode == Mode.Letters
        val c = if (shifted) shifted(String(Character.toChars(code))).codePointAt(0) else code
        val connector = (c == '\''.code || c == '-'.code) && word.isNotEmpty()
        if (predict && (Character.isLetterOrDigit(c) || connector) && board.mode != Mode.Emoji) {
            board.clip = null
            if (word.isEmpty()) previous = previousWord(ic)
            word.appendCodePoint(c)
            taps += Tap(c, if (Character.isLetter(c)) x else -1, if (Character.isLetter(c)) y else -1)
            ic.setComposingText(word, 1)
            weakSpace = false
        } else {
            commitWord(ic, autocorrect = !Character.isLetterOrDigit(c))
            if (weakSpace && c.toChar() in SWAPPED_PUNCTUATION && before(ic, 1) == " ") {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(String(Character.toChars(c)) + " ", 1)
            } else {
                ic.commitText(String(Character.toChars(c)), 1)
            }
            weakSpace = false
        }
        if (board.shift == Shift.On) board.shift = Shift.Off
        if (word.isEmpty()) updateShift()
        refresh()
    }

    /** Types a whole string as it is: emoji, or a key from a long-press popup that is not a letter. */
    fun onText(text: String) {
        val ic = currentInputConnection ?: return
        if (text.codePointCount(0, text.length) == 1 && board.mode != Mode.Emoji) {
            return onKey(text.codePointAt(0))
        }
        lastEdit = SystemClock.uptimeMillis()
        revert = null
        commitWord(ic, autocorrect = false)
        ic.commitText(text, 1)
        weakSpace = false
        refresh()
    }

    private fun space(ic: android.view.inputmethod.InputConnection) {
        val now = SystemClock.uptimeMillis()
        if (word.isNotEmpty()) {
            commitWord(ic, autocorrect = true, separator = " ")
        } else if (board.prefs.doubleSpace && weakSpace && now - lastSpace < DOUBLE_SPACE_MS &&
            before(ic, 2).let { it.length == 2 && it[0].isLetterOrDigit() && it[1] == ' ' }
        ) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            weakSpace = false
        } else {
            ic.commitText(" ", 1)
            weakSpace = before(ic, 2).firstOrNull()?.isLetterOrDigit() == true
        }
        lastSpace = now
        if (board.mode != Mode.Letters && board.mode != Mode.Numbers && board.mode != Mode.Phone) {
            board.mode = Mode.Letters
        }
        updateShift()
        refresh()
    }

    private fun enter(ic: android.view.inputmethod.InputConnection) {
        commitWord(ic, autocorrect = true)
        if (editor != null) {
            ic.commitText("\n", 1)
            refresh()
            return
        }
        val info = currentInputEditorInfo
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 ||
            action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            sendKeyChar('\n')
        } else {
            ic.performEditorAction(action)
        }
        weakSpace = false
        updateShift()
        refresh()
    }

    private fun delete(ic: android.view.inputmethod.InputConnection) {
        val undo = revert
        revert = null
        weakSpace = false
        when {
            undo != null && before(ic, undo.second.length) == undo.second -> {
                ic.deleteSurroundingText(undo.second.length, 0)
                ic.commitText(undo.first, 1)
            }

            word.isNotEmpty() -> {
                word.setLength(word.offsetByCodePoints(word.length, -1))
                taps.removeAt(taps.lastIndex)
                if (word.isEmpty()) ic.commitText("", 1) else ic.setComposingText(word, 1)
            }

            !ic.getSelectedText(0).isNullOrEmpty() -> ic.commitText("", 1)
            else -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        if (word.isEmpty()) updateShift()
        refresh()
    }

    fun moveCursor(steps: Int) {
        val ic = currentInputConnection ?: return
        lastEdit = SystemClock.uptimeMillis()
        commitWord(ic, autocorrect = false)
        refresh()
        val key = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(key) }
    }

    fun pick(candidate: Candidate) {
        val ic = currentInputConnection ?: return
        lastEdit = SystemClock.uptimeMillis()
        revert = null
        ic.commitText(candidate.word + " ", 1)
        word.clear()
        taps.clear()
        weakSpace = true
        updateShift()
        refresh()
    }


    fun startVoice() {
        currentInputConnection?.let { commitWord(it, autocorrect = false) }
        refresh()
        board.panel = Panel.Voice
    }

    fun openClipboard() {
        currentInputConnection?.let { commitWord(it, autocorrect = false) }
        refresh()
        readClip()
        board.panel = Panel.Clipboard
    }

    fun showKeys() {
        board.panel = Panel.Keys
    }

    /** False when the field takes no images of this type. */
    fun pasteClip(clip: Clip, closePanel: Boolean = false): Boolean {
        val ic = currentInputConnection ?: return true
        commitWord(ic, autocorrect = false)
        val image = clip.image
        if (image != null) {
            val accepted = EditorInfoCompat.getContentMimeTypes(currentInputEditorInfo)
                .any { ClipDescription.compareMimeTypes(clip.mime, it) }
            if (!accepted || editor != null) return false
            val uri = FileProvider.getUriForFile(this, "$packageName.clips", image)
            val info = InputContentInfo(uri, ClipDescription("image", arrayOf(clip.mime)))
            ic.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
        } else {
            ic.commitText(clip.text, 1)
        }
        board.clip = null
        if (closePanel) board.panel = Panel.Keys
        refresh()
        return true
    }

    fun startEdit(clip: Clip) {
        super.getCurrentInputConnection()?.let { commitWord(it, autocorrect = false) }
        reset()
        editor = ClipEditor(window.window!!.decorView, clip).also { board.editing = it }
        board.panel = Panel.Keys
        board.mode = Mode.Letters
        board.enter = R.drawable.ic_keyboard_return
        updateShift()
        refresh()
    }

    fun finishEdit(save: Boolean) {
        val edited = editor ?: return
        edited.finishComposingText()
        editor = null
        board.editing = null
        if (save) clips.update(edited.clip, edited.value)
        reset()
        currentInputEditorInfo?.let { board.enter = enterIcon(it) }
        board.panel = Panel.Clipboard
        updateShift()
        refresh()
    }

    fun pickInputMethod() = getSystemService(InputMethodManager::class.java).showInputMethodPicker()

    private fun commitWord(ic: android.view.inputmethod.InputConnection, autocorrect: Boolean, separator: String = "") {
        if (word.isEmpty()) {
            if (separator.isNotEmpty()) ic.commitText(separator, 1)
            return
        }
        val typed = word.toString()
        val best = board.candidates.getOrNull(1)
        val text = if (autocorrect && board.prefs.autocorrect && best?.bold == true) best.word else typed
        ic.commitText(text + separator, 1)
        revert = if (text != typed) (typed + separator) to (text + separator) else null
        weakSpace = separator == " "
        word.clear()
        taps.clear()
    }

    private fun refresh() {
        board.composing = word.isNotEmpty()
        if (!predict || board.mode == Mode.Emoji) {
            board.candidates = emptyList()
            return
        }
        val dict = dictionary()
        if (dict == null) {
            board.candidates = emptyList()
            return
        }
        if (word.isEmpty()) {
            val result = dict.suggest(emptyList(), previousWord(currentInputConnection), proximity, 3)
            val shape = if (board.shift != Shift.Off) Shape.Capital else Shape.Lower
            board.candidates = order(result.words.map { Candidate(shape.apply(it)) })
            return
        }
        val typed = word.toString()
        val result = dict.suggest(taps, previous, proximity, 3)
        val correct = result.autocorrect && board.prefs.autocorrect
        board.candidates = when {
            correct -> listOf(
                Candidate(typed, typed = true),
                Candidate(result.words[0], bold = true),
                result.words.getOrNull(1)?.let { Candidate(it) },
            )

            result.typedIsWord -> listOf(
                result.words.getOrNull(0)?.let { Candidate(it) },
                Candidate(typed, typed = true),
                result.words.getOrNull(1)?.let { Candidate(it) },
            )

            else -> order(result.words.map { Candidate(it) }).let {
                if (it.all { c -> c == null }) listOf(null, Candidate(typed, typed = true), null) else it
            }
        }
    }

    // The best word goes in the middle, the next ones to its sides.
    private fun order(words: List<Candidate>) = listOf(words.getOrNull(1), words.getOrNull(0), words.getOrNull(2))

    private fun before(ic: android.view.inputmethod.InputConnection, n: Int) =
        ic.getTextBeforeCursor(n, 0)?.toString().orEmpty()

    /** The word before the cursor: null at the start of a sentence, empty after other punctuation. */
    private fun previousWord(ic: android.view.inputmethod.InputConnection?): String? =
        previousWord(ic?.getTextBeforeCursor(64, 0)?.toString().orEmpty())

    private fun updateShift() {
        if (board.shift == Shift.Locked || board.mode != Mode.Letters) return
        val info = currentInputEditorInfo ?: return
        val caps = board.prefs.autoCaps && info.inputType != InputType.TYPE_NULL &&
                (currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0) != 0
        board.shift = if (caps) Shift.On else Shift.Off
    }

    private fun readClip() {
        val data = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
        val description = data?.description
        if (data == null || description == null) {
            board.clip = null
            return
        }
        val item = data.getItemAt(0)
        val sensitive = description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        val fresh = System.currentTimeMillis() - description.timestamp < CLIP_MAX_AGE_MS
        fun offer(clip: Clip?) {
            board.clip =
                if (board.prefs.paste && fresh && clip != null) clip.copy(hidden = clip.hidden || hideClip) else null
        }
        // Only a new copy is added: an edited or deleted clip must not come back from the clipboard.
        if (description.timestamp == lastStamp) return offer(clips.items.firstOrNull { it.id == lastClipId })
        lastStamp = description.timestamp
        val mime =
            (0 until description.mimeTypeCount).map(description::getMimeType).firstOrNull { it.startsWith("image/") }
        val uri = item.uri
        if (mime != null && uri != null) {
            // The clipboard grants access to the image only while it holds it, so it is copied now.
            val source = "${description.timestamp}|$uri"
            val known = clips.bySource(source)
            if (known != null) {
                lastClipId = known.id
                return offer(known)
            }
            board.clip = null
            lifecycleScope.launch {
                val clip = clips.addImage(contentResolver, uri, mime, source, sensitive)
                lastClipId = clip?.id ?: 0
                offer(clip)
            }
            return
        }
        val isText = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        val text = if (isText) item.text?.toString()?.takeIf { it.isNotBlank() } else null
        val clip = text?.let { clips.addText(it, sensitive) }
        lastClipId = clip?.id ?: 0
        offer(clip)
    }

    private fun enterIcon(info: EditorInfo): Int {
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return R.drawable.ic_keyboard_return
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_arrow_forward
            EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_search
            EditorInfo.IME_ACTION_SEND -> R.drawable.ic_send
            EditorInfo.IME_ACTION_DONE -> R.drawable.ic_check
            EditorInfo.IME_ACTION_PREVIOUS -> R.drawable.ic_arrow_back
            else -> R.drawable.ic_keyboard_return
        }
    }
}

fun previousWord(before: String): String? {
    val text = before.trimEnd()
    if (text.isEmpty() || text.last() in sentenceEnd) return null
    val start = text.indexOfLast { !it.isLetterOrDigit() && it != '\'' && it != '-' } + 1
    return text.substring(start)
}
