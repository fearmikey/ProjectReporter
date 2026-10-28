package com.fearmikey.projectreporter.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Immutable snapshot of a note's text and selection. Kept independent of Compose's
 * TextFieldValue so all editing logic is plain Kotlin and easy to unit test.
 */
data class NoteEdit(
    val text: String,
    val selectionStart: Int = text.length,
    val selectionEnd: Int = selectionStart
) {
    val selectionMin: Int get() = minOf(selectionStart, selectionEnd)
    val selectionMax: Int get() = maxOf(selectionStart, selectionEnd)
}

enum class NoteListStyle { BULLET, NUMBERED, CHECKLIST }

/**
 * Plain-text list formatting for notes. Notes are stored and exported (PDF / Word) as plain
 * text, so formatting is expressed with simple line prefixes:
 *  - bullets: `- `
 *  - numbered: `1. `
 *  - checklist: `☐ ` (open) / `☑ ` (done)
 */
object NoteFormatter {
    const val BULLET = "- "
    const val UNCHECKED = "☐ "
    const val CHECKED = "☑ "

    private val prefixRegex = Regex("""^([ \t]*)(- |• |\* |(\d{1,4})\. |☐ |☑ )""")
    private val wordRegex = Regex("""\S+""")

    data class LinePrefix(val indent: String, val marker: String, val number: Int?) {
        val length: Int get() = indent.length + marker.length
        val isChecked: Boolean get() = marker == CHECKED
        val style: NoteListStyle
            get() = when {
                number != null -> NoteListStyle.NUMBERED
                marker == UNCHECKED || marker == CHECKED -> NoteListStyle.CHECKLIST
                else -> NoteListStyle.BULLET
            }
    }

    private data class LineMapping(val oldStart: Int, val removed: Int, val newStart: Int, val inserted: Int)

    fun parsePrefix(line: String): LinePrefix? = prefixRegex.find(line)?.let {
        LinePrefix(it.groupValues[1], it.groupValues[2], it.groupValues[3].toIntOrNull())
    }

    fun lineStart(text: String, pos: Int): Int =
        if (pos <= 0) 0 else text.lastIndexOf('\n', pos - 1) + 1

    fun lineEnd(text: String, pos: Int): Int =
        text.indexOf('\n', pos).let { if (it < 0) text.length else it }

    private fun lineAt(text: String, pos: Int): String =
        text.substring(lineStart(text, pos), lineEnd(text, pos))

    /** List style of the line holding the cursor, or null for a plain line. */
    fun currentStyle(edit: NoteEdit): NoteListStyle? =
        parsePrefix(lineAt(edit.text, edit.selectionMin))?.style

    fun wordCount(text: String): Int =
        wordRegex.findAll(text).count { match -> match.value.any(Char::isLetterOrDigit) }

    /**
     * Toggles [style] on every line touched by the selection. If all (non-blank) lines already
     * use the style it is removed, otherwise any existing list prefix is replaced.
     */
    fun applyListStyle(edit: NoteEdit, style: NoteListStyle): NoteEdit {
        val text = edit.text
        val blockStart = lineStart(text, edit.selectionMin)
        val blockEnd = lineEnd(text, edit.selectionMax)
        val lines = text.substring(blockStart, blockEnd).split('\n')
        val multiLine = lines.size > 1
        val prefixes = lines.map(::parsePrefix)
        val targets = lines.indices.filter { !multiLine || lines[it].isNotBlank() }.toSet()
        if (targets.isEmpty()) return edit

        val removing = targets.all { prefixes[it]?.style == style }
        var number = if (style == NoteListStyle.NUMBERED) (previousNumber(text, blockStart) ?: 0) + 1 else 0

        val out = StringBuilder()
        val mappings = ArrayList<LineMapping>(lines.size)
        var oldOffset = blockStart
        lines.forEachIndexed { i, line ->
            val existing = prefixes[i]
            val newStart = blockStart + out.length
            val (removeLen, insert) = when {
                i !in targets -> 0 to ""
                removing -> existing!!.length to existing.indent
                else -> {
                    val marker = when (style) {
                        NoteListStyle.BULLET -> BULLET
                        NoteListStyle.NUMBERED -> "${number++}. "
                        NoteListStyle.CHECKLIST ->
                            if (existing?.style == NoteListStyle.CHECKLIST) existing.marker else UNCHECKED
                    }
                    (existing?.length ?: 0) to (existing?.indent.orEmpty() + marker)
                }
            }
            out.append(insert).append(line, removeLen, line.length)
            if (i < lines.lastIndex) out.append('\n')
            mappings += LineMapping(oldOffset, removeLen, newStart, insert.length)
            oldOffset += line.length + 1
        }

        fun map(pos: Int): Int {
            val m = mappings.last { it.oldStart <= pos }
            val col = pos - m.oldStart
            return if (col <= m.removed) m.newStart + m.inserted else m.newStart + m.inserted + (col - m.removed)
        }

        val newText = renumberAfter(
            text.substring(0, blockStart) + out + text.substring(blockEnd),
            blockStart + out.length
        )
        return NoteEdit(newText, map(edit.selectionStart), map(edit.selectionEnd))
    }

