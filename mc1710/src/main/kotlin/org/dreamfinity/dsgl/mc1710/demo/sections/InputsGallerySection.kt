package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DynamicTextProps
import org.dreamfinity.dsgl.core.InputProps
import org.dreamfinity.dsgl.core.TextAreaProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
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

                text(TextProps("Range (step 5)"))
                input(
                    InputProps(
                        InputType.Range(
                            value = 35,
                            min = 0,
                            max = 100,
                            step = 5
                        )
                    ).apply {
                        key = "input.range"
                        width = inputWidth
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
    }
}
