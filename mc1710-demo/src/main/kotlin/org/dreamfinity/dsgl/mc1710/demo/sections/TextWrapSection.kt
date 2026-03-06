package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.TextWrap
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private const val WRAP_SAMPLE_TEXT =
    "This sentence demonstrates style.textWrap on text and button labels inside a fixed-width panel."
private const val WRAP_SAMPLE_WORD = "long_unbroken_word_to_force_hard_break_1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val WRAP_TEXTAREA_SAMPLE =
    "Textarea sample: long_unbroken_word_to_force_hard_break_1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ\nSecond line with spaces for normal wrapping."

fun UiScope.textWrapSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val minWidth = 96
    val maxWidth = (contentWidth - 8).coerceAtLeast(minWidth)
    val panelWidth = window.textWrapWidth.toInt().coerceIn(minWidth, maxWidth)
    val mode = if (window.textWrapNoWrap) TextWrap.NoWrap else TextWrap.Wrap

    div({
        key = "section.textWrap"
        width = contentWidth
        height = contentHeight
        gap = 4
        asFlexColumn()
    }) {
        text("Text Wrap: wrap / nowrap")
        text(
            "Wrap keeps text inside panel width; NoWrap keeps one line and may overflow or clip.",
            { color = DEMO_MUTED }
        )

        div({ gap = 4; asFlexRow() }) {
            button(
                if (mode == TextWrap.Wrap) "Mode: wrap" else "Mode: nowrap",
                {
                    width = 82
                    onMouseClick = {
                        window.textWrapNoWrap = !window.textWrapNoWrap
                        window.appendInfo("TextWrap mode=${if (window.textWrapNoWrap) "nowrap" else "wrap"}")
                    }
                }
            )
            button("Reset width", {
                width = 62
                onMouseClick = {
                    window.textWrapWidth = ((minWidth + maxWidth) / 2).toLong()
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
                width = contentWidth - 8
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: panelWidth.toLong()
                    window.textWrapWidth = next.coerceIn(minWidth.toLong(), maxWidth.toLong())
                }
            }
        )
        text(
            "panelWidth=$panelWidth mode=${if (mode == TextWrap.Wrap) "wrap" else "nowrap"}",
            { color = DEMO_MUTED }
        )

        div({
            key = "textWrap.panel"
            width = panelWidth
            padding = 3
            backgroundColor = 0xFF2B3542.toInt()
            gap = 2
            style = { border(1, 0xFF6F8298.toInt()) }
            asFlexColumn()
        }) {
            text("Text node (static)", { style = { textWrap = mode } })
            text(WRAP_SAMPLE_TEXT, { style = { textWrap = mode } })
            text("Text node (lambda)")
            text(WRAP_SAMPLE_WORD, { style = { textWrap = mode } })
            button("Button label: $WRAP_SAMPLE_WORD", {
                width = panelWidth - 6
                style = { textWrap = mode }
            })
            textarea({
                placeholder = "Wrap demo area"
                key = "textWrap.textarea"
                width = panelWidth - 6
                height = 36
                value = WRAP_TEXTAREA_SAMPLE
                style = { textWrap = mode }
            })
        }
    }
}
