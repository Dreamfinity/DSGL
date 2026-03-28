package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.useState
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_OK

private const val SINGLE_KEY = "textEditing.single"
private const val PASSWORD_KEY = "textEditing.password"
private const val AREA_KEY = "textEditing.area"
private const val FAIL_COLOR = 0xFFFF8A8A.toInt()

fun UiScope.textEditingSection(onLogHook: (String, Event, String?) -> Unit) {
    var textEditingSingleValue by useState("Edit this line")
    var textEditingPasswordValue by useState("secret42")
    var textEditingAreaValue by useState(
        "Line 1: drag-select me\nLine 2: use Shift+Arrows\nLine 3: Ctrl/Cmd+C/V/X"
    )
    var textEditingSawSelectionDrag by useState(false)
    var textEditingSawShiftSelection by useState(false)
    var textEditingSawClipboardShortcut by useState(false)
    var textEditingSawFocus by useState(false)

    div({
        key = "section.textEditing"
        style = {
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
                value = textEditingSingleValue,
                placeholder = "Type and select text"
            ),
            {
                key = SINGLE_KEY
                style = { width = 100.percent }
                onFocusGain = {
                    textEditingSawFocus = true
                    onLogHook("textEditing.single.focus", it, null)
                }
                onInput = { event ->
                    textEditingSingleValue = event.value
                }
                onMouseDrag = { event ->
                    textEditingSawSelectionDrag = true
                    onLogHook("textEditing.selection.drag", event, "key=$SINGLE_KEY")
                }
                onKeyDown = { event ->
                    if (KeyModifiers.shiftDown && isArrowLike(event.keyCode)) {
                        textEditingSawShiftSelection = true
                        onLogHook("textEditing.selection.shiftKey", event, "key=$SINGLE_KEY code=${event.keyCode}")
                    }
                    if (KeyModifiers.shortcutDown && isClipboardShortcut(event.keyCode)) {
                        textEditingSawClipboardShortcut = true
                        onLogHook("textEditing.clipboard", event, "key=$SINGLE_KEY code=${event.keyCode}")
                    }
                }
            }
        )
        text("Single-line: caret + selection visible in control", {
            style = { color = DEMO_MUTED }
        })

        text("Password input (copy/cut restricted, paste allowed)")
        input(
            InputType.Password(
                value = textEditingPasswordValue,
                placeholder = "password"
            ),
            {
                key = PASSWORD_KEY
                style = { width = 100.percent }
                onFocusGain = {
                    textEditingSawFocus = true
                    onLogHook("textEditing.password.focus", it, null)
                }
                onInput = { event ->
                    textEditingPasswordValue = event.value
                }
                onMouseDrag = { event ->
                    textEditingSawSelectionDrag = true
                    onLogHook("textEditing.selection.drag", event, "key=$PASSWORD_KEY")
                }
                onKeyDown = { event ->
                    if (KeyModifiers.shiftDown && isArrowLike(event.keyCode)) {
                        textEditingSawShiftSelection = true
                        onLogHook("textEditing.selection.shiftKey", event, "key=$PASSWORD_KEY code=${event.keyCode}")
                    }
                    if (KeyModifiers.shortcutDown && isClipboardShortcut(event.keyCode)) {
                        textEditingSawClipboardShortcut = true
                        onLogHook("textEditing.clipboard", event, "key=$PASSWORD_KEY code=${event.keyCode}")
                    }
                }
            }
        )
        text("Password: masked selection/caret behavior", {
            style = { color = DEMO_MUTED }
        })

        text("Textarea")
        textarea({
            placeholder = "Multiline editing"
            key = AREA_KEY
            style = {
                width = 100.percent
                height = 3.em
            }
            value = textEditingAreaValue
            onFocusGain = {
                textEditingSawFocus = true
                onLogHook("textEditing.area.focus", it, null)
            }
            onInput = { event ->
                textEditingAreaValue = event.value
            }
            onMouseDrag = { event ->
                textEditingSawSelectionDrag = true
                onLogHook("textEditing.selection.drag", event, "key=$AREA_KEY")
            }
            onKeyDown = { event ->
                if (KeyModifiers.shiftDown && isArrowLike(event.keyCode)) {
                    textEditingSawShiftSelection = true
                    onLogHook("textEditing.selection.shiftKey", event, "key=$AREA_KEY code=${event.keyCode}")
                }
                if (KeyModifiers.shortcutDown && isClipboardShortcut(event.keyCode)) {
                    textEditingSawClipboardShortcut = true
                    onLogHook("textEditing.clipboard", event, "key=$AREA_KEY code=${event.keyCode}")
                }
            }
        })
        text("Textarea: multiline selection + scroll-aware caret", {
            style = { color = DEMO_MUTED }
        })

        text("Checklist")
        checklistLine("caret blinks when focused", textEditingSawFocus)
        checklistLine("mouse drag selects and highlights text", textEditingSawSelectionDrag)
        checklistLine("Shift + arrows extends selection", textEditingSawShiftSelection)
        checklistLine("copy/cut/paste/undo/redo shortcuts are handled", textEditingSawClipboardShortcut)

        button("Reset Checklist", {
            onMouseClick = {
                textEditingSawSelectionDrag = false
                textEditingSawShiftSelection = false
                textEditingSawClipboardShortcut = false
                textEditingSawFocus = false
                onLogHook("textEditing.checklist.reset", it, null)
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
