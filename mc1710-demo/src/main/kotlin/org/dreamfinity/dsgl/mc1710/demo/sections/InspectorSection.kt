package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.renderInspectorSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div(
        ComponentProps(
            key = "section.inspector",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("In-game Inspector is global (works on every DSGL screen)."))
        text(TextProps("F8: toggle inspector overlay").apply { color = DEMO_MUTED })
        text(TextProps("F9: switch mode (Pick/Locked)").apply { color = DEMO_MUTED })
        text(TextProps("Expanded panel: click Min to collapse into floating chip.").apply { color = DEMO_MUTED })
        text(TextProps("Minimized chip: drag to move, click (no drag) to restore.").apply { color = DEMO_MUTED })
        text(TextProps("Expanded panel: drag header to move; drag edges/corners to resize.").apply { color = DEMO_MUTED })
        text(TextProps("Pick mode captures clicks for selection; Locked mode blocks input in inspector rect.").apply {
            color = DEMO_MUTED
        })
        text(TextProps("Click-through check: clicking inspector must NOT increment the counter below.").apply {
            color = DEMO_MUTED
        })

        div(
            ComponentProps(
                key = "inspector.sample.panel",
                gap = 4,
                padding = 4,
                backgroundColor = 0x3338424F,
                style = {
                    border(1, 0xFF5F6E80.toInt())
                }
            ).asFlexColumn()
        ) {
            text(TextProps("Sample subtree for inspection (hover/click with inspector ON)."))
            text(TextProps("Behind-inspector click counter: ${window.inspectorBehindClickCounter}").apply {
                color = 0xFFB6D7A8.toInt()
            })
            div(ComponentProps(gap = 4).asFlexRow()) {
                button(
                    ButtonProps("Behind button (+1)").apply {
                        onMouseClick = {
                            window.inspectorBehindClickCounter += 1
                            window.appendInfo("Inspector sample: behind counter=${window.inspectorBehindClickCounter}")
                        }
                    }
                )
                input(
                    InputProps(InputType.Text(window.focusStableValue, "Focusable input")).apply {
                        width = 116
                        key = "inspector.sample.input"
                        onInput = { event ->
                            window.focusStableValue = event.value
                        }
                    }
                )
            }
            div(
                ComponentProps(
                    key = "inspector.sample.grid",
                    gap = 3,
                    style = {
                        display = org.dreamfinity.dsgl.core.style.Display.Grid
                        gridColumns = 3
                    }
                )
            ) {
                repeat(6) { index ->
                    div(
                        ComponentProps(
                            key = "inspector.sample.cell.$index",
                            padding = 2,
                            backgroundColor = 0x22496699,
                            style = {
                                border(1, 0x665A9CE0)
                            }
                        )
                    ) {
                        text(TextProps("cell-$index"))
                    }
                }
            }
        }
    }
}