    /** Flips open/done state of checklist items touched by the selection. */
    fun toggleChecked(edit: NoteEdit): NoteEdit {
        val text = edit.text
        val start = lineStart(text, edit.selectionMin)
        val end = lineEnd(text, edit.selectionMax)
        val block = text.substring(start, end).split('\n').joinToString("\n") { line ->
            val p = parsePrefix(line)
            if (p?.style != NoteListStyle.CHECKLIST) line
            else p.indent + (if (p.isChecked) UNCHECKED else CHECKED) + line.substring(p.length)
        }
        // Markers have equal length, so the selection stays valid.
        return edit.copy(text = text.substring(0, start) + block + text.substring(end))
    }

    /**
     * Post-processes a raw text change from the keyboard. When Enter is pressed on a list
     * line the list is continued; pressing Enter on an empty list item ends the list.
     */
    fun onTextChange(old: NoteEdit, new: NoteEdit): NoteEdit {
        if (new.text.length != old.text.length + 1 || new.selectionStart != new.selectionEnd) return new
        val cursor = new.selectionStart
        if (cursor == 0 || new.text[cursor - 1] != '\n') return new
        if (new.text.substring(0, cursor - 1) + new.text.substring(cursor) != old.text) return new

        val prevLineStart = lineStart(new.text, cursor - 1)
        val prevLine = new.text.substring(prevLineStart, cursor - 1)
        val prefix = parsePrefix(prevLine) ?: return new

        if (prevLine.substring(prefix.length).isBlank() && lineAt(new.text, cursor).isBlank()) {
            // Empty item: end the list by clearing the prefix and dropping the new line.
            return NoteEdit(new.text.substring(0, prevLineStart) + new.text.substring(cursor), prevLineStart)
        }

        val continuation = prefix.indent + when (prefix.style) {
            NoteListStyle.NUMBERED -> "${prefix.number!! + 1}. "
            NoteListStyle.CHECKLIST -> UNCHECKED
            NoteListStyle.BULLET -> prefix.marker
        }
        var text = new.text.substring(0, cursor) + continuation + new.text.substring(cursor)
        val newCursor = cursor + continuation.length
        if (prefix.style == NoteListStyle.NUMBERED) text = renumberAfter(text, lineEnd(text, newCursor))
        return NoteEdit(text, newCursor)
    }

    /** Replaces the selection with [insert]. */
    fun insertText(edit: NoteEdit, insert: String): NoteEdit {
        val text = edit.text.replaceRange(edit.selectionMin, edit.selectionMax, insert)
        return NoteEdit(text, edit.selectionMin + insert.length)
    }

    /** Inserts a word/phrase at the cursor, adding separating spaces where needed. */
    fun insertInline(edit: NoteEdit, insert: String): NoteEdit {
        val text = edit.text
        val leading = edit.selectionMin > 0 && !text[edit.selectionMin - 1].isWhitespace()
        val trailing = edit.selectionMax < text.length && !text[edit.selectionMax].isWhitespace()
        return insertText(edit, (if (leading) " " else "") + insert + (if (trailing) " " else ""))
    }

    /** Inserts [insert] at the start of a line, breaking the current line if it has content. */
    fun insertOnNewLine(edit: NoteEdit, insert: String): NoteEdit {
        val text = edit.text
        val lineSoFar = text.substring(lineStart(text, edit.selectionMin), edit.selectionMin)
        val content = lineSoFar.substring(parsePrefix(lineSoFar)?.length ?: 0)
        return insertText(edit, if (content.isBlank()) insert else "\n$insert")
    }

    private fun previousNumber(text: String, lineStartPos: Int): Int? =
        if (lineStartPos == 0) null else parsePrefix(lineAt(text, lineStartPos - 1))?.number

    /**
     * Renumbers the run of numbered lines directly following the line that ends at [pos].
     * Only text after [pos] changes, so selections before it remain valid.
     */
    internal fun renumberAfter(text: String, pos: Int): String {
        if (pos >= text.length) return text
        var expected = (parsePrefix(lineAt(text, pos))?.number ?: 0) + 1
        val sb = StringBuilder(text.length + 4).append(text, 0, pos + 1)
        var i = pos + 1
        while (i <= text.length) {
            val end = lineEnd(text, i)
            val line = text.substring(i, end)
            val p = parsePrefix(line)
            if (p?.number == null) {
                sb.append(text, i, text.length)
                return sb.toString()
            }
            sb.append(p.indent).append(expected++).append(". ").append(line, p.length, line.length)
            if (end < text.length) sb.append('\n')
            i = end + 1
        }
        return sb.toString()
    }
}

/**
 * Undo/redo history. Rapid typing within [coalesceMillis] is grouped into one undo step;
 * toolbar actions always create their own step.
 */
class NoteEditHistory(
    private val maxSize: Int = 100,
    private val coalesceMillis: Long = 1_000
) {
    private val undoStack = ArrayDeque<NoteEdit>()
    private val redoStack = ArrayDeque<NoteEdit>()
    private var lastRecordAt = 0L

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Records [before] as an undo point for a text change happening now. */
    fun record(before: NoteEdit, nowMillis: Long = System.currentTimeMillis(), force: Boolean = false) {
        redoStack.clear()
        if (force || undoStack.isEmpty() || nowMillis - lastRecordAt >= coalesceMillis) {
            undoStack.addLast(before)
            if (undoStack.size > maxSize) undoStack.removeFirst()
        }
        lastRecordAt = if (force) 0L else nowMillis
        sync()
    }

    fun undo(current: NoteEdit): NoteEdit? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        lastRecordAt = 0L
        sync()
        return previous
    }

    fun redo(current: NoteEdit): NoteEdit? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        lastRecordAt = 0L
        sync()
        return next
    }

    private fun sync() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
}
