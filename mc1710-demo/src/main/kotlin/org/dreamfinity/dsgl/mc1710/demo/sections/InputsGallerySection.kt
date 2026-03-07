package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.select.SelectRuntime
import org.dreamfinity.dsgl.core.select.SelectStyle
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.inputsGallerySection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val halfWidth = ((contentWidth - 6) / 2).coerceAtLeast(88)
    val inputWidth = (halfWidth - 6).coerceAtLeast(76)
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
            width = contentWidth.px
            height = contentHeight.px
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
                    width = halfWidth.px
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
                        style = { width = inputWidth.px }
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
                        style = { width = inputWidth.px }
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
                        key = "input.number"
                        style = { width = inputWidth.px }
                    }
                )

                text("Number 0..100 wired with slider below")
                input(
                    InputType.Number(
                        value = window.sharedRangeValue,
                        placeholder = "0..100",
                        min = 0,
                        max = 100
                    ),
                    {
                        key = "input.number"
                        style = { width = inputWidth.px }
                    }
                )

                text("Range (step 5, value=${window.sharedRangeValue})")
                input(
                    InputType.Range(
                        value = window.sharedRangeValue,
                        min = 0,
                        max = 100,
                        step = 5
                    ),
                    {
                        key = "input.range"
                        style = { width = inputWidth.px }
                        onInput = { window.sharedRangeValue = it.parsedValue as? Long ?: Long.MIN_VALUE }
                        onValueChange = { window.sharedRangeValue = it.value.toLongOrNull() ?: Long.MIN_VALUE }
                    }
                )
            }

            div({
                key = "inputs.right"
                style = {
                    width = halfWidth.px
                    gap = 3.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Checkbox (min 1, max 2)")
                input(
                    InputType.Checkbox(
                        variants = window.checkboxOptions,
                        selected = window.inputEventCheckboxValue,
                        minSelected = 1,
                        maxSelected = 2
                    ),
                    {
                        key = "input.checkbox"
                        style = { width = inputWidth.px }
                        onInput = { event ->
                            window.inputEventCheckboxValue = window.parseCheckboxSelection(event.parsedValue)
                        }
                        onValueChange = { event ->
                            window.inputEventCheckboxValue = window.parseCheckboxSelection(event.parsedValue)
                        }
                    }
                )
                text("Selected: ${window.checkboxValueString()}", { style = { color = DEMO_MUTED } })

                text("Radio")
                input(
                    InputType.Radio(
                        variants = window.radioOptions,
                        selected = window.inputEventRadioValue
                    ),
                    {
                        key = "input.radio"
                        style = { width = inputWidth.px }
                        onInput = { event ->
                            window.inputEventRadioValue = event.parsedValue as? String
                        }
                        onValueChange = { event ->
                            window.inputEventRadioValue = event.parsedValue as? String
                        }
                    }
                )
                text("Selected: ${window.inputEventRadioValue ?: "-"}", { style = { color = DEMO_MUTED } })

                text("Date (dd.MM.yyyy HH:mm)")
                input(
                    InputType.Date(
                        value = window.openedAtForDemo,
                        zoneId = window.timeZoneForDemo
                    ), {
                        key = "input.date"
                        style = { width = inputWidth.px }
                    }
                )

                text("Opened: ${window.openedAtForDemo}", { style = { color = DEMO_MUTED } }
                )
            }
        }

        text("Textarea (multiline input)")
        textarea({
            placeholder = "Multiline example"
            key = "input.textarea"
            placeholder = "Type multiple lines"
            style = {
                width = (contentWidth - 8).px
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
                    width = halfWidth.px
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Basic")
                select({
                    key = "input.select.basic"
                    value = window.selectBasicValue
                    style = { width = inputWidth.px }
                    onValueChange = { event ->
                        window.selectBasicValue = event.value
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
                    value = window.selectManyValue
                    style = { width = inputWidth.px }
                    onValueChange = { event ->
                        window.selectManyValue = event.value
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
                    width = halfWidth.px
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Disabled select")
                select({
                    key = "input.select.disabled"
                    value = window.selectDisabledValue
                    disabled = true
                    style = { width = inputWidth.px }
                    onValueChange = { event ->
                        window.selectDisabledValue = event.value
                    }
                }) {
                    option("locked", "Locked value")
                    option("alt", "Alternative")
                }

                button(
                    if (window.selectDynamicAlt) "Use option set A" else "Use option set B",
                    {
                        onMouseClick = {
                            window.selectDynamicAlt = !window.selectDynamicAlt
                        }
                    }
                )
                text("Dynamic options")
                select({
                    key = "input.select.dynamic"
                    value = window.selectDynamicValue
                    style = { width = inputWidth.px }
                    onValueChange = { event ->
                        window.selectDynamicValue = event.value
                    }
                }) {
                    placeholder("Dynamic set")
                    if (window.selectDynamicAlt) {
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
            "Select state: basic=${window.selectBasicValue ?: "-"} many=${window.selectManyValue ?: "-"} dynamic=${window.selectDynamicValue ?: "-"}",
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
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            div({
                key = "input.textarea.clip.left"
                style = {
                    width = 42.px
                    height = 84.px
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
                    width = ((contentWidth - 100).coerceAtLeast(90)).px
                    height = 84.px
                }
                value = window.clippingScrollDemoText
                onInput = { event ->
                    window.clippingScrollDemoText = event.value
                }
                onValueChange = { event ->
                    window.clippingScrollDemoText = event.value
                }
            })
            div({
                key = "input.textarea.clip.right"
                style = {
                    width = 42.px
                    height = 84.px
                    backgroundColor = 0xFF345A34.toInt()
                    padding = 2.px
                }
            }) {
                text("R", { style = { color = 0xFFD0FFD0.toInt() } })
            }
        }
    }
}
