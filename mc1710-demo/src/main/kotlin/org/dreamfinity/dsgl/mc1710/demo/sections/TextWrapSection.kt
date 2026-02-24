package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.TextWrap
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private const val WRAP_SAMPLE_TEXT =
    "This sentence demonstrates style.textWrap on text and button labels inside a fixed-width panel."
private const val WRAP_SAMPLE_WORD = "long_unbroken_word_to_force_hard_break_1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val WRAP_TEXTAREA_SAMPLE =
    "Textarea sample: long_unbroken_word_to_force_hard_break_1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ\nSecond line with spaces for normal wrapping."

fun UiScope.renderTextWrapSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val minWidth = 96
    val maxWidth = (contentWidth - 8).coerceAtLeast(minWidth)
    val panelWidth = window.textWrapWidth.toInt().coerceIn(minWidth, maxWidth)
    val mode = if (window.textWrapNoWrap) TextWrap.NoWrap else TextWrap.Wrap

    div(
        ComponentProps(
            key = "section.textWrap",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("Text Wrap: wrap / nowrap"))
        text(TextProps("Wrap keeps text inside panel width; NoWrap keeps one line and may overflow or clip.").apply {
            color = DEMO_MUTED
        })

        div(ComponentProps(gap = 4).asFlexRow()) {
            button(
                ButtonProps(if (mode == TextWrap.Wrap) "Mode: wrap" else "Mode: nowrap").apply {
                    width = 82
                    onMouseClick = {
                        window.textWrapNoWrap = !window.textWrapNoWrap
                        window.appendInfo("TextWrap mode=${if (window.textWrapNoWrap) "nowrap" else "wrap"}")
                    }
                }
            )
            button(
                ButtonProps("Reset width").apply {
                    width = 62
                    onMouseClick = {
                        window.textWrapWidth = ((minWidth + maxWidth) / 2).toLong()
                    }
                }
            )
        }

        input(
            InputProps(
                InputType.Range(
                    value = panelWidth.toLong(),
                    min = minWidth.toLong(),
                    max = maxWidth.toLong(),
                    step = 2
                )
            ).apply {
                key = "textWrap.width"
                width = contentWidth - 8
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: panelWidth.toLong()
                    window.textWrapWidth = next.coerceIn(minWidth.toLong(), maxWidth.toLong())
                }
            }
        )
        text(
            TextProps {
                "panelWidth=$panelWidth mode=${if (mode == TextWrap.Wrap) "wrap" else "nowrap"}"
            }.apply { color = DEMO_MUTED }
        )

        div(
            ComponentProps(
                key = "textWrap.panel",
                width = panelWidth,
                padding = 3,
                backgroundColor = 0xFF2B3542.toInt(),
                gap = 2
            ).asFlexColumn().apply {
                style = {
                    border(1, 0xFF6F8298.toInt())
                }
            }
        ) {
            text(TextProps("Text node (static)").apply {
                style = { textWrap = mode }
            })
            text(TextProps(WRAP_SAMPLE_TEXT).apply {
                style = { textWrap = mode }
            })
            text(TextProps("Text node (lambda)"))
            text(TextProps { WRAP_SAMPLE_WORD }.apply {
                style = { textWrap = mode }
            })
            button(
                ButtonProps("Button label: $WRAP_SAMPLE_WORD").apply {
                    width = panelWidth - 6
                    style = { textWrap = mode }
                }
            )
            textarea(
                TextAreaProps("Wrap demo area").apply {
                    key = "textWrap.textarea"
                    width = panelWidth - 6
                    height = 36
                    value = WRAP_TEXTAREA_SAMPLE
                    style = { textWrap = mode }
                }
            )
        }
    }
}
