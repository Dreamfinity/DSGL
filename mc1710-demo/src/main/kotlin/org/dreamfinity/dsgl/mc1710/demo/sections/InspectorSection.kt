package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.inspectorSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.inspector"
        width = contentWidth
        height = contentHeight
        gap = 4
        asFlexColumn()
    }) {
        text("In-game Inspector is global (works on every DSGL screen).")
        text("F8: toggle inspector overlay", { color = DEMO_MUTED })
        text("F9: switch mode (Pick/Locked)", { color = DEMO_MUTED })
        text("Expanded panel: click Min to collapse into floating chip.", { color = DEMO_MUTED })
        text("Minimized chip: drag to move, click (no drag) to restore.", { color = DEMO_MUTED })
        text("Expanded panel: drag header to move; drag edges/corners to resize.", { color = DEMO_MUTED })
        text("Pick mode captures clicks for selection; Locked mode blocks input in inspector rect.", {
            color = DEMO_MUTED
        })
        text("Click-through check: clicking inspector must NOT increment the counter below.", {
            color = DEMO_MUTED
        })

        div({
            key = "inspector.sample.panel"
            gap = 4
            padding = 4
            backgroundColor = 0x3338424F
            style = { border(1, 0xFF5F6E80.toInt()) }
            asFlexColumn()
        }) {
            text("Sample subtree for inspection (hover/click with inspector ON).")
            text("Behind-inspector click counter: ${window.inspectorBehindClickCounter}", {
                color = 0xFFB6D7A8.toInt()
            })
            div({ gap = 4; asFlexRow() }) {
                button("Behind button (+1)", {
                    onMouseClick = {
                        window.inspectorBehindClickCounter += 1
                        window.appendInfo("Inspector sample: behind counter=${window.inspectorBehindClickCounter}")
                    }
                })
                input(
                    InputType.Text(window.focusStableValue, "Focusable input"),
                    {
                        width = 116
                        key = "inspector.sample.input"
                        onInput = { event ->
                            window.focusStableValue = event.value
                        }
                    }
                )
            }
            div({
                key = "inspector.sample.grid"
                gap = 3
                style = {
                    display = Display.Grid
                    gridColumns = 3
                }
            }) {
                repeat(6) { index ->
                    div({
                        key = "inspector.sample.cell.$index"
                        padding = 2
                        backgroundColor = 0x22496699
                        style = {
                            border(1, 0x665A9CE0)
                        }

                    }) {
                        text("cell-$index")
                    }
                }
            }
        }
    }
}
