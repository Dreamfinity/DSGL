package org.dreamfinity.dsgl.mcForge1710.demo.sections

import org.dreamfinity.dsgl.core.dom.elements.InputOption
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.event.FocusGainEvent
import org.dreamfinity.dsgl.core.event.FocusLoseEvent
import org.dreamfinity.dsgl.core.event.InputEvent
import org.dreamfinity.dsgl.core.event.ValueChangedEvent
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_SURFACE_ALT
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val inputEventCheckboxOptions =
    listOf(
        InputOption("alpha", "Alpha"),
        InputOption("beta", "Beta"),
        InputOption("gamma", "Gamma"),
    )

private val inputEventRadioOptions =
    listOf(
        InputOption("north", "North"),
        InputOption("center", "Center"),
        InputOption("south", "South"),
    )

private val inputEventTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

fun UiScope.inputEventsSection(onLogHook: (String, Event, String?) -> Unit) {
    var inputEventTextValue by useState("")
    var inputEventTextareaValue by useState("Multiline event sample")
    var inputEventCheckboxValue by useState(setOf("alpha"))
    var inputEventRadioValue by useState<String?>("center")
    var inputEventRangeValue by useState(35L)
    var inputEventLogEntries by useState(emptyList<String>())

    fun appendInputEvent(
        control: String,
        phase: String,
        value: String,
        event: Event,
    ) {
        val time = LocalTime.now().format(inputEventTimeFormatter)
        val line = "$time $control.$phase value=$value"
        inputEventLogEntries = (listOf(line) + inputEventLogEntries).take(8)
        onLogHook("inputEvents.$control.$phase", event, "value=$value")
    }

    fun parseCheckboxSelection(parsedValue: Any?): Set<String> {
        val parsedSet = parsedValue as? Set<*>
        if (parsedSet != null) {
            return parsedSet.mapNotNull { it as? String }.toSet()
        }
        val parsedString = parsedValue as? String
        if (!parsedString.isNullOrBlank()) {
            return parsedString
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
        return emptySet()
    }

    fun checkboxValueString(): String =
        inputEventCheckboxValue
            .toList()
            .sorted()
            .joinToString(",")

    div({
        key = "section.inputEvents"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("HTML-like events demo: onFocus/onBlur/onInput/onChange")
        text(
            "Proof case: type in text field, then click elsewhere -> onInput per key, onChange on blur.",
            { style = { color = DEMO_MUTED } },
        )

        div({
            style = {
                gap = 6.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            div({
                key = "inputEvents.left"
                style = {
                    flexGrow = 1f
                    gap = 3.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Text input")
                input(InputType.Text(value = inputEventTextValue, placeholder = "Type then blur"), {
                    key = "inputEvents.text"
                    style = { width = 100.percent }
                    onFocusGain = { event: FocusGainEvent ->
                        appendInputEvent("text", "focus", inputEventTextValue, event)
                    }
                    onFocusLose = { event: FocusLoseEvent ->
                        appendInputEvent("text", "blur", inputEventTextValue, event)
                    }
                    onInput = { event: InputEvent ->
                        inputEventTextValue = event.value
                        appendInputEvent("text", "input", event.value, event)
                    }
                    onValueChange = { event: ValueChangedEvent ->
                        inputEventTextValue = event.value
                        appendInputEvent("text", "change", event.value, event)
                    }
                })

                text("Textarea")
                textarea({
                    placeholder = "Multiline event sample"
                    key = "inputEvents.textarea"
                    style = {
                        width = 100.percent
                        height = 46.px
                    }
                    value = inputEventTextareaValue
                    onFocusGain = { event: FocusGainEvent ->
                        appendInputEvent("textarea", "focus", inputEventTextareaValue, event)
                    }
                    onFocusLose = { event: FocusLoseEvent ->
                        appendInputEvent("textarea", "blur", inputEventTextareaValue, event)
                    }
                    onInput = { event: InputEvent ->
                        inputEventTextareaValue = event.value
                        appendInputEvent("textarea", "input", event.value.replace("\n", "\\n"), event)
                    }
                    onValueChange = { event: ValueChangedEvent ->
                        inputEventTextareaValue = event.value
                        appendInputEvent("textarea", "change", event.value.replace("\n", "\\n"), event)
                    }
                })
            }

            div({
                key = "inputEvents.right"
                style = {
                    flexGrow = 1f
                    gap = 3.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Checkbox")
                input(
                    InputType.Checkbox(
                        variants = inputEventCheckboxOptions,
                        selected = inputEventCheckboxValue,
                        minSelected = 0,
                        maxSelected = 3,
                    ),
                    {
                        key = "inputEvents.checkbox"
                        style = { width = 100.percent }
                        onFocusGain = { event: FocusGainEvent ->
                            appendInputEvent("checkbox", "focus", checkboxValueString(), event)
                        }
                        onFocusLose = { event: FocusLoseEvent ->
                            appendInputEvent("checkbox", "blur", checkboxValueString(), event)
                        }
                        onInput = { event: InputEvent ->
                            inputEventCheckboxValue = parseCheckboxSelection(event.parsedValue)
                            appendInputEvent("checkbox", "input", event.value, event)
                        }
                        onValueChange = { event: ValueChangedEvent ->
                            inputEventCheckboxValue = parseCheckboxSelection(event.parsedValue)
                            appendInputEvent("checkbox", "change", event.value, event)
                        }
                    },
                )

                text("Radio")
                input(
                    InputType.Radio(
                        variants = inputEventRadioOptions,
                        selected = inputEventRadioValue,
                    ),
                    {
                        key = "inputEvents.radio"
                        style = { width = 100.percent }
                        onFocusGain = { event: FocusGainEvent ->
                            appendInputEvent("radio", "focus", inputEventRadioValue ?: "", event)
                        }
                        onFocusLose = { event: FocusLoseEvent ->
                            appendInputEvent("radio", "blur", inputEventRadioValue ?: "", event)
                        }
                        onInput = { event: InputEvent ->
                            inputEventRadioValue = event.parsedValue as? String
                            appendInputEvent("radio", "input", event.value, event)
                        }
                        onValueChange = { event: ValueChangedEvent ->
                            inputEventRadioValue = event.parsedValue as? String
                            appendInputEvent("radio", "change", event.value, event)
                        }
                    },
                )

                text("Range")
                input(
                    InputType.Range(
                        value = inputEventRangeValue,
                        min = 0,
                        max = 100,
                        step = 1,
                    ),
                    {
                        key = "inputEvents.range"
                        style = { width = 100.percent }
                        onFocusGain = { event: FocusGainEvent ->
                            appendInputEvent("range", "focus", inputEventRangeValue.toString(), event)
                        }
                        onFocusLose = { event: FocusLoseEvent ->
                            appendInputEvent("range", "blur", inputEventRangeValue.toString(), event)
                        }
                        onInput = { event: InputEvent ->
                            inputEventRangeValue = (event.parsedValue as? Long) ?: inputEventRangeValue
                            appendInputEvent("range", "input", event.value, event)
                        }
                        onValueChange = { event: ValueChangedEvent ->
                            inputEventRangeValue = (event.parsedValue as? Long) ?: inputEventRangeValue
                            appendInputEvent("range", "change", event.value, event)
                        }
                    },
                )
                text(
                    "Range value=$inputEventRangeValue",
                    { style = { color = DEMO_MUTED } },
                )
            }
        }

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Clear Log", {
                onMouseClick = { inputEventLogEntries = emptyList() }
            })
            text(
                "Entries=${inputEventLogEntries.size}",
                { style = { color = DEMO_MUTED } },
            )
        }

        div({
            key = "inputEvents.logPanel"
            style = {
                width = 100.percent
                maxHeight = 25.em
                display = Display.Flex
                flexDirection = FlexDirection.Column
                overflowY = Overflow.Auto
                backgroundColor = DEMO_SURFACE_ALT
                padding = 3.px
                border {
                    width = 1.px
                    color = 0xFF6A7785.toInt()
                }
            }
        }) {
            if (inputEventLogEntries.isEmpty()) {
                text("No input events yet.", { style = { color = DEMO_MUTED } })
            } else {
                inputEventLogEntries.forEach { line ->
                    text(line)
                }
            }
        }
    }
}
