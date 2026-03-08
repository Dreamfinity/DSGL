package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow

private const val INSPECTOR_MUTED_TEXT: Int = 0xFFB0B7C1.toInt()

fun UiScope.inspectorSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.inspector"
        style = {
            width = contentWidth.px
            height = contentHeight.px
            gap = 4.px

            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("In-game Inspector is global (works on every DSGL screen).")
        text("F8: toggle inspector overlay", { style = { color = INSPECTOR_MUTED_TEXT } })
        text("F9: switch mode (Pick/Locked)", { style = { color = INSPECTOR_MUTED_TEXT } })
        text("Expanded panel: click Min to collapse into floating chip.", { style = { color = INSPECTOR_MUTED_TEXT } })
        text("Minimized chip: drag to move, click (no drag) to restore.", { style = { color = INSPECTOR_MUTED_TEXT } })
        text("Expanded panel: drag header to move; drag edges/corners to resize.", { style = { color = INSPECTOR_MUTED_TEXT } })
        text("Style editor now uses typed controls: dropdowns, text inputs, numeric input + units.", {
            style = { color = INSPECTOR_MUTED_TEXT }
        })
        text("Hover var(--token) values in style overrides to preview resolved values.", {
            style = { color = INSPECTOR_MUTED_TEXT }
        })
        text("Pick mode captures clicks for selection; Locked mode blocks input in inspector rect.", {
            style = { color = INSPECTOR_MUTED_TEXT }
        })
        text("Click-through check: clicking inspector must NOT increment the counter below.", {
            style = { color = INSPECTOR_MUTED_TEXT }
        })

        div({
            key = "inspector.sample.panel"
            style = {
                gap = 4.px
                padding = 4.px
                backgroundColor = 0x3338424F
                border(1.px, 0xFF5F6E80.toInt())
                display = Display.Flex
                flexDirection = FlexDirection.Column
            }

        }) {
            text("Sample subtree for inspection (hover/click with inspector ON).")
            text("Behind-inspector click counter: ${window.inspectorBehindClickCounter}", {
                style = { color = 0xFFB6D7A8.toInt() }
            })
            div({
                style = {
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                button("Behind button (+1)", {
                    onMouseClick = {
                        window.inspectorBehindClickCounter += 1
                        window.appendInfo("Inspector sample: behind counter=${window.inspectorBehindClickCounter}")
                    }
                })
                input(
                    InputType.Text(window.focusStableValue, "Focusable input"),
                    {
                        style = { width = 116.px }
                        key = "inspector.sample.input"
                        onInput = { event ->
                            window.focusStableValue = event.value
                        }
                    }
                )
            }
            div({
                key = "inspector.sample.grid"
                style = {
                    gap = 3.px
                    display = Display.Grid
                    gridColumns = 3
                }
            }) {
                repeat(6) { index ->
                    div({
                        key = "inspector.sample.cell.$index"
                        style = {
                            padding = 2.px
                            backgroundColor = 0x22496699
                            border(1.px, 0x665A9CE0)
                        }

                    }) {
                        text("cell-$index")
                    }
                }
            }
        }
    }
}
