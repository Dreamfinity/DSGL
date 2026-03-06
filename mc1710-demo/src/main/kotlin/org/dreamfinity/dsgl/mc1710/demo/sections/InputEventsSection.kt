package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.FocusGainEvent
import org.dreamfinity.dsgl.core.event.FocusLoseEvent
import org.dreamfinity.dsgl.core.event.InputEvent
import org.dreamfinity.dsgl.core.event.ValueChangedEvent
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_SURFACE_ALT

fun UiScope.inputEventsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val halfWidth = ((contentWidth - 8) / 2).coerceAtLeast(90)

    div({
        key = "section.inputEvents"
        style = {
            width = contentWidth
            height = contentHeight
            gap = 4

            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("HTML-like events demo: onFocus/onBlur/onInput/onChange")
        text(
            "Proof case: type in text field, then click elsewhere -> onInput per key, onChange on blur.",
            { style = { color = DEMO_MUTED } }
        )

        div({
            style = {
                gap = 6
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            div({
                key = "inputEvents.left"
                style = {
                    width = halfWidth
                    gap = 3
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Text input")
                input(
                    InputType.Text(value = window.inputEventTextValue, placeholder = "Type then blur"),
                    {
                        key = "inputEvents.text"
                        style = { width = halfWidth - 6 }
                        onFocusGain = { event: FocusGainEvent ->
                            window.recordInputEvent("text", "focus", window.inputEventTextValue, event)
                        }
                        onFocusLose = { event: FocusLoseEvent ->
                            window.recordInputEvent("text", "blur", window.inputEventTextValue, event)
                        }
                        onInput = { event: InputEvent ->
                            window.inputEventTextValue = event.value
                            window.recordInputEvent("text", "input", event.value, event)
                        }
                        onValueChange = { event: ValueChangedEvent ->
                            window.inputEventTextValue = event.value
                            window.recordInputEvent("text", "change", event.value, event)
                        }
                    }
                )

                text("Textarea")
                textarea({
                    placeholder = "Multiline event sample"
                    key = "inputEvents.textarea"
                    style = {
                        width = halfWidth - 6
                        height = 46
                    }
                    value = window.inputEventTextareaValue
                    onFocusGain = { event: FocusGainEvent ->
                        window.recordInputEvent("textarea", "focus", window.inputEventTextareaValue, event)
                    }
                    onFocusLose = { event: FocusLoseEvent ->
                        window.recordInputEvent("textarea", "blur", window.inputEventTextareaValue, event)
                    }
                    onInput = { event: InputEvent ->
                        window.inputEventTextareaValue = event.value
                        window.recordInputEvent("textarea", "input", event.value.replace("\n", "\\n"), event)
                    }
                    onValueChange = { event: ValueChangedEvent ->
                        window.inputEventTextareaValue = event.value
                        window.recordInputEvent("textarea", "change", event.value.replace("\n", "\\n"), event)
                    }
                })
            }

            div({
                key = "inputEvents.right"
                style = {
                    width = halfWidth
                    gap = 3
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Checkbox")
                input(
                    InputType.Checkbox(
                        variants = window.checkboxOptions,
                        selected = window.inputEventCheckboxValue,
                        minSelected = 0,
                        maxSelected = 3
                    ),
                    {
                        key = "inputEvents.checkbox"
                        style = { width = halfWidth - 6 }
                        onFocusGain = { event: FocusGainEvent ->
                            window.recordInputEvent("checkbox", "focus", window.checkboxValueString(), event)
                        }
                        onFocusLose = { event: FocusLoseEvent ->
                            window.recordInputEvent("checkbox", "blur", window.checkboxValueString(), event)
                        }
                        onInput = { event: InputEvent ->
                            window.inputEventCheckboxValue = window.parseCheckboxSelection(event.parsedValue)
                            window.recordInputEvent("checkbox", "input", event.value, event)
                        }
                        onValueChange = { event: ValueChangedEvent ->
                            window.inputEventCheckboxValue = window.parseCheckboxSelection(event.parsedValue)
                            window.recordInputEvent("checkbox", "change", event.value, event)
                        }
                    }
                )

                text("Radio")
                input(
                    InputType.Radio(
                        variants = window.radioOptions,
                        selected = window.inputEventRadioValue
                    ),
                    {
                        key = "inputEvents.radio"
                        style = { width = halfWidth - 6 }
                        onFocusGain = { event: FocusGainEvent ->
                            window.recordInputEvent("radio", "focus", window.inputEventRadioValue ?: "", event)
                        }
                        onFocusLose = { event: FocusLoseEvent ->
                            window.recordInputEvent("radio", "blur", window.inputEventRadioValue ?: "", event)
                        }
                        onInput = { event: InputEvent ->
                            window.inputEventRadioValue = event.parsedValue as? String
                            window.recordInputEvent("radio", "input", event.value, event)
                        }
                        onValueChange = { event: ValueChangedEvent ->
                            window.inputEventRadioValue = event.parsedValue as? String
                            window.recordInputEvent("radio", "change", event.value, event)
                        }
                    }
                )

                text("Range")
                input(
                    InputType.Range(
                        value = window.inputEventRangeValue,
                        min = 0,
                        max = 100,
                        step = 1
                    ),
                    {
                        key = "inputEvents.range"
                        style = { width = halfWidth - 6 }
                        onFocusGain = { event: FocusGainEvent ->
                            window.recordInputEvent("range", "focus", window.inputEventRangeValue.toString(), event)
                        }
                        onFocusLose = { event: FocusLoseEvent ->
                            window.recordInputEvent("range", "blur", window.inputEventRangeValue.toString(), event)
                        }
                        onInput = { event: InputEvent ->
                            window.inputEventRangeValue = (event.parsedValue as? Long) ?: window.inputEventRangeValue
                            window.recordInputEvent("range", "input", event.value, event)
                        }
                        onValueChange = { event: ValueChangedEvent ->
                            window.inputEventRangeValue = (event.parsedValue as? Long) ?: window.inputEventRangeValue
                            window.recordInputEvent("range", "change", event.value, event)
                        }
                    }
                )
                text(
                    "Range value=${window.inputEventRangeValue}",
                    { style = { color = DEMO_MUTED } }
                )
            }
        }

        div({
            style = {
                gap = 4
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Clear Log", {
                style = { width = 62 }
                onMouseClick = { window.clearInputEventLog() }
            })
            text(
                "Entries=${window.inputEventLogEntries.size}",
                { style = { color = DEMO_MUTED } }
            )
        }

        div({
            key = "inputEvents.logPanel"
            style = {
                width = contentWidth - 8
                height = 54
                backgroundColor = DEMO_SURFACE_ALT
                padding = 3
                border(1, 0xFF6A7785.toInt())
            }
        }) {
            if (window.inputEventLogEntries.isEmpty()) {
                text("No input events yet.", { style = { color = DEMO_MUTED } })
            } else {
                window.inputEventLogEntries.forEach { line ->
                    text(line)
                }
            }
        }
    }
}