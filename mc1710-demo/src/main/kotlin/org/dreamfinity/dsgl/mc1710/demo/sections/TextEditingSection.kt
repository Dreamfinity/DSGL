package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_OK

private const val SINGLE_KEY = "textEditing.single"
private const val PASSWORD_KEY = "textEditing.password"
private const val AREA_KEY = "textEditing.area"
private const val FAIL_COLOR = 0xFFFF8A8A.toInt()

fun UiScope.textEditingSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.textEditing"
        style = {
            width = contentWidth.px
            height = contentHeight.px
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("HTML-like text editing: caret blink, selection and clipboard shortcuts")
        text("Use Ctrl on Windows/Linux or Cmd on macOS for copy/cut/paste/select-all/undo/redo.", {
            style = { color = DEMO_MUTED }
        })

        text("Single-line input")
        input(
            InputType.Text(
                value = window.textEditingSingleValue,
                placeholder = "Type and select text"
            ),
            {
                key = SINGLE_KEY
                style = { width = (contentWidth - 8).px }
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
        text("Single-line: caret + selection visible in control", { style = { color = DEMO_MUTED } })

        text("Password input (copy/cut restricted, paste allowed)")
        input(
            InputType.Password(
                value = window.textEditingPasswordValue,
                placeholder = "password"
            ),
            {
                key = PASSWORD_KEY
                style = { width = (contentWidth - 8).px }
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
        text("Password: masked selection/caret behavior", { style = { color = DEMO_MUTED } })

        text("Textarea")
        textarea({
            placeholder = "Multiline editing"
            key = AREA_KEY
            style = {
                width = (contentWidth - 8).px
                height = 62.px
            }
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
        })
        text("Textarea: multiline selection + scroll-aware caret", { style = { color = DEMO_MUTED } })

        text("Checklist")
        checklistLine("caret blinks when focused", window.textEditingSawFocus)
        checklistLine("mouse drag selects and highlights text", window.textEditingSawSelectionDrag)
        checklistLine("Shift + arrows extends selection", window.textEditingSawShiftSelection)
        checklistLine("copy/cut/paste/undo/redo shortcuts are handled", window.textEditingSawClipboardShortcut)

        button("Reset Checklist", {
            style = { width = 72.px }
            onMouseClick = {
                window.textEditingSawSelectionDrag = false
                window.textEditingSawShiftSelection = false
                window.textEditingSawClipboardShortcut = false
                window.textEditingSawFocus = false
                window.logHook("textEditing.checklist.reset", it)
            }
        })
    }
}

private fun UiScope.checklistLine(textValue: String, done: Boolean) {
    val mark = if (done) "[ok]" else "[ ]"
    val color = if (done) DEMO_OK else FAIL_COLOR
    text("$mark $textValue", {
        style = {
            this.color = color
            foregroundColor(color)
        }
    })
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


