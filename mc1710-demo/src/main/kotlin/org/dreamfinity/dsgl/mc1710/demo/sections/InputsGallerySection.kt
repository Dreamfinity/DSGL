package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.renderInputsGallerySection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val halfWidth = ((contentWidth - 6) / 2).coerceAtLeast(88)
    val inputWidth = (halfWidth - 6).coerceAtLeast(76)

    column(
        ComponentProps(
            key = "section.inputs",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        )
    ) {
        text(TextProps("All InputType variants are interactive below."))
        text(TextProps("Validation examples: allowed chars, min/max, step, date format.").apply {
            color = DEMO_MUTED
        })

        row(ComponentProps(gap = 6)) {
            column(ComponentProps(width = halfWidth, gap = 3, key = "inputs.left")) {
                text(TextProps("Text (A-F/0-9, max 8)"))
                input(
                    InputProps(
                        InputType.Text(
                            value = "A1",
                            placeholder = "hex",
                            allowedChars = "0123456789ABCDEF",
                            maxLength = 8
                        )
                    ).apply {
                        key = "input.text"
                        width = inputWidth
                    }
                )

                text(TextProps("Password (max 12)"))
                input(
                    InputProps(
                        InputType.Password(
                            value = "",
                            placeholder = "secret",
                            maxLength = 12
                        )
                    ).apply {
                        key = "input.password"
                        width = inputWidth
                    }
                )

                text(TextProps("Number (10..20, wheel when focused)"))
                input(
                    InputProps(
                        InputType.Number(
                            value = 15,
                            placeholder = "10..20",
                            min = 10,
                            max = 20
                        )
                    ).apply {
                        key = "input.number"
                        width = inputWidth
                    }
                )

                text(TextProps("Number 0..100 wired with slider below"))
                input(
                    InputProps(
                        InputType.Number(
                            value = window.sharedRangeValue,
                            placeholder = "0..100",
                            min = 0,
                            max = 100
                        )
                    ).apply {
                        key = "input.number"
                        width = inputWidth
                    }
                )

                dynamicText(DynamicTextProps { "Range (step 5, value=${window.sharedRangeValue})" })
                input(
                    InputProps(
                        InputType.Range(
                            value = window.sharedRangeValue,
                            min = 0,
                            max = 100,
                            step = 5
                        )
                    ).apply {
                        key = "input.range"
                        width = inputWidth
                        onInput = { window.sharedRangeValue = it.parsedValue as? Long ?: Long.MIN_VALUE }
                        onValueChange = { window.sharedRangeValue = it.value.toLongOrNull() ?: Long.MIN_VALUE }
                    }
                )
            }

            column(ComponentProps(width = halfWidth, gap = 3, key = "inputs.right")) {
                text(TextProps("Checkbox (min 1, max 2)"))
                input(
                    InputProps(
                        InputType.Checkbox(
                            variants = window.checkboxOptions,
                            selected = setOf("alpha"),
                            minSelected = 1,
                            maxSelected = 2
                        )
                    ).apply {
                        key = "input.checkbox"
                        width = inputWidth
                    }
                )

                text(TextProps("Radio"))
                input(
                    InputProps(
                        InputType.Radio(
                            variants = window.radioOptions,
                            selected = "center"
                        )
                    ).apply {
                        key = "input.radio"
                        width = inputWidth
                    }
                )

                text(TextProps("Date (dd.MM.yyyy HH:mm)"))
                input(
                    InputProps(
                        InputType.Date(
                            value = window.openedAtForDemo,
                            zoneId = window.timeZoneForDemo
                        )
                    ).apply {
                        key = "input.date"
                        width = inputWidth
                    }
                )

                dynamicText(
                    DynamicTextProps {
                        "Opened: ${window.openedAtForDemo}"
                    }.apply { color = DEMO_MUTED }
                )
            }
        }

        text(TextProps("Textarea (multiline input)"))
        textarea(
            TextAreaProps("Multiline example")
                .apply {
                    key = "input.textarea"
                    width = contentWidth - 8
                    height = 40
                    placeholder = "Type multiple lines"
                }
        )

        text(TextProps("Clipping + internal scrolling demo (100 lines prefilled)").apply { color = DEMO_MUTED })
        row(ComponentProps(gap = 4, key = "input.textarea.clip.demo.controls")) {
            button(ButtonProps("Clear Focus")) {
                onClick { FocusManager.clearFocus() }
            }
            text(
                TextProps("1) Clear focus  2) wheel-scroll textarea  3) click visible text: caret must land exactly under cursor")
                    .apply { color = DEMO_MUTED }
            )
        }
        row(ComponentProps(gap = 4, key = "input.textarea.clip.demo.row")) {
            div(
                ComponentProps(
                    key = "input.textarea.clip.left",
                    width = 42,
                    height = 84,
                    backgroundColor = 0xFF5A3434.toInt(),
                    padding = 2
                )
            ) {
                text(TextProps("L").apply { color = 0xFFFFD0D0.toInt() })
            }
            textarea(
                TextAreaProps("Scroll with wheel / PgUp / PgDn").apply {
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
                }
            )
            div(
                ComponentProps(
                    key = "input.textarea.clip.right",
                    width = 42,
                    height = 84,
                    backgroundColor = 0xFF345A34.toInt(),
                    padding = 2
                )
            ) {
                text(TextProps("R").apply { color = 0xFFD0FFD0.toInt() })
            }
        }
    }
}
