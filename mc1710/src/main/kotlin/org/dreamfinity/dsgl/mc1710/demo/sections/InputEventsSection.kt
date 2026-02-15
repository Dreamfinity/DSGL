package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DynamicTextProps
import org.dreamfinity.dsgl.core.InputProps
import org.dreamfinity.dsgl.core.TextAreaProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.FocusLoseEvent
import org.dreamfinity.dsgl.core.event.ValueChangedEvent
import org.dreamfinity.dsgl.core.event.FocusGainEvent
import org.dreamfinity.dsgl.core.event.InputEvent
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_SURFACE_ALT

fun UiScope.renderInputEventsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val halfWidth = ((contentWidth - 8) / 2).coerceAtLeast(90)

    column(
        ComponentProps(
            key = "section.inputEvents",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        )
    ) {
        text(TextProps("HTML-like events demo: onFocus/onBlur/onInput/onChange"))
        text(TextProps("Proof case: type in text field, then click elsewhere -> onInput per key, onChange on blur.").apply {
            color = DEMO_MUTED
        })

        row(ComponentProps(gap = 6)) {
            column(ComponentProps(width = halfWidth, gap = 3, key = "inputEvents.left")) {
                text(TextProps("Text input"))
                input(
                    InputProps(InputType.Text(value = window.inputEventTextValue, placeholder = "Type then blur"))
                        .apply {
                            key = "inputEvents.text"
                            width = halfWidth - 6
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

                text(TextProps("Textarea"))
                textarea(
                    TextAreaProps("Multiline event sample").apply {
                        key = "inputEvents.textarea"
                        width = halfWidth - 6
                        height = 46
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
                    }
                )
            }

            column(ComponentProps(width = halfWidth, gap = 3, key = "inputEvents.right")) {
                text(TextProps("Checkbox"))
                input(
                    InputProps(
                        InputType.Checkbox(
                            variants = window.checkboxOptions,
                            selected = window.inputEventCheckboxValue,
                            minSelected = 0,
                            maxSelected = 3
                        )
                    ).apply {
                        key = "inputEvents.checkbox"
                        width = halfWidth - 6
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

                text(TextProps("Radio"))
                input(
                    InputProps(
                        InputType.Radio(
                            variants = window.radioOptions,
                            selected = window.inputEventRadioValue
                        )
                    ).apply {
                        key = "inputEvents.radio"
                        width = halfWidth - 6
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

                text(TextProps("Range"))
                input(
                    InputProps(
                        InputType.Range(
                            value = window.inputEventRangeValue,
                            min = 0,
                            max = 100,
                            step = 1
                        )
                    ).apply {
                        key = "inputEvents.range"
                        width = halfWidth - 6
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
                dynamicText(
                    DynamicTextProps {
                        "Range value=${window.inputEventRangeValue}"
                    }.apply { color = DEMO_MUTED }
                )
            }
        }

        row(ComponentProps(gap = 4)) {
            button(
                ButtonProps("Clear Log").apply {
                    width = 62
                    onMouseClick = { window.clearInputEventLog() }
                }
            )
            dynamicText(
                DynamicTextProps {
                    "Entries=${window.inputEventLogEntries.size}"
                }.apply { color = DEMO_MUTED }
            )
        }

        div(
            ComponentProps(
                key = "inputEvents.logPanel",
                width = contentWidth - 8,
                height = 54,
                backgroundColor = DEMO_SURFACE_ALT,
                padding = 3,
                style = { border(1, 0xFF6A7785.toInt()) }
            )
        ) {
            if (window.inputEventLogEntries.isEmpty()) {
                text(TextProps("No input events yet.").apply { color = DEMO_MUTED })
            } else {
                window.inputEventLogEntries.forEach { line ->
                    text(TextProps(line))
                }
            }
        }
    }
}

