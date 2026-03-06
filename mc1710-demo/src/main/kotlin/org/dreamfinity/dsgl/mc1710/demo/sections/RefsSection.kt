package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.ref.ElementHandle
import org.dreamfinity.dsgl.core.ref.Ref
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.refsSection(
    window: ShowcaseWindow,
    contentWidth: Int,
    contentHeight: Int,
    inputRef: Ref<ElementHandle>,
    panelRef: Ref<ElementHandle>
) {
    div({
        key = "section.refs"
        width = contentWidth
        height = contentHeight
        gap = 4
        asFlexColumn()
    }) {
        text("Refs: object refs + callback refs (commit-phase attach/detach).")
        text("Focus via ref uses ElementHandle.requestFocus().", { color = DEMO_MUTED })

        input(
            InputType.Text(
                value = window.refsInputValue,
                placeholder = "Focusable input with stable key"
            ),
            {
                key = "refs.input.primary"
                width = contentWidth - 10
                onInput = { event ->
                    window.refsInputValue = event.value
                    window.logHook("refs.input.onInput", event, "value=${event.value}")
                }
            },
            ref = inputRef
        )

        div({ gap = 4; asFlexRow() }) {
            button("Focus via ref", {
                width = 82
                onMouseClick = {
                    inputRef.current?.requestFocus()
                    window.appendInfo("Refs: requestFocus() called via object ref")
                }
            })
            button("Rebuild", {
                width = 56
                onMouseClick = {
                    window.refsRebuildCount += 1
                    window.requestManualInvalidate("refs section rebuild")
                }
            })
            button(if (window.refsCallbackMounted) "Unmount callback target" else "Mount callback target", {
                width = 136
                onMouseClick = {
                    window.refsCallbackMounted = !window.refsCallbackMounted
                    window.appendInfo("Refs: callback target mounted=${window.refsCallbackMounted}")
                }
            })
        }

        text({
            val hasRef = inputRef.current != null
            value = "objectRef.current set=$hasRef rebuilds=${window.refsRebuildCount}"
            color = DEMO_MUTED
        })

        div(
            {
                key = "refs.bounds.panel"
                width = contentWidth - 10
                height = 34
                padding = 4
                backgroundColor = 0xFF313844.toInt()
            },
            ref = panelRef
        ) {
            text("Bounds target panel", { color = 0xFFE2EAFF.toInt() })
        }

        text({
            val bounds = panelRef.current?.bounds
            value = if (bounds == null) {
                "panelRef.current: null"
            } else {
                "panelRef.bounds: x=${bounds.x} y=${bounds.y} w=${bounds.width} h=${bounds.height}"
            }
            color = DEMO_MUTED
        })

        if (window.refsCallbackMounted) {
            div(
                {
                    key = "refs.callback.target"
                    width = contentWidth - 10
                    height = 24
                    backgroundColor = 0xFF2F3C2F.toInt()
                    padding = 4
                },
                ref = window.refsCallbackRef
            ) {
                text("Callback ref target", { color = 0xFFC5E8C5.toInt() })
            }
        }

        text(
            "callback attaches=${window.refsCallbackAttachCount} detaches=${window.refsCallbackDetachCount} last=${window.refsCallbackLast}",
            { color = DEMO_MUTED }
        )
    }
}