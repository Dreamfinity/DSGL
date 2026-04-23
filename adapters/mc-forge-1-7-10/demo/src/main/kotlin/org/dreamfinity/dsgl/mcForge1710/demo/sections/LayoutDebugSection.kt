package org.dreamfinity.dsgl.mcForge1710.demo.sections

import org.dreamfinity.dsgl.core.dom.debug.LayoutDebug
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.TextWrap
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_MUTED

private const val WRAP_DEBUG_TEXT_A =
    "Wrapped text A: if line-height or measured-height is wrong, this line stack will collide with text B."
private const val WRAP_DEBUG_TEXT_B =
    "Wrapped text B: this block should always appear below text A with no overlap."

fun UiScope.layoutDebugSection(onClearLogs: () -> Unit, onInfo: (String) -> Unit) {
    val minWidth = 96
    val maxWidth = 320
    var layoutDebugStrict by useState(LayoutDebug.strictBounds)
    var layoutDebugDraw by useState(LayoutDebug.drawBounds)
    var layoutDebugWrapWidth by useState(148L)
    val wrapWidth = layoutDebugWrapWidth.toInt().coerceIn(minWidth, maxWidth)

    div({
        key = "section.layoutDebug"
        style = {
            gap = 4.px

            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Layout validator")
        text("Checks containment, invalid sizes, and wrapped text line-stack invariants.", {
            style = { color = DEMO_MUTED }
        })

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(
                if (layoutDebugStrict) "strict: on" else "strict: off",
                {
                    onMouseClick = {
                        layoutDebugStrict = !layoutDebugStrict
                        LayoutDebug.strictBounds = layoutDebugStrict
                        onInfo("LayoutDebug.strict=$layoutDebugStrict")
                    }
                },
            )
            button(
                if (layoutDebugDraw) "draw bounds: on" else "draw bounds: off",
                {
                    onMouseClick = {
                        layoutDebugDraw = !layoutDebugDraw
                        LayoutDebug.drawBounds = layoutDebugDraw
                        onInfo("LayoutDebug.drawBounds=$layoutDebugDraw")
                    }
                },
            )
            button("clear logs", {
                onMouseClick = { onClearLogs() }
            })
        }
        text(
            "validatorViolations=${LayoutDebug.lastViolationCount} strict=${LayoutDebug.strictBounds} draw=${LayoutDebug.drawBounds}",
            { style = { color = DEMO_MUTED } },
        )

        input(
            InputType.Range(
                value = wrapWidth.toLong(),
                min = minWidth.toLong(),
                max = maxWidth.toLong(),
                step = 2,
            ),
            {
                key = "layoutDebug.wrapWidth"
                style = { width = 100.percent }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: wrapWidth.toLong()
                    layoutDebugWrapWidth = next.coerceIn(minWidth.toLong(), maxWidth.toLong())
                }
            },
        )
        text("wrap test width=$wrapWidth", { style = { color = DEMO_MUTED } })

        div({
            key = "layoutDebug.wrapCase"
            style = {
                width = wrapWidth.px
                padding = 3.px
                gap = 2.px
                backgroundColor = 0xFF2D3745.toInt()
                display = Display.Flex
                flexDirection = FlexDirection.Column
                border {
                    width = 1.px
                    color = 0xFF70859C.toInt()
                }
            }
        }) {
            text("Case: wrapped text stack", { style = { textWrap = TextWrap.Wrap } })
            text(WRAP_DEBUG_TEXT_A, { style = { textWrap = TextWrap.Wrap } })
            text(WRAP_DEBUG_TEXT_B, { style = { textWrap = TextWrap.Wrap } })
            button("button label wraps too: long_unbroken_word_to_force_hard_break_123456789", {
                style = {
                    width = 100.percent
                    textWrap = TextWrap.Wrap
                }
            })
        }
    }
}
