package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.inputsGallerySection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val halfWidth = ((contentWidth - 6) / 2).coerceAtLeast(88)
    val inputWidth = (halfWidth - 6).coerceAtLeast(76)

    div({
        key = "section.inputs"
        width = contentWidth
        height = contentHeight
        gap = 4
        asFlexColumn()
    }) {
        text("All InputType variants are interactive below.")
        text(
            "Validation examples: allowed chars, min/max, step, date format.",
            { color = DEMO_MUTED }
        )

        div({ gap = 6; asFlexRow() }) {
            div({ width = halfWidth; gap = 3; key = "inputs.left"; asFlexColumn() }) {
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
                        width = inputWidth
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
                        width = inputWidth
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
                        width = inputWidth
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
                        width = inputWidth
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
                        width = inputWidth
                        onInput = { window.sharedRangeValue = it.parsedValue as? Long ?: Long.MIN_VALUE }
                        onValueChange = { window.sharedRangeValue = it.value.toLongOrNull() ?: Long.MIN_VALUE }
                    }
                )
            }

            div({ width = halfWidth; gap = 3; key = "inputs.right"; asFlexColumn() }) {
                text("Checkbox (min 1, max 2)")
                input(
                    InputType.Checkbox(
                        variants = window.checkboxOptions,
                        selected = setOf("alpha"),
                        minSelected = 1,
                        maxSelected = 2
                    ),
                    {
                        key = "input.checkbox"
                        width = inputWidth
                    }
                )

                text("Radio")
                input(
                    InputType.Radio(
                        variants = window.radioOptions,
                        selected = "center"
                    ),
                    {
                        key = "input.radio"
                        width = inputWidth
                    }
                )

                text("Date (dd.MM.yyyy HH:mm)")
                input(
                    InputType.Date(
                        value = window.openedAtForDemo,
                        zoneId = window.timeZoneForDemo
                    ), {
                        key = "input.date"
                        width = inputWidth
                    }
                )

                text("Opened: ${window.openedAtForDemo}", { color = DEMO_MUTED }
                )
            }
        }

        text("Textarea (multiline input)")
        textarea({
            placeholder = "Multiline example"
            key = "input.textarea"
            width = contentWidth - 8
            height = 40
            placeholder = "Type multiple lines"
        })

        text("Clipping + internal scrolling demo (100 lines prefilled)", { color = DEMO_MUTED })
        div({ gap = 4; key = "input.textarea.clip.demo.controls"; asFlexRow() }) {
            button("Clear Focus", { onMouseClick = { FocusManager.clearFocus() } })
            text(
                "1) Clear focus  2) wheel-scroll textarea  3) click visible text: caret must land exactly under cursor",
                { color = DEMO_MUTED }
            )
        }
        div({ gap = 4; key = "input.textarea.clip.demo.row"; asFlexRow() }) {
            div({
                key = "input.textarea.clip.left"
                width = 42
                height = 84
                backgroundColor = 0xFF5A3434.toInt()
                padding = 2
            }) {
                text("L", { color = 0xFFFFD0D0.toInt() })
            }
            textarea({
                placeholder = "Scroll with wheel / PgUp / PgDn"
                key = "input.textarea.clip"
                width = (contentWidth - 100).coerceAtLeast(90)
                height = 84
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
                width = 42
                height = 84
                backgroundColor = 0xFF345A34.toInt()
                padding = 2
            }) {
                text("R", { color = 0xFFD0FFD0.toInt() })
            }
        }
    }
}