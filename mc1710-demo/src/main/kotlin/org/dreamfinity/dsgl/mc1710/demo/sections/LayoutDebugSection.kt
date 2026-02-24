package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.InputProps
import org.dreamfinity.dsgl.core.TextProps
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

fun UiScope.renderLayoutDebugSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val minWidth = 96
    val maxWidth = (contentWidth - 8).coerceAtLeast(minWidth)
    val wrapWidth = window.layoutDebugWrapWidth.toInt().coerceIn(minWidth, maxWidth)

    div(
        ComponentProps(
            key = "section.layoutDebug",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("Layout validator"))
        text(TextProps("Checks containment, invalid sizes, and wrapped text line-stack invariants.").apply {
            color = DEMO_MUTED
        })

        div(ComponentProps(gap = 4).asFlexRow()) {
            button(
                ButtonProps(if (window.layoutDebugStrict) "strict: on" else "strict: off").apply {
                    width = 64
                    onMouseClick = {
                        window.layoutDebugStrict = !window.layoutDebugStrict
                        LayoutDebug.strictBounds = window.layoutDebugStrict
                        window.appendInfo("LayoutDebug.strict=${window.layoutDebugStrict}")
                    }
                }
            )
            button(
                ButtonProps(if (window.layoutDebugDraw) "draw bounds: on" else "draw bounds: off").apply {
                    width = 86
                    onMouseClick = {
                        window.layoutDebugDraw = !window.layoutDebugDraw
                        LayoutDebug.drawBounds = window.layoutDebugDraw
                        window.appendInfo("LayoutDebug.drawBounds=${window.layoutDebugDraw}")
                    }
                }
            )
            button(
                ButtonProps("clear logs").apply {
                    width = 54
                    onMouseClick = { window.clearEventLogs() }
                }
            )
        }
        text(
            TextProps {
                "validatorViolations=${LayoutDebug.lastViolationCount} strict=${LayoutDebug.strictBounds} draw=${LayoutDebug.drawBounds}"
            }.apply { color = DEMO_MUTED }
        )

        input(
            InputProps(
                InputType.Range(
                    value = wrapWidth.toLong(),
                    min = minWidth.toLong(),
                    max = maxWidth.toLong(),
                    step = 2
                )
            ).apply {
                key = "layoutDebug.wrapWidth"
                width = contentWidth - 8
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: wrapWidth.toLong()
                    window.layoutDebugWrapWidth = next.coerceIn(minWidth.toLong(), maxWidth.toLong())
                }
            }
        )
        text(TextProps { "wrap test width=$wrapWidth" }.apply { color = DEMO_MUTED })

        div(
            ComponentProps(
                key = "layoutDebug.wrapCase",
                width = wrapWidth,
                padding = 3,
                gap = 2,
                backgroundColor = 0xFF2D3745.toInt()
            ).asFlexColumn().apply {
                style = { border(1, 0xFF70859C.toInt()) }
            }
        ) {
            text(TextProps("Case: wrapped text stack").apply { style = { textWrap = TextWrap.Wrap } })
            text(TextProps(WRAP_DEBUG_TEXT_A).apply { style = { textWrap = TextWrap.Wrap } })
            text(TextProps(WRAP_DEBUG_TEXT_B).apply { style = { textWrap = TextWrap.Wrap } })
            button(
                ButtonProps("button label wraps too: long_unbroken_word_to_force_hard_break_123456789").apply {
                    width = wrapWidth - 8
                    style = { textWrap = TextWrap.Wrap }
                }
            )
        }
    }
}
