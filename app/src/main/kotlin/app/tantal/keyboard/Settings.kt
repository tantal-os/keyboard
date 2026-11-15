package app.tantal.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.tantal.theme.TantalTheme

data class Prefs(
    val suggestions: Boolean = true,
    val autocorrect: Boolean = true,
    val autoCaps: Boolean = true,
    val doubleSpace: Boolean = true,
    val paste: Boolean = true,
    val vibrate: Boolean = true,
    val numberRow: Boolean = true,
    val extended: Boolean = true,
    val languageKey: Boolean = false,
) {
    /** Dp. */
    val keyHeight get() = if (extended) 48 else 44

    companion object {
        fun read(context: Context): Prefs {
            val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            return Prefs(
                suggestions = p.getBoolean("suggestions", true),
                autocorrect = p.getBoolean("autocorrect", true),
                autoCaps = p.getBoolean("auto_caps", true),
                doubleSpace = p.getBoolean("double_space", true),
                paste = p.getBoolean("paste", true),
                vibrate = p.getBoolean("vibrate", true),
                numberRow = p.getBoolean("number_row", true),
                extended = p.getBoolean("extended", true),
                languageKey = p.getBoolean("language_key", false),
            )
        }
    }
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TantalTheme { Surface(Modifier.fillMaxSize()) { SettingsScreen() } } }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val store = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var prefs by remember { mutableStateOf(Prefs.read(context)) }
    var setup by remember { mutableStateOf(setupStep(context)) }
    var languageNames by remember { mutableStateOf("") }
    LifecycleResumeEffect(Unit) {
        setup = setupStep(context)
        languageNames = enabledLanguages(context)
        onPauseOrDispose {}
    }
    fun edit(key: String, value: Boolean) {
        store.edit().putBoolean(key, value).apply()
        prefs = Prefs.read(context)
    }

    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.app_name),
            Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (setup != Setup.Done) {
            SetupCard(setup)
        } else {
            OutlinedTextField(
                "", {}, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.try_it)) },
            )
        }
        Section(stringResource(R.string.languages))
        Item(languageNames, stringResource(R.string.languages_sub), onClick = {
            val id = context.getSystemService(InputMethodManager::class.java).enabledInputMethodList
                .firstOrNull { it.packageName == context.packageName }?.id
            context.startActivity(
                Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS).putExtra(Settings.EXTRA_INPUT_METHOD_ID, id),
            )
        })
        SwitchItem(stringResource(R.string.language_key), stringResource(R.string.language_key_sub), prefs.languageKey) {
            edit("language_key", it)
        }
        Section(stringResource(R.string.typing))
        SwitchItem(stringResource(R.string.suggestions), stringResource(R.string.suggestions_sub), prefs.suggestions) {
            edit("suggestions", it)
        }
        SwitchItem(stringResource(R.string.autocorrect), stringResource(R.string.autocorrect_sub), prefs.autocorrect) {
            edit("autocorrect", it)
        }
        SwitchItem(stringResource(R.string.auto_caps), checked = prefs.autoCaps) { edit("auto_caps", it) }
        SwitchItem(stringResource(R.string.double_space), stringResource(R.string.double_space_sub), prefs.doubleSpace) {
            edit("double_space", it)
        }
        Section(stringResource(R.string.clipboard))
        SwitchItem(stringResource(R.string.paste_button), stringResource(R.string.paste_button_sub), prefs.paste) {
            edit("paste", it)
        }
        Section(stringResource(R.string.keys))
        SwitchItem(stringResource(R.string.vibrate), checked = prefs.vibrate) { edit("vibrate", it) }
        SwitchItem(stringResource(R.string.number_row), checked = prefs.numberRow) { edit("number_row", it) }
        SwitchItem(stringResource(R.string.extended), stringResource(R.string.extended_sub), prefs.extended) {
            edit("extended", it)
        }
        Spacer(Modifier.height(24.dp))
    }

}


private enum class Setup { Enable, Select, Done }

private fun enabledLanguages(context: Context): String {
    val imm = context.getSystemService(InputMethodManager::class.java)
    val ours = imm.inputMethodList.first { it.packageName == context.packageName }
    val enabled = imm.getEnabledInputMethodSubtypeList(ours, true).ifEmpty {
        List(ours.subtypeCount) { ours.getSubtypeAt(it) }
    }
    return enabled.joinToString { it.getDisplayName(context, context.packageName, context.applicationInfo) }
}

private fun setupStep(context: Context): Setup {
    val imm = context.getSystemService(InputMethodManager::class.java)
    val ours = imm.enabledInputMethodList.firstOrNull { it.packageName == context.packageName } ?: return Setup.Enable
    val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    return if (current == ours.id) Setup.Done else Setup.Select
}

@Composable
private fun SetupCard(step: Setup) {
    val context = LocalContext.current
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (step == Setup.Enable) R.string.setup_enable else R.string.setup_select),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(if (step == Setup.Enable) R.string.setup_enable_sub else R.string.setup_select_sub),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button({
                if (step == Setup.Enable) {
                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                } else {
                    context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                }
            }) {
                Text(stringResource(if (step == Setup.Enable) R.string.setup_enable_button else R.string.setup_select_button))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun Item(title: String, sub: String = "", onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        trailing()
    }
}

// The whole row toggles, so the switch itself takes no touches.
@Composable
private fun SwitchItem(title: String, sub: String = "", checked: Boolean, onChange: (Boolean) -> Unit) {
    Item(title, sub, onClick = { onChange(!checked) }) { Switch(checked, null) }
}
