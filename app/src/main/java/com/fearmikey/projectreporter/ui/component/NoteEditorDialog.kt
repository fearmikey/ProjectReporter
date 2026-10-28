package com.fearmikey.projectreporter.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen note editor with list/checklist formatting, smart list continuation,
 * undo/redo, quick-insert templates, timestamps, optional voice dictation and
 * unsaved-changes protection.
 *
 * @param createdAt creation time of an existing note, or null when creating a new note.
 * @param onDelete shown as a menu action when non-null (i.e. when editing an existing note).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorDialog(
    initialContent: String,
    createdAt: Long?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val state = rememberNoteEditorState(initialContent)

    var showMenu by remember { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    val isNew = createdAt == null
    val hasChanges = state.text != initialContent
    val canSave = state.text.isNotBlank() && hasChanges

    fun requestClose() {
        if (hasChanges) showDiscardConfirm = true else onDismiss()
    }

    fun save() {
        if (canSave) onSave(state.text.trimEnd())
    }

    val dictate = rememberDictation("Dictate your note") { spoken ->
        state.insertInline(spoken)
        focusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    navigationIcon = {
                        IconButton(onClick = ::requestClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                if (isNew) "New Note" else "Edit Note",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (createdAt != null) {
                                    "Created " + SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale.getDefault())
                                        .format(Date(createdAt))
                                } else {
                                    SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date())
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = state::undo, enabled = state.history.canUndo) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = state::redo, enabled = state.history.canRedo) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }
                        Button(
                            onClick = ::save,
                            enabled = canSave,
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            Text("Save")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Clear text") },
                                leadingIcon = { Icon(Icons.Default.ClearAll, contentDescription = null) },
                                enabled = state.text.isNotEmpty(),
                                onClick = {
                                    showMenu = false
                                    state.clear()
                                }
                            )
                            if (onDelete != null) {
                                DropdownMenuItem(
                                    text = { Text("Delete note", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }
                )
                HorizontalDivider()

                TextField(
                    value = state.value,
                    onValueChange = state::onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text("Write your note…\n\nUse the toolbar below for bullets, numbered lists and checklists.")
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                NoteQuickInsertRow(state)

                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    NoteFormattingToolbar(state, onDictate = dictate)
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved changes to this note will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onDismiss()
                }) { Text("Discard") }
            },
            dismissButton = {
                if (canSave) {
                    TextButton(onClick = {
                        showDiscardConfirm = false
                        save()
                    }) { Text("Save") }
                } else {
                    TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep editing") }
                }
            }
        )
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete this note?") },
            text = { Text("The note will be moved to the Recycle Bin, where it can be restored.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
