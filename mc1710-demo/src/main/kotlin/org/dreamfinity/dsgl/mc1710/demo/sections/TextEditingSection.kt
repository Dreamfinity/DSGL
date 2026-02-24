package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_OK

private const val SINGLE_KEY = "textEditing.single"
private const val PASSWORD_KEY = "textEditing.password"
private const val AREA_KEY = "textEditing.area"
private const val FAIL_COLOR = 0xFFFF8A8A.toInt()

fun UiScope.renderTextEditingSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div(
        ComponentProps(
            key = "section.textEditing",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("HTML-like text editing: caret blink, selection and clipboard shortcuts"))
        text(TextProps("Use Ctrl on Windows/Linux or Cmd on macOS for copy/cut/paste/select-all/undo/redo.").apply {
            color = DEMO_MUTED
        })

        text(TextProps("Single-line input"))
        input(
            InputProps(
                InputType.Text(
                    value = window.textEditingSingleValue,
                    placeholder = "Type and select text"
                )
            ).apply {
                key = SINGLE_KEY
                width = contentWidth - 8
                onFocusGain = {
                    window.textEditingSawFocus = true
                    window.logHook("textEditing.single.focus", it)
                }
                onInput = { event: InputEvent ->
                    window.textEditingSingleValue = event.value
                }
                onMouseDrag = { event: MouseDragEvent ->
                    trackSelectionDrag(window, event, SINGLE_KEY)
                }
                onKeyDown = { event: KeyboardKeyDownEvent ->
                    trackKeyboardEditing(window, event, SINGLE_KEY)
                }
            }
        )
        text(TextProps("Single-line: caret + selection visible in control").apply { color = DEMO_MUTED })

        text(TextProps("Password input (copy/cut restricted, paste allowed)"))
        input(
            InputProps(
                InputType.Password(
                    value = window.textEditingPasswordValue,
                    placeholder = "password"
                )
            ).apply {
                key = PASSWORD_KEY
                width = contentWidth - 8
                onFocusGain = {
                    window.textEditingSawFocus = true
                    window.logHook("textEditing.password.focus", it)
                }
                onInput = { event: InputEvent ->
                    window.textEditingPasswordValue = event.value
                }
                onMouseDrag = { event: MouseDragEvent ->
                    trackSelectionDrag(window, event, PASSWORD_KEY)
                }
                onKeyDown = { event: KeyboardKeyDownEvent ->
                    trackKeyboardEditing(window, event, PASSWORD_KEY)
                }
            }
        )
        text(TextProps("Password: masked selection/caret behavior").apply { color = DEMO_MUTED })

        text(TextProps("Textarea"))
        textarea(
            TextAreaProps("Multiline editing").apply {
                key = AREA_KEY
                width = contentWidth - 8
                height = 62
                value = window.textEditingAreaValue
                onFocusGain = {
                    window.textEditingSawFocus = true
                    window.logHook("textEditing.area.focus", it)
                }
                onInput = { event: InputEvent ->
                    window.textEditingAreaValue = event.value
                }
                onMouseDrag = { event: MouseDragEvent ->
                    trackSelectionDrag(window, event, AREA_KEY)
                }
                onKeyDown = { event: KeyboardKeyDownEvent ->
                    trackKeyboardEditing(window, event, AREA_KEY)
                }
            }
        )
        text(TextProps("Textarea: multiline selection + scroll-aware caret").apply { color = DEMO_MUTED })

        text(TextProps("Checklist"))
        checklistLine("caret blinks when focused", window.textEditingSawFocus)
        checklistLine("mouse drag selects and highlights text", window.textEditingSawSelectionDrag)
        checklistLine("Shift + arrows extends selection", window.textEditingSawShiftSelection)
        checklistLine("copy/cut/paste/undo/redo shortcuts are handled", window.textEditingSawClipboardShortcut)

        button(
            ButtonProps("Reset Checklist").apply {
                width = 72
                onMouseClick = {
                    window.textEditingSawSelectionDrag = false
                    window.textEditingSawShiftSelection = false
                    window.textEditingSawClipboardShortcut = false
                    window.textEditingSawFocus = false
                    window.logHook("textEditing.checklist.reset", it)
                }
            }
        )
    }
}

private fun UiScope.checklistLine(textValue: String, done: Boolean) {
    val mark = if (done) "[ok]" else "[ ]"
    val color = if (done) DEMO_OK else FAIL_COLOR
    text(TextProps("$mark $textValue").apply { this.color = color })
}

private fun trackSelectionDrag(window: ShowcaseWindow, event: MouseDragEvent, key: Any) {
    if (!window.textEditingSawSelectionDrag) {
        window.textEditingSawSelectionDrag = true
    }
    window.logHook("textEditing.selection.drag", event, "key=$key")
}

private fun trackKeyboardEditing(window: ShowcaseWindow, event: KeyboardKeyDownEvent, key: Any) {
    if (KeyModifiers.shiftDown && isArrowLike(event.keyCode)) {
        window.textEditingSawShiftSelection = true
        window.logHook("textEditing.selection.shiftKey", event, "key=$key code=${event.keyCode}")
    }
    if (KeyModifiers.shortcutDown && isClipboardShortcut(event.keyCode)) {
        window.textEditingSawClipboardShortcut = true
        window.logHook("textEditing.clipboard", event, "key=$key code=${event.keyCode}")
    }
}

private fun isArrowLike(keyCode: Int): Boolean {
    return keyCode == KeyCodes.LEFT ||
            keyCode == KeyCodes.RIGHT ||
            keyCode == KeyCodes.UP ||
            keyCode == KeyCodes.DOWN ||
            keyCode == KeyCodes.HOME ||
            keyCode == KeyCodes.END
}

private fun isClipboardShortcut(keyCode: Int): Boolean {
    return keyCode == KeyCodes.C ||
            keyCode == KeyCodes.X ||
            keyCode == KeyCodes.V ||
            keyCode == KeyCodes.A ||
            keyCode == KeyCodes.Z
}