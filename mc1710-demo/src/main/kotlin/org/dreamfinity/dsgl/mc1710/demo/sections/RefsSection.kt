package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DynamicTextProps
import org.dreamfinity.dsgl.core.InputProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.ref.ElementHandle
import org.dreamfinity.dsgl.core.ref.Ref
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.renderRefsSection(
    window: ShowcaseWindow,
    contentWidth: Int,
    contentHeight: Int,
    inputRef: Ref<ElementHandle>,
    panelRef: Ref<ElementHandle>
) {
    column(
        ComponentProps(
            key = "section.refs",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        )
    ) {
        text(TextProps("Refs: object refs + callback refs (commit-phase attach/detach)."))
        text(TextProps("Focus via ref uses ElementHandle.requestFocus().").apply { color = DEMO_MUTED })

        input(
            InputProps(
                InputType.Text(
                    value = window.refsInputValue,
                    placeholder = "Focusable input with stable key"
                )
            ).apply {
                key = "refs.input.primary"
                width = contentWidth - 10
                onInput = { event ->
                    window.refsInputValue = event.value
                    window.logHook("refs.input.onInput", event, "value=${event.value}")
                }
            },
            ref = inputRef
        )

        row(ComponentProps(gap = 4)) {
            button(
                ButtonProps("Focus via ref").apply {
                    width = 82
                    onMouseClick = {
                        inputRef.current?.requestFocus()
                        window.appendInfo("Refs: requestFocus() called via object ref")
                    }
                }
            )
            button(
                ButtonProps("Rebuild").apply {
                    width = 56
                    onMouseClick = {
                        window.refsRebuildCount += 1
                        window.requestManualInvalidate("refs section rebuild")
                    }
                }
            )
            button(
                ButtonProps(if (window.refsCallbackMounted) "Unmount callback target" else "Mount callback target").apply {
                    width = 136
                    onMouseClick = {
                        window.refsCallbackMounted = !window.refsCallbackMounted
                        window.appendInfo("Refs: callback target mounted=${window.refsCallbackMounted}")
                    }
                }
            )
        }

        dynamicText(
            DynamicTextProps {
                val hasRef = inputRef.current != null
                "objectRef.current set=$hasRef rebuilds=${window.refsRebuildCount}"
            }.apply { color = DEMO_MUTED }
        )

        div(
            ComponentProps(
                key = "refs.bounds.panel",
                width = contentWidth - 10,
                height = 34,
                padding = 4,
                backgroundColor = 0xFF313844.toInt()
            ),
            ref = panelRef
        ) {
            text(TextProps("Bounds target panel").apply { color = 0xFFE2EAFF.toInt() })
        }

        dynamicText(
            DynamicTextProps {
                val bounds = panelRef.current?.bounds
                if (bounds == null) {
                    "panelRef.current: null"
                } else {
                    "panelRef.bounds: x=${bounds.x} y=${bounds.y} w=${bounds.width} h=${bounds.height}"
                }
            }.apply { color = DEMO_MUTED }
        )

        if (window.refsCallbackMounted) {
            div(
                ComponentProps(
                    key = "refs.callback.target",
                    width = contentWidth - 10,
                    height = 24,
                    backgroundColor = 0xFF2F3C2F.toInt(),
                    padding = 4
                ),
                ref = window.refsCallbackRef
            ) {
                text(TextProps("Callback ref target").apply { color = 0xFFC5E8C5.toInt() })
            }
        }

        dynamicText(
            DynamicTextProps {
                "callback attaches=${window.refsCallbackAttachCount} detaches=${window.refsCallbackDetachCount} last=${window.refsCallbackLast}"
            }.apply { color = DEMO_MUTED }
        )
    }
}
