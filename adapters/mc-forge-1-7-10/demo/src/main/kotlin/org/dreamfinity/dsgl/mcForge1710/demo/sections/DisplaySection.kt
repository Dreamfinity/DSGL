package org.dreamfinity.dsgl.mcForge1710.demo.sections

import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.style.*
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_MUTED

private val JUSTIFY_OPTIONS =
    listOf(
        "start" to JustifyContent.Start,
        "center" to JustifyContent.Center,
        "end" to JustifyContent.End,
        "space-between" to JustifyContent.SpaceBetween,
        "space-around" to JustifyContent.SpaceAround,
        "space-evenly" to JustifyContent.SpaceEvenly,
    )

private val ALIGN_OPTIONS =
    listOf(
        "start" to AlignItems.Start,
        "center" to AlignItems.Center,
        "end" to AlignItems.End,
        "stretch" to AlignItems.Stretch,
    )

fun UiScope.displaySection(onInfo: (String) -> Unit, onLogHook: (String, Event, String?) -> Unit) {
    var displayBlockLargeGap by useState(false)
    var displayInlineWidth by useState(132L)
    var displayShowHidden by useState(true)
    var displayFlexJustifyIndex by useState(0)
    var displayFlexAlignIndex by useState(0)
    var displayGridColumns by useState(3L)
    var displayGridLargeGap by useState(false)
    var displayNoneClicks by useState(0)

    val inlineMinWidth = 96
    val inlineMaxWidth = 320
    val inlineWidth = displayInlineWidth.toInt().coerceIn(inlineMinWidth, inlineMaxWidth)
    val gridColumns = displayGridColumns.toInt().coerceIn(2, 6)
    val justifyIndex = displayFlexJustifyIndex.mod(JUSTIFY_OPTIONS.size)
    val alignIndex = displayFlexAlignIndex.mod(ALIGN_OPTIONS.size)
    val justify = JUSTIFY_OPTIONS[justifyIndex]
    val align = ALIGN_OPTIONS[alignIndex]

    div({
        key = "section.display"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Display showcase: block / inline / none / flex / grid")
        text("This section is self-checking: each panel demonstrates one display mode.", {
            style = { color = DEMO_MUTED }
        })

        text("Block flow (vertical stacking)")
        button(if (displayBlockLargeGap) "Block gap: large" else "Block gap: compact", {
            onMouseClick = {
                displayBlockLargeGap = !displayBlockLargeGap
                onInfo("Display.block gap=${if (displayBlockLargeGap) "large" else "compact"}")
            }
        })
        div({
            key = "display.block.container"
            style = {
                width = 100.percent
                padding = 3.px
                backgroundColor = 0xFF2B3542.toInt()
                display = Display.Block
                gap = (if (displayBlockLargeGap) 6 else 2).px
                border {
                    width = 1.px
                    color = 0xFF657688.toInt()
                }
            }
        }) {
            repeat(3) { index ->
                div({
                    key = "display.block.item.$index"
                    style = {
                        padding = 2.px
                        backgroundColor = (0xFF3A4B60 + index * 0x000A0A00).toInt()
                        border {
                            width = 1.px
                            color = 0xFF8095AA.toInt()
                        }
                    }
                }) {
                    text("Block item ${index + 1}")
                }
            }
        }

        text("Inline flow (chips with wrap, incl. nested flex/block items)")
        input(
            InputType.Range(
                value = inlineWidth.toLong(),
                min = inlineMinWidth.toLong(),
                max = inlineMaxWidth.toLong(),
                step = 4,
            ),
            {
                key = "display.inline.width"
                style = { width = 100.percent }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: inlineWidth.toLong()
                    displayInlineWidth = next.coerceIn(inlineMinWidth.toLong(), inlineMaxWidth.toLong())
                }
            },
        )
        dynamicText(
            { "inline container width=$inlineWidth (drag slider to force wrapping)" },
            { style = { color = DEMO_MUTED } },
        )
        div({
            key = "display.inline.container"
            style = {
                width = inlineWidth.px
                padding = 3.px
                backgroundColor = 0xFF2E3946.toInt()
                display = Display.Inline
                border {
                    width = 1.px
                    color = 0xFF607181.toInt()
                }
                gap = 2.px
            }
        }) {
            listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta").forEachIndexed { index, label ->
                div({
                    key = "display.inline.chip.$index"
                    style = {
                        padding = 2.px
                        backgroundColor = 0xFF40556B.toInt()
                        display = Display.Inline
                        margin {
                            top = 1.px
                            right = 2.px
                            bottom = 1.px
                            left = 1.px
                        }
                        border {
                            width = 1.px
                            color = 0xFF90A7BE.toInt()
                        }
                    }
                }) {
                    text(label)
                }
            }
            div({
                key = "display.inline.flex.item"
                style = {
                    padding = 2.px
                    backgroundColor = 0xFF3C5D4A.toInt()
                    display = Display.Inline
                    margin {
                        top = 1.px
                        right = 2.px
                        bottom = 1.px
                        left = 1.px
                    }
                    border {
                        width = 1.px
                        color = 0xFF86B197.toInt()
                    }
                }
            }) {
                text("flex")
                div({
                    style = {
                        gap = 1.px
                        padding = 1.px
                        backgroundColor = 0xFF2E4739.toInt()
                        display = Display.Flex
                        flexDirection = FlexDirection.Row
                        border {
                            width = 1.px
                            color = 0xFF5B8D73.toInt()
                        }
                    }
                }) {
                    div({
                        style = {
                            width = 8.px
                            height = 4.px
                            backgroundColor = 0xFF6EAE8B.toInt()
                        }
                    })
                    div({
                        style = {
                            width = 6.px
                            height = 4.px
                            backgroundColor = 0xFF4E7A62.toInt()
                        }
                    })
                }
            }
            div({
                key = "display.inline.block.item"
                style = {
                    padding = 2.px
                    backgroundColor = 0xFF5E4B3C.toInt()
                    display = Display.Inline
                    margin {
                        top = 1.px
                        right = 2.px
                        bottom = 1.px
                        left = 1.px
                    }
                    border {
                        width = 1.px
                        color = 0xFFB58E6A.toInt()
                    }
                }
            }) {
                div({
                    style = {
                        padding = 1.px
                        backgroundColor = 0xFF4B3B30.toInt()
                        display = Display.Block
                        border {
                            width = 1.px
                            color = 0xFF8B6A51.toInt()
                        }
                        gap = 1.px
                    }
                }) {
                    text("block")
                    text("A")
                    text("B")
                }
            }
        }

        text("Display none (layout + hit-test removal)")
        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(if (displayShowHidden) "Target visible" else "Target hidden", {
                onMouseClick = {
                    displayShowHidden = !displayShowHidden
                    onInfo("Display.none visible=$displayShowHidden")
                }
            })
            text(
                "targetClicks=$displayNoneClicks (should not change while hidden)",
                { style = { color = DEMO_MUTED } },
            )
        }
        div({
            key = "display.none.container"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Column
                width = 100.percent
                padding = 3.px
                backgroundColor = 0xFF303A46.toInt()
                gap = 2.px
                border {
                    width = 1.px
                    color = 0xFF64788B.toInt()
                }
            }
        }) {
            div({
                key = "display.none.target"
                onMouseClick = {
                    displayNoneClicks += 1
                    onLogHook("display.none.onMouseClick", it, null)
                }
                style = {
                    padding = 2.px
                    backgroundColor = 0xFF5A3E3E.toInt()
                    display = if (displayShowHidden) Display.Block else Display.None
                    border {
                        width = 1.px
                        color = 0xFFB07B7B.toInt()
                    }
                }
            }) {
                text("Toggle target (click me)")
            }
            text("Sibling stays and reflows when target is hidden.", { style = { color = DEMO_MUTED } })
        }

        text("Flex layout (row + column)")
        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("justify=${justify.first}", {
                onMouseClick = {
                    displayFlexJustifyIndex = (justifyIndex + 1) % JUSTIFY_OPTIONS.size
                }
            })
            button("align=${align.first}", {
                onMouseClick = {
                    displayFlexAlignIndex = (alignIndex + 1) % ALIGN_OPTIONS.size
                }
            })
            button(if (displayGridLargeGap) "gap: large" else "gap: compact", {
                onMouseClick = { displayGridLargeGap = !displayGridLargeGap }
            })
        }
        text("Row uses fixed-size items so justify spacing is easier to compare.", {
            style = { color = DEMO_MUTED }
        })
        div({
            key = "display.flex.justify.playground"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
                width = 100.percent
                padding = 1.px
                backgroundColor = 0xFF24303A.toInt()
                justifyContent = justify.second
                alignItems = AlignItems.Center
                gap = 0.px
                border {
                    width = 1.px
                    color = 0xFF7E93A8.toInt()
                }
            }
        }) {
            dot("left", "A", 0xFFB3D6FF.toInt(), 0xFFDEEFFF.toInt())
            dot("mid", "B", 0xFF9FE3B5.toInt(), 0xFFD6FFE4.toInt())
            dot("right", "C", 0xFFFFC7A3.toInt(), 0xFFFFE5D3.toInt())
        }
        text("Top strip isolates justify (A/B/C). Large row below combines justify + align + gap.", {
            style = { color = DEMO_MUTED }
        })
        div({
            key = "display.flex.row"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
                width = 100.percent
                padding = 2.px
                backgroundColor = 0xFF2A343F.toInt()
                justifyContent = justify.second
                alignItems = align.second
                gap = (if (displayGridLargeGap) 8 else 2).px
                border {
                    width = 1.px
                    color = 0xFF6C7E90.toInt()
                }
            }
        }) {
            flexRowCell("0", "1", 14, 1, 0xFF46627C.toInt())
            flexRowCell("1", "2", 20, 2, 0xFF4E7A5A.toInt())
            flexRowCell("2", "3", 26, 3, 0xFF7A5B4A.toInt())
            flexRowCell("3", "4", 16, 1, 0xFF6A4B78.toInt())
        }
        div({
            key = "display.flex.column"
            style = {
                width = 100.percent
                padding = 2.px
                backgroundColor = 0xFF2A3340.toInt()
                gap = 2.px
                border {
                    width = 1.px
                    color = 0xFF6B7E92.toInt()
                }
                display = Display.Flex
                flexDirection = FlexDirection.Column
            }
        }) {
            div({
                style = {
                    display = Display.Inline
                    backgroundColor = 0xFF4B5C70.toInt()
                }
            }) { text("header") }
            div({
                style = {
                    display = Display.Inline
                    backgroundColor = 0xFF3E6A54.toInt()
                    flexGrow = 1f
                }
            }) {
                text("content grow")
            }
            div({
                style = {
                    display = Display.Inline
                    backgroundColor = 0xFF66503D.toInt()
                }
            }) { text("footer") }
        }

        text("Grid layout (repeat(columns, 1fr))")
        input(
            InputType.Range(
                value = gridColumns.toLong(),
                min = 2,
                max = 6,
                step = 1,
            ),
            {
                key = "display.grid.columns"
                style = { width = 100.percent }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: gridColumns.toLong()
                    displayGridColumns = next.coerceIn(2, 6)
                }
            },
        )
        text(
            "gridColumns=$displayGridColumns (first tile spans 2 columns)",
            { style = { color = DEMO_MUTED } },
        )
        div({
            key = "display.grid.container"
            style = {
                width = 100.percent
                padding = 3.px
                backgroundColor = 0xFF2B3540.toInt()
                display = Display.Grid
                this.gridColumns = gridColumns
                gap = (if (displayGridLargeGap) 4 else 2).px
                alignItems = align.second
                justifyItems = JustifyItems.Stretch
                border {
                    width = 1.px
                    color = 0xFF70849A.toInt()
                }
            }
        }) {
            repeat(10) { index ->
                div({
                    key = "display.grid.item.$index"
                    style = {
                        padding = 1.px
                        backgroundColor = (0xFF3D5873 + index * 0x00040401).toInt()
                        if (index == 0) {
                            gridColumnSpan = 2
                        }
                        border {
                            width = 1.px
                            color = 0xFF93AACC.toInt()
                        }
                    }
                }) {
                    text("Cell ${index + 1}")
                }
            }
        }
    }
}

private fun UiScope.dot(
    keyPart: String,
    label: String,
    fill: Int,
    borderColor: Int,
) {
    div({
        key = "display.flex.justify.dot.$keyPart"
        style = {
            display = Display.Inline
            backgroundColor = fill
            border {
                width = 1.px
                color = borderColor
            }
        }
    }) { text(label) }
}

private fun UiScope.flexRowCell(
    keyPart: String,
    label: String,
    @Suppress("UnusedParameter") widthPx: Int,
    @Suppress("UnusedParameter") paddingPx: Int,
    color: Int,
) {
    div({
        key = "display.flex.row.item.$keyPart"
        style = {
            display = Display.Inline
            backgroundColor = color
        }
    }) { text(label) }
}
