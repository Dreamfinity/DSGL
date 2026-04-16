package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.TextWrap
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private const val WRAP_SAMPLE_TEXT =
    "This sentence demonstrates style.textWrap on text and button labels inside a fixed-width panel."
private const val WRAP_SAMPLE_WORD = "long_unbroken_word_to_force_hard_break_1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val WRAP_TEXTAREA_SAMPLE =
    "Textarea sample: long_unbroken_word_to_force_hard_break_1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ\nSecond line with spaces for normal wrapping."

fun UiScope.textWrapSection(onInfo: (String) -> Unit) {
    val minWidth = 96
    val maxWidth = 320
    var textWrapNoWrap by useState(false)
    var textWrapWidth by useState(176L)
    val panelWidth = textWrapWidth.toInt().coerceIn(minWidth, maxWidth)
    val mode = if (textWrapNoWrap) TextWrap.NoWrap else TextWrap.Wrap

    div({
        key = "section.textWrap"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Text Wrap: wrap / nowrap")
        text(
            "Wrap keeps text inside panel width; NoWrap keeps one line and may overflow or clip.",
            { style = { color = DEMO_MUTED } }
        )

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(
                if (mode == TextWrap.Wrap) "Mode: wrap" else "Mode: nowrap",
                {
                    onMouseClick = {
                        textWrapNoWrap = !textWrapNoWrap
                        onInfo("TextWrap mode=${if (textWrapNoWrap) "nowrap" else "wrap"}")
                    }
                }
            )
            button("Reset width", {
                onMouseClick = {
                    textWrapWidth = ((minWidth + maxWidth) / 2).toLong()
                }
            })
        }

        input(
            InputType.Range(
                value = panelWidth.toLong(),
                min = minWidth.toLong(),
                max = maxWidth.toLong(),
                step = 2
            ),
            {
                key = "textWrap.width"
                style = { width = 100.percent }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: panelWidth.toLong()
                    textWrapWidth = next.coerceIn(minWidth.toLong(), maxWidth.toLong())
                }
            }
        )
        text(
            "panelWidth=$panelWidth mode=${if (mode == TextWrap.Wrap) "wrap" else "nowrap"}",
            { style = { color = DEMO_MUTED } }
        )

        div({
            key = "textWrap.panel"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Column
                width = panelWidth.px
                padding = 3.px
                backgroundColor = 0xFF2B3542.toInt()
                gap = 2.px
                border(1.px, 0xFF6F8298.toInt())
            }

        }) {
            text("Text node (static)", { style = { textWrap = mode } })
            text(WRAP_SAMPLE_TEXT, { style = { textWrap = mode } })
            text("Text node (lambda)")
            text(WRAP_SAMPLE_WORD, { style = { textWrap = mode } })
            button("Button label: $WRAP_SAMPLE_WORD", {
                style = {
                    width = 100.percent
                    textWrap = mode
                }
            })
            textarea({
                placeholder = "Wrap demo area"
                key = "textWrap.textarea"
                value = WRAP_TEXTAREA_SAMPLE
                style = {
                    width = 100.percent
                    height = 36.px
                    textWrap = mode
                }
            })
        }
    }
}


