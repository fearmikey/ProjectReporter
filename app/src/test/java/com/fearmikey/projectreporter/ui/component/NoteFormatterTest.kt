package com.fearmikey.projectreporter.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFormatterTest {

    /** Simulates pressing Enter at the cursor and running list post-processing. */
    private fun pressEnter(edit: NoteEdit): NoteEdit {
        val raw = NoteEdit(
            edit.text.replaceRange(edit.selectionMin, edit.selectionMax, "\n"),
            edit.selectionMin + 1
        )
        return NoteFormatter.onTextChange(edit, raw)
    }

    @Test
    fun `bullet toggles on and off for current line`() {
        val on = NoteFormatter.applyListStyle(NoteEdit("Crack in wall"), NoteListStyle.BULLET)
        assertEquals("- Crack in wall", on.text)
        assertEquals(on.text.length, on.selectionStart)

        val off = NoteFormatter.applyListStyle(on, NoteListStyle.BULLET)
        assertEquals("Crack in wall", off.text)
        assertEquals(off.text.length, off.selectionStart)
    }

    @Test
    fun `switching style replaces existing prefix`() {
        val edit = NoteFormatter.applyListStyle(NoteEdit("- item"), NoteListStyle.CHECKLIST)
        assertEquals("☐ item", edit.text)
    }

    @Test
    fun `numbered list over multi-line selection numbers sequentially and skips blank lines`() {
        val text = "one\n\ntwo\nthree"
        val edit = NoteFormatter.applyListStyle(NoteEdit(text, 0, text.length), NoteListStyle.NUMBERED)
        assertEquals("1. one\n\n2. two\n3. three", edit.text)
        assertEquals(3, edit.selectionStart) // just after the first "1. " marker
        assertEquals(edit.text.length, edit.selectionEnd)
    }

    @Test
    fun `numbered item continues numbering from previous line`() {
        val text = "1. first\nsecond"
        val edit = NoteFormatter.applyListStyle(NoteEdit(text), NoteListStyle.NUMBERED)
        assertEquals("1. first\n2. second", edit.text)
    }

    @Test
    fun `enter continues bullet list`() {
        val result = pressEnter(NoteEdit("- pipe leak"))
        assertEquals("- pipe leak\n- ", result.text)
        assertEquals(result.text.length, result.selectionStart)
    }

    @Test
    fun `enter continues numbered list and renumbers following items`() {
        val text = "1. a\n2. b"
        val result = pressEnter(NoteEdit(text, 4)) // cursor at end of "1. a"
        assertEquals("1. a\n2. \n3. b", result.text)
        assertEquals(8, result.selectionStart)
    }

    @Test
    fun `enter continues checklist with an unchecked item`() {
        val result = pressEnter(NoteEdit("☑ done"))
        assertEquals("☑ done\n☐ ", result.text)
    }

    @Test
    fun `enter on empty list item ends the list`() {
        val result = pressEnter(NoteEdit("- a\n- "))
        assertEquals("- a\n", result.text)
        assertEquals(4, result.selectionStart)
    }

    @Test
    fun `enter on plain line is untouched`() {
        val result = pressEnter(NoteEdit("hello"))
        assertEquals("hello\n", result.text)
    }

    @Test
    fun `toggle checked flips checklist items only`() {
        val text = "☐ a\nplain\n☑ b"
        val result = NoteFormatter.toggleChecked(NoteEdit(text, 0, text.length))
        assertEquals("☑ a\nplain\n☐ b", result.text)
    }

    @Test
    fun `insert on new line breaks a non-empty line`() {
        assertEquals("text\nIssue: ", NoteFormatter.insertOnNewLine(NoteEdit("text"), "Issue: ").text)
        assertEquals("- Issue: ", NoteFormatter.insertOnNewLine(NoteEdit("- "), "Issue: ").text)
    }

    @Test
    fun `insert inline adds separating spaces`() {
        assertEquals("at 14:00", NoteFormatter.insertInline(NoteEdit("at"), "14:00").text)
        assertEquals("a 14:00 b", NoteFormatter.insertInline(NoteEdit("ab", 1), "14:00").text)
    }

    @Test
    fun `word count ignores list markers`() {
        assertEquals(3, NoteFormatter.wordCount("- one\n☐ two three"))
        assertEquals(0, NoteFormatter.wordCount("  "))
    }

    @Test
    fun `history undo and redo`() {
        val history = NoteEditHistory(coalesceMillis = 1_000)
        val a = NoteEdit("a")
        val ab = NoteEdit("ab")
        history.record(NoteEdit(""), nowMillis = 0)
        history.record(a, nowMillis = 100) // coalesced with previous step
        assertTrue(history.canUndo)

        assertEquals(NoteEdit(""), history.undo(ab))
        assertFalse(history.canUndo)
        assertEquals(ab, history.redo(NoteEdit("")))
        assertNull(history.redo(ab))
    }
}
