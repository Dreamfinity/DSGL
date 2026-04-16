package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.dom.elements.InputOption
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.select.SelectRuntime
import org.dreamfinity.dsgl.core.select.SelectStyle
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import java.time.Instant
import java.time.ZoneId

private val inputsCheckboxOptions = listOf(
    InputOption("alpha", "Alpha"),
    InputOption("beta", "Beta"),
    InputOption("gamma", "Gamma")
)

private val inputsRadioOptions = listOf(
    InputOption("north", "North"),
    InputOption("center", "Center"),
    InputOption("south", "South")
)

fun UiScope.inputsGallerySection(
    clippingScrollDemoText: String,
    onClippingScrollDemoTextChange: (String) -> Unit
) {
    var openedAt by useState(Instant.now())
    var timeZoneId by useState(ZoneId.systemDefault())
    var sharedRangeValue by useState(35L)
    var inputCheckboxValue by useState(setOf("alpha"))
    var inputRadioValue by useState<String?>("center")
    var selectBasicValue by useState<String?>(null)
    var selectManyValue by useState<String?>("item-05")
    var selectDisabledValue by useState<String?>("locked")
    var selectDynamicValue by useState<String?>("alpha")
    var selectDynamicAlt by useState(false)
    var toggleBasicValue by useState(false)
    var toggleSecondaryValue by useState(true)

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

    fun checkboxValueString(): String = inputCheckboxValue.toList().sorted().joinToString(",")

    SelectRuntime.engine.setStyle(
        SelectStyle(
            panelBackgroundColor = 0xFF202A35.toInt(),
            panelBorderColor = 0xFF607286.toInt(),
            panelShadowColor = 0x70101926,
            optionHoverBackgroundColor = 0xFF33506B.toInt(),
            optionSelectedBackgroundColor = 0xFF2A4258.toInt(),
            groupTextColor = 0xFFB7C6D6.toInt(),
            openDurationMs = 120L,
            closeDurationMs = 90L
        )
    )

    div({
        key = "section.inputs"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("All InputType variants are interactive below.")
        text(
            "Validation examples: allowed chars, min/max, step, date format.",
            { style = { color = DEMO_MUTED } }
        )

        div({
            style = {
                gap = 6.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            div({
                key = "inputs.left"
                style = {
                    flexGrow = 1f
                    gap = 3.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Text (A-F/0-9, max 8)")
                input(
                    InputType.Text(
                        value = "A1",
                        placeholder = "hex",
                        allowedChars = "0123456789ABCDEF",
                        maxLength = 8
                    ),
                    {
                        key = "input.text"
                        style = { width = 100.percent }
                    }
                )

                text("Password (max 12)")
                input(
                    InputType.Password(
                        value = "",
                        placeholder = "secret",
                        maxLength = 12
                    ),
                    {
                        key = "input.password"
                        style = { width = 100.percent }
                    }
                )

                text("Number (10..20, wheel when focused)")
                input(
                    InputType.Number(
                        value = 15,
                        placeholder = "10..20",
                        min = 10,
                        max = 20
                    ),
                    {
                        key = "input.number.basic"
                        style = { width = 100.percent }
                    }
                )

                text("Number 0..100 wired with slider below")
                input(
                    InputType.Number(
                        value = sharedRangeValue,
                        placeholder = "0..100",
                        min = 0,
                        max = 100
                    ),
                    {
                        key = "input.number.shared"
                        style = { width = 100.percent }
                        onInput = { event ->
                            sharedRangeValue = (event.parsedValue as? Long) ?: sharedRangeValue
                        }
                        onValueChange = { event ->
                            sharedRangeValue = event.value.toLongOrNull() ?: sharedRangeValue
                        }
                    }
                )

                text("Range (step 5, value=$sharedRangeValue)")
                input(
                    InputType.Range(
                        value = sharedRangeValue,
                        min = 0,
                        max = 100,
                        step = 5
                    ),
                    {
                        key = "input.range"
                        style = { width = 100.percent }
                        onInput = { event ->
                            sharedRangeValue = (event.parsedValue as? Long) ?: sharedRangeValue
                        }
                        onValueChange = { event ->
                            sharedRangeValue = event.value.toLongOrNull() ?: sharedRangeValue
                        }
                    }
                )
            }

            div({
                key = "inputs.right"
                style = {
                    flexGrow = 1f
                    gap = 3.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Checkbox (min 1, max 2)")
                input(
                    InputType.Checkbox(
                        variants = inputsCheckboxOptions,
                        selected = inputCheckboxValue,
                        minSelected = 1,
                        maxSelected = 2
                    ),
                    {
                        key = "input.checkbox"
                        style = { width = 100.percent }
                        onInput = { event ->
                            inputCheckboxValue = parseCheckboxSelection(event.parsedValue)
                        }
                        onValueChange = { event ->
                            inputCheckboxValue = parseCheckboxSelection(event.parsedValue)
                        }
                    }
                )
                text("Selected: ${checkboxValueString()}", { style = { color = DEMO_MUTED } })

                text("Radio")
                input(
                    InputType.Radio(
                        variants = inputsRadioOptions,
                        selected = inputRadioValue
                    ),
                    {
                        key = "input.radio"
                        style = { width = 100.percent }
                        onInput = { event ->
                            inputRadioValue = event.parsedValue as? String
                        }
                        onValueChange = { event ->
                            inputRadioValue = event.parsedValue as? String
                        }
                    }
                )
                text("Selected: ${inputRadioValue ?: "-"}", { style = { color = DEMO_MUTED } })

                text("Toggle (iOS-like)")
                div({
                    key = "inputs.toggle.row"
                    style = {
                        display = Display.Flex
                        flexDirection = FlexDirection.Row
                        gap = 6.px
                    }
                }) {
                    toggle({
                        key = "input.toggle.basic"
                        checked = toggleBasicValue
                        onValueChange = { event ->
                            toggleBasicValue = event.parsedValue as? Boolean ?: false
                        }
                    })
                    text(if (toggleBasicValue) "On" else "Off", { style = { color = DEMO_MUTED } })
                }

                text("Disabled toggle")
                toggle({
                    key = "input.toggle.disabled"
                    checked = toggleSecondaryValue
                    disabled = true
                })

                text("Date (dd.MM.yyyy HH:mm)")
                input(
                    InputType.Date(
                        value = openedAt,
                        zoneId = timeZoneId
                    ),
                    {
                        key = "input.date"
                        style = { width = 100.percent }
                    }
                )

                text("Opened: $openedAt", { style = { color = DEMO_MUTED } })
            }
        }

        text("Textarea (multiline input)")
        textarea({
            key = "input.textarea"
            placeholder = "Type multiple lines"
            style = {
                width = 100.percent
                height = 40.px
            }
        })

        text("Select (overlay popup + keyboard + disabled options)")
        text(
            "Use Enter/Space/ArrowDown when focused. Esc closes popup. Wheel scrolls long list.",
            { style = { color = DEMO_MUTED } }
        )

        div({
            key = "inputs.select.row"
            style = {
                gap = 6.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            div({
                key = "inputs.select.left"
                style = {
                    flexGrow = 1f
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Basic")
                select({
                    key = "input.select.basic"
                    value = selectBasicValue
                    style = { width = 100.percent }
                    onValueChange = { event ->
                        selectBasicValue = event.value
                    }
                }) {
                    placeholder("Choose a fruit")
                    option("apple", "Apple")
                    option("banana", "Banana")
                    separator("sep-1")
                    group("Citrus") {
                        option("orange", "Orange")
                        option("lemon", "Lemon")
                        option("pomelo", "Pomelo") { enabled(false) }
                    }
                }

                text("Many options (scroll)")
                select({
                    key = "input.select.many"
                    value = selectManyValue
                    style = {
                        maxHeight = 15.em
                        width = 100.percent
                    }
                    onValueChange = { event ->
                        selectManyValue = event.value
                    }
                }) {
                    placeholder("Pick one")
                    repeat(100) { index ->
                        val id = "item-${index.toString().padStart(2, '0')}"
                        option(id, "Item ${index.toString().padStart(2, '0')}") {
                            enabled(index % 9 != 0)
                        }
                    }
                }
            }

            div({
                key = "inputs.select.right"
                style = {
                    flexGrow = 1f
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Disabled select")
                select({
                    key = "input.select.disabled"
                    value = selectDisabledValue
                    disabled = true
                    style = { width = 100.percent }
                    onValueChange = { event ->
                        selectDisabledValue = event.value
                    }
                }) {
                    option("locked", "Locked value")
                    option("alt", "Alternative")
                }

                button(
                    if (selectDynamicAlt) "Use option set A" else "Use option set B",
                    {
                        onMouseClick = {
                            selectDynamicAlt = !selectDynamicAlt
                        }
                    }
                )
                text("Dynamic options")
                select({
                    key = "input.select.dynamic"
                    value = selectDynamicValue
                    style = { width = 100.percent }
                    onValueChange = { event ->
                        selectDynamicValue = event.value
                    }
                }) {
                    placeholder("Dynamic set")
                    if (selectDynamicAlt) {
                        option("alpha", "Alpha")
                        option("beta", "Beta")
                        option("gamma", "Gamma")
                    } else {
                        option("delta", "Delta")
                        option("epsilon", "Epsilon")
                        option("theta", "Theta")
                    }
                }
            }
        }
        text(
            "Select state: basic=${selectBasicValue ?: "-"} many=${selectManyValue ?: "-"} dynamic=${selectDynamicValue ?: "-"}",
            { style = { color = DEMO_MUTED } }
        )

        text("Clipping + internal scrolling demo (100 lines prefilled)", { style = { color = DEMO_MUTED } })
        div({
            key = "input.textarea.clip.demo.controls"
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Clear Focus", { onMouseClick = { FocusManager.clearFocus() } })
            text(
                "1) Clear focus  2) wheel-scroll textarea  3) click visible text: caret must land exactly under cursor",
                { style = { color = DEMO_MUTED } }
            )
        }
        div({
            key = "input.textarea.clip.demo.row"
            style = {
                gap = 4.px
                height = 8.em
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            div({
                key = "input.textarea.clip.left"
                style = {
                    width = 42.px
                    height = 100.percent
                    backgroundColor = 0xFF5A3434.toInt()
                    padding = 2.px
                }
            }) {
                text("L", { style = { color = 0xFFFFD0D0.toInt() } })
            }
            textarea({
                placeholder = "Scroll with wheel / PgUp / PgDn"
                key = "input.textarea.clip"
                style = {
                    flexGrow = 1f
                }
                value = clippingScrollDemoText
                onInput = { event ->
                    onClippingScrollDemoTextChange(event.value)
                }
                onValueChange = { event ->
                    onClippingScrollDemoTextChange(event.value)
                }
            })
            div({
                key = "input.textarea.clip.right"
                style = {
                    width = 42.px
                    height = 100.percent
                    backgroundColor = 0xFF345A34.toInt()
                    padding = 2.px
                }
            }) {
                text("R", { style = { color = 0xFFD0FFD0.toInt() } })
            }
        }
    }
}
