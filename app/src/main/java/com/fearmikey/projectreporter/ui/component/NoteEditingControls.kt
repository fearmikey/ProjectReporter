package com.fearmikey.projectreporter.ui.component

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun TextFieldValue.toEdit() = NoteEdit(text, selection.start, selection.end)
internal fun NoteEdit.toValue() = TextFieldValue(text, TextRange(selectionStart, selectionEnd))

/**
 * Editing state shared by the note editors (report notes and photo notes): the current
 * text/selection, undo/redo history and smart list continuation.
 */
@Stable
class NoteEditorState(initial: TextFieldValue) {
    var value by mutableStateOf(initial)
        internal set

    val history = NoteEditHistory()

    val text: String get() = value.text
    val currentStyle: NoteListStyle? get() = NoteFormatter.currentStyle(value.toEdit())

    /** Applies a toolbar-driven edit as its own undo step. */
    fun applyEdit(edit: NoteEdit) {
        val current = value.toEdit()
        if (edit.text != current.text) history.record(current, force = true)
        value = edit.toValue()
    }

    /** Handles raw keyboard input, continuing lists and recording undo points. */
    fun onValueChange(incoming: TextFieldValue) {
        if (incoming.text == value.text) {
            // Selection / composition change only.
            value = incoming
        } else {
            val old = value.toEdit()
            val processed = NoteFormatter.onTextChange(old, incoming.toEdit())
            history.record(old)
            // Keep the IME's composition intact unless list continuation rewrote the text.
            value = if (processed == incoming.toEdit()) incoming else processed.toValue()
        }
    }

    fun undo() { history.undo(value.toEdit())?.let { value = it.toValue() } }
    fun redo() { history.redo(value.toEdit())?.let { value = it.toValue() } }

    fun insertOnNewLine(text: String) = applyEdit(NoteFormatter.insertOnNewLine(value.toEdit(), text))
    fun insertInline(text: String) = applyEdit(NoteFormatter.insertInline(value.toEdit(), text))
    fun applyListStyle(style: NoteListStyle) = applyEdit(NoteFormatter.applyListStyle(value.toEdit(), style))
    fun toggleChecked() = applyEdit(NoteFormatter.toggleChecked(value.toEdit()))
    fun clear() = applyEdit(NoteEdit(""))
}

/** Remembers a [NoteEditorState] whose text survives configuration changes. */
@Composable
fun rememberNoteEditorState(initialContent: String): NoteEditorState =
    rememberSaveable(
        saver = Saver<NoteEditorState, Any>(
            save = { state -> with(TextFieldValue.Saver) { save(state.value) } },
            restore = { saved -> TextFieldValue.Saver.restore(saved)?.let(::NoteEditorState) }
        )
    ) {
        NoteEditorState(TextFieldValue(initialContent, TextRange(initialContent.length)))
    }

/**
 * Voice dictation helper. Returns null when no speech recogniser is installed, otherwise a
 * function that launches dictation and delivers the recognised text to [onResult].
 */
@Composable
fun rememberDictation(prompt: String, onResult: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val intent = remember(prompt) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
    }
    val available = remember(intent) { intent.resolveActivity(context.packageManager) != null }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            currentOnResult(spoken)
        }
    }
    return if (available) ({ launcher.launch(intent) }) else null
}

private data class QuickInsert(val label: String, val icon: ImageVector, val text: String)

private val quickInserts = listOf(
    QuickInsert("Observation", Icons.Default.Visibility, "Observation: "),
    QuickInsert("Issue", Icons.Default.Report, "Issue: "),
    QuickInsert("Action required", Icons.Default.Build, "Action required: "),
    QuickInsert("Safety", Icons.Default.HealthAndSafety, "Safety: "),
    QuickInsert("Follow-up", Icons.Default.Update, "Follow-up: ")
)

/** Horizontally scrolling row of site-report templates plus time / date stamps. */
@Composable
fun NoteQuickInsertRow(
    state: NoteEditorState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        quickInserts.forEach { item ->
            AssistChip(
                onClick = { state.insertOnNewLine(item.text) },
                label = { Text(item.label) },
                leadingIcon = {
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                }
            )
        }
        AssistChip(
            onClick = { state.insertInline(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) },
            label = { Text("Time") },
            leadingIcon = {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            }
        )
        AssistChip(
            onClick = {
                state.insertInline(DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date()))
            },
            label = { Text("Date") },
            leadingIcon = {
                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            }
        )
    }
}

/** Bullet / numbered / checklist toggles, a "mark done" action, dictation and a word count. */
@Composable
fun NoteFormattingToolbar(
    state: NoteEditorState,
    onDictate: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val currentStyle = state.currentStyle
    val text = state.text
    val wordCount = remember(text) { NoteFormatter.wordCount(text) }
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconToggleButton(
            checked = currentStyle == NoteListStyle.BULLET,
            onCheckedChange = { state.applyListStyle(NoteListStyle.BULLET) }
        ) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Bullet list")
        }
        FilledTonalIconToggleButton(
            checked = currentStyle == NoteListStyle.NUMBERED,
            onCheckedChange = { state.applyListStyle(NoteListStyle.NUMBERED) }
        ) {
            Icon(Icons.Default.FormatListNumbered, contentDescription = "Numbered list")
        }
        FilledTonalIconToggleButton(
            checked = currentStyle == NoteListStyle.CHECKLIST,
            onCheckedChange = { state.applyListStyle(NoteListStyle.CHECKLIST) }
        ) {
            Icon(Icons.Default.Checklist, contentDescription = "Checklist")
        }
        IconButton(
            onClick = state::toggleChecked,
            enabled = currentStyle == NoteListStyle.CHECKLIST
        ) {
            Icon(Icons.Default.CheckBox, contentDescription = "Mark item done")
        }
        if (onDictate != null) {
            IconButton(onClick = onDictate) {
                Icon(Icons.Default.Mic, contentDescription = "Dictate")
            }
        }
        Spacer(modifier = Modifier.weight(1f).width(8.dp))
        Text(
            text = "$wordCount ${if (wordCount == 1) "word" else "words"} · ${text.length} chars",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}
