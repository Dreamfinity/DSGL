package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.debug.LayoutDebug
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.TextWrap
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private const val WRAP_DEBUG_TEXT_A =
    "Wrapped text A: if line-height or measured-height is wrong, this line stack will collide with text B."
private const val WRAP_DEBUG_TEXT_B =
    "Wrapped text B: this block should always appear below text A with no overlap."

fun UiScope.layoutDebugSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val minWidth = 96
    val maxWidth = (contentWidth - 8).coerceAtLeast(minWidth)
    val wrapWidth = window.layoutDebugWrapWidth.toInt().coerceIn(minWidth, maxWidth)

    div({
        key = "section.layoutDebug"
        width = contentWidth
        height = contentHeight
        gap = 4
        asFlexColumn()
    }) {
        text("Layout validator")
        text("Checks containment, invalid sizes, and wrapped text line-stack invariants.", {
            color = DEMO_MUTED
        })

        div({ gap = 4; asFlexRow() }) {
            button(
                if (window.layoutDebugStrict) "strict: on" else "strict: off",
                {
                    width = 64
                    onMouseClick = {
                        window.layoutDebugStrict = !window.layoutDebugStrict
                        LayoutDebug.strictBounds = window.layoutDebugStrict
                        window.appendInfo("LayoutDebug.strict=${window.layoutDebugStrict}")
                    }
                }
            )
            button(
                if (window.layoutDebugDraw) "draw bounds: on" else "draw bounds: off",
                {
                    width = 86
                    onMouseClick = {
                        window.layoutDebugDraw = !window.layoutDebugDraw
                        LayoutDebug.drawBounds = window.layoutDebugDraw
                        window.appendInfo("LayoutDebug.drawBounds=${window.layoutDebugDraw}")
                    }
                }
            )
            button("clear logs", {
                width = 54
                onMouseClick = { window.clearEventLogs() }
            })
        }
        text(
            "validatorViolations=${LayoutDebug.lastViolationCount} strict=${LayoutDebug.strictBounds} draw=${LayoutDebug.drawBounds}",
            { color = DEMO_MUTED }
        )

        input(
            InputType.Range(
                value = wrapWidth.toLong(),
                min = minWidth.toLong(),
                max = maxWidth.toLong(),
                step = 2
            ),
            {
                key = "layoutDebug.wrapWidth"
                width = contentWidth - 8
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: wrapWidth.toLong()
                    window.layoutDebugWrapWidth = next.coerceIn(minWidth.toLong(), maxWidth.toLong())
                }
            }
        )
        text("wrap test width=$wrapWidth", { color = DEMO_MUTED })

        div({
            key = "layoutDebug.wrapCase"
            width = wrapWidth
            padding = 3
            gap = 2
            backgroundColor = 0xFF2D3745.toInt()
            style = { border(1, 0xFF70859C.toInt()) }
            asFlexColumn()
        }) {
            text("Case: wrapped text stack", { style = { textWrap = TextWrap.Wrap } })
            text(WRAP_DEBUG_TEXT_A, { style = { textWrap = TextWrap.Wrap } })
            text(WRAP_DEBUG_TEXT_B, { style = { textWrap = TextWrap.Wrap } })
            button("button label wraps too: long_unbroken_word_to_force_hard_break_123456789", {
                width = wrapWidth - 8
                style = { textWrap = TextWrap.Wrap }
            })
        }
    }
}
