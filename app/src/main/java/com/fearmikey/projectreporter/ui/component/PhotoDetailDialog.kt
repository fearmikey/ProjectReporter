package com.fearmikey.projectreporter.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.fearmikey.projectreporter.data.entity.PhotoEntity

/**
 * Full-screen photo viewer with a pinch / double-tap zoomable image and a note panel.
 *
 * The note is edited in a draft and only written to the database when the user taps
 * **Done**, presses back, or closes the viewer — never on every keystroke.
 *
 * @param onSaveNote called with the trimmed note text when it has changed.
 * @param onDelete moves the photo to the Recycle Bin.
 */
@Composable
fun PhotoDetailDialog(
    photo: PhotoEntity,
    onDismiss: () -> Unit,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit
) {
    var isEditing by rememberSaveable(photo.photoId) { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showRemoveNoteConfirm by rememberSaveable { mutableStateOf(false) }

    // A fresh draft is created each time editing starts and discarded when it ends.
    val editor: NoteEditorState? = if (isEditing) {
        key(photo.photoId) { rememberNoteEditorState(photo.annotation) }
    } else null

    fun commitDraft() {
        val draft = editor ?: return
        val text = draft.text.trimEnd()
        if (text != photo.annotation) onSaveNote(text)
        isEditing = false
    }

    fun close() {
        commitDraft()
        onDismiss()
    }

    Dialog(
        onDismissRequest = { if (isEditing) commitDraft() else onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize()) {
                PhotoTopBar(
                    timestamp = photo.timestampOverlay,
                    isEditing = isEditing,
                    editor = editor,
                    hasNote = photo.annotation.isNotBlank(),
                    showMenu = showMenu,
                    onShowMenu = { showMenu = it },
                    onClose = ::close,
                    onDone = ::commitDraft,
                    onEdit = { isEditing = true },
                    onRemoveNote = { showRemoveNoteConfirm = true },
                    onDelete = { showDeleteConfirm = true }
                )

                ZoomableImage(
                    model = photo.imageUri,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        if (editor != null) {
                            PhotoNoteEditor(editor)
                        } else {
                            PhotoNoteSummary(
                                note = photo.annotation,
                                onEdit = { isEditing = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRemoveNoteConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveNoteConfirm = false },
            icon = { Icon(Icons.Default.ClearAll, contentDescription = null) },
            title = { Text("Remove note?") },
            text = { Text("The note attached to this photo will be removed. The photo itself is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveNoteConfirm = false
                    onSaveNote("")
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveNoteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete this photo?") },
            text = { Text("The photo and its note will be moved to the Recycle Bin, where it can be restored.") },
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

@Composable
private fun PhotoTopBar(
    timestamp: String,
    isEditing: Boolean,
    editor: NoteEditorState?,
    hasNote: Boolean,
    showMenu: Boolean,
    onShowMenu: (Boolean) -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    onRemoveNote: () -> Unit,
    onDelete: () -> Unit
) {
    val iconColors = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.38f)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, colors = iconColors) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                if (isEditing) "Photo note" else "Photo",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (timestamp.isNotBlank()) {
                Text(
                    "Captured $timestamp",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isEditing && editor != null) {
            IconButton(onClick = editor::undo, enabled = editor.history.canUndo, colors = iconColors) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = editor::redo, enabled = editor.history.canRedo, colors = iconColors) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            Button(
                onClick = onDone,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text("Done")
            }
        } else {
            IconButton(onClick = onEdit, colors = iconColors) {
                Icon(
                    if (hasNote) Icons.Default.Edit else Icons.Default.AddComment,
                    contentDescription = if (hasNote) "Edit note" else "Add note"
                )
            }
            Box {
                IconButton(onClick = { onShowMenu(true) }, colors = iconColors) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { onShowMenu(false) }) {
                    if (hasNote) {
                        DropdownMenuItem(
                            text = { Text("Remove note") },
                            leadingIcon = { Icon(Icons.Default.ClearAll, contentDescription = null) },
                            onClick = {
                                onShowMenu(false)
                                onRemoveNote()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete photo", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            onShowMenu(false)
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/** Read-only view of the note, or a prompt to add one. Tapping either opens the editor. */
@Composable
private fun PhotoNoteSummary(note: String, onEdit: () -> Unit) {
    if (note.isBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Add note", onClick = onEdit)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.padding(8.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Add a note", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Describe what this photo shows. Notes are included in exported reports.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Edit note", onClick = onEdit)
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.Notes,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Note",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
            }
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun PhotoNoteEditor(editor: NoteEditorState) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val dictate = rememberDictation("Describe this photo") { spoken ->
        editor.insertInline(spoken)
        focusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
        OutlinedTextField(
            value = editor.value,
            onValueChange = editor::onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(min = 112.dp, max = 220.dp)
                .focusRequester(focusRequester),
            label = { Text("Photo note") },
            placeholder = { Text("What does this photo show? Add observations, issues or actions…") },
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            trailingIcon = if (editor.text.isNotEmpty()) {
                {
                    IconButton(onClick = editor::clear) {
                        Icon(Icons.Default.Close, contentDescription = "Clear note")
                    }
                }
            } else null
        )
        NoteQuickInsertRow(editor, contentPadding = PaddingValues(horizontal = 16.dp))
        HorizontalDivider()
        NoteFormattingToolbar(editor, onDictate = dictate)
    }
}

/** Image with pinch-to-zoom, panning while zoomed, and double-tap to toggle zoom. */
@Composable
private fun ZoomableImage(model: Any?, modifier: Modifier = Modifier) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(raw: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(model) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val target = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            scale = target
                            offset = clamp((center - tap) * (target - 1f), target)
                        }
                    }
                )
            }
            .pointerInput(model) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    offset = if (newScale > 1f) clamp(offset + pan, newScale) else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Photo",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}
