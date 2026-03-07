package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.*
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private val JUSTIFY_OPTIONS = listOf(
    "start" to JustifyContent.Start,
    "center" to JustifyContent.Center,
    "end" to JustifyContent.End,
    "space-between" to JustifyContent.SpaceBetween,
    "space-around" to JustifyContent.SpaceAround,
    "space-evenly" to JustifyContent.SpaceEvenly
)

private val ALIGN_OPTIONS = listOf(
    "start" to AlignItems.Start,
    "center" to AlignItems.Center,
    "end" to AlignItems.End,
    "stretch" to AlignItems.Stretch
)

fun UiScope.displaySection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val inlineMinWidth = 96
    val inlineMaxWidth = (contentWidth - 8).coerceAtLeast(inlineMinWidth)
    val inlineWidth = window.displayInlineWidth.toInt().coerceIn(inlineMinWidth, inlineMaxWidth)
    val gridColumns = window.displayGridColumns.toInt().coerceIn(2, 6)
    val justifyIndex = window.displayFlexJustifyIndex.mod(JUSTIFY_OPTIONS.size)
    val alignIndex = window.displayFlexAlignIndex.mod(ALIGN_OPTIONS.size)
    val justify = JUSTIFY_OPTIONS[justifyIndex]
    val align = ALIGN_OPTIONS[alignIndex]

    div({
        key = "section.display"
        style = {
            width = contentWidth.px
            height = contentHeight.px
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
        button(if (window.displayBlockLargeGap) "Block gap: large" else "Block gap: compact", {
            style = { width = 92.px }
            onMouseClick = {
                window.displayBlockLargeGap = !window.displayBlockLargeGap
                window.appendInfo("Display.block gap=${if (window.displayBlockLargeGap) "large" else "compact"}")
            }
        })
        div({
            key = "display.block.container"
            style = {
                width = (contentWidth - 8).px
                padding = 3.px
                backgroundColor = 0xFF2B3542.toInt()
                display = Display.Block
                gap = (if (window.displayBlockLargeGap) 6 else 2).px
                border(1.px, 0xFF657688.toInt())
            }
        }) {
            repeat(3) { index ->
                div({
                    key = "display.block.item.$index"
                    style = {
                        padding = 2.px
                        backgroundColor = (0xFF3A4B60 + index * 0x000A0A00).toInt()
                        border(1.px, 0xFF8095AA.toInt())
                    }
                }
                ) {
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
                step = 4
            ),
            {
                key = "display.inline.width"
                style = { width = (contentWidth - 8).px }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: inlineWidth.toLong()
                    window.displayInlineWidth = next.coerceIn(inlineMinWidth.toLong(), inlineMaxWidth.toLong())
                }
            }
        )
        dynamicText(
            { "inline container width=$inlineWidth (drag slider to force wrapping)" },
            { style = { color = DEMO_MUTED } })
        div({
            key = "display.inline.container"
            style = {
                width = inlineWidth.px
                padding = 3.px
                backgroundColor = 0xFF2E3946.toInt()
                display = Display.Inline
                border(1.px, 0xFF607181.toInt())
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
                        margin(1.px, 2.px, 1.px, 1.px)
                        border(1.px, 0xFF90A7BE.toInt())
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
                    margin(1.px, 2.px, 1.px, 1.px)
                    border(1.px, 0xFF86B197.toInt())
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
                        border(1.px, 0xFF5B8D73.toInt())
                        display = Display.Flex
                        flexDirection = FlexDirection.Row
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
                    margin(1.px, 2.px, 1.px, 1.px)
                    border(1.px, 0xFFB58E6A.toInt())
                }
            }) {
                div({
                    style = {
                        gap = 0.px
                        padding = 1.px
                        backgroundColor = 0xFF4B3B30.toInt()
                        display = Display.Block
                        border(1.px, 0xFF8B6A51.toInt())
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
            button(
                if (window.displayShowHidden) "Target visible" else "Target hidden",
                {
                    style = { width = 86.px }
                    onMouseClick = {
                        window.displayShowHidden = !window.displayShowHidden
                        window.appendInfo("Display.none visible=${window.displayShowHidden}")
                    }
                }
            )
            text(
                "targetClicks=${window.displayNoneClicks} (should not change while hidden)",
                { style = { color = DEMO_MUTED } }
            )
        }
        div({
            key = "display.none.container"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Column
                width = (contentWidth - 8).px
                padding = 3.px
                backgroundColor = 0xFF303A46.toInt()
                gap = 2.px
                border(1.px, 0xFF64788B.toInt())
            }

        }) {
            div({
                key = "display.none.target"
                onMouseClick = {
                    window.displayNoneClicks += 1
                    window.logHook("display.none.onMouseClick", it)
                }
                style = {
                    padding = 2.px
                    backgroundColor = 0xFF5A3E3E.toInt()
                    display = if (window.displayShowHidden) Display.Block else Display.None
                    border(1.px, 0xFFB07B7B.toInt())
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
                style = { width = 84.px }
                onMouseClick = {
                    window.displayFlexJustifyIndex = (justifyIndex + 1) % JUSTIFY_OPTIONS.size
                }
            })
            button("align=${align.first}", {
                style = { width = 80.px }
                onMouseClick = {
                    window.displayFlexAlignIndex = (alignIndex + 1) % ALIGN_OPTIONS.size
                }
            })
            button(
                if (window.displayGridLargeGap) "gap: large" else "gap: compact",
                {
                    style = { width = 78.px }
                    onMouseClick = { window.displayGridLargeGap = !window.displayGridLargeGap }
                }
            )
        }
        text("Row uses fixed-size items so justify spacing is easier to compare.", {
            style = { color = DEMO_MUTED }
        })
        div({
            key = "display.flex.justify.playground"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
                width = (contentWidth - 8).px
                height = 20.px
                padding = 1.px
                backgroundColor = 0xFF24303A.toInt()
                justifyContent = justify.second
                alignItems = AlignItems.Center
                gap = 0.px
                border(1.px, 0xFF7E93A8.toInt())
            }

        }) {
            div({
                key = "display.flex.justify.dot.left"
                style = {
                    width = 10.px
                    height = 10.px
                    backgroundColor = 0xFFB3D6FF.toInt()
                    border(1.px, 0xFFDEEFFF.toInt())
                }
            }) { text("A") }
            div({
                key = "display.flex.justify.dot.mid"
                style = {
                    width = 10.px
                    height = 10.px
                    backgroundColor = 0xFF9FE3B5.toInt()
                    border(1.px, 0xFFD6FFE4.toInt())
                }
            }) { text("B") }
            div({
                key = "display.flex.justify.dot.right"
                style = {
                    width = 10.px
                    height = 10.px
                    backgroundColor = 0xFFFFC7A3.toInt()
                    border(1.px, 0xFFFFE5D3.toInt())
                }
            }) { text("C") }
        }
        text("Top strip isolates justify (A/B/C). Large row below combines justify + align + gap.", {
            style = { color = DEMO_MUTED }
        })
        div({
            key = "display.flex.row"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
                width = (contentWidth - 8).px
                height = 36.px
                padding = 2.px
                backgroundColor = 0xFF2A343F.toInt()
                justifyContent = justify.second
                alignItems = align.second
                gap = (if (window.displayGridLargeGap) 8 else 2).px
                border(1.px, 0xFF6C7E90.toInt())
            }

        }) {
            div({
                key = "display.flex.row.item.0"
                style = {
                    width = 14.px
                    padding = 1.px
                    backgroundColor = 0xFF46627C.toInt()
                }
            }) { text("1") }
            div({
                key = "display.flex.row.item.1"
                style = {
                    width = 20.px
                    padding = 2.px
                    backgroundColor = 0xFF4E7A5A.toInt()
                }
            }) { text("2") }
            div({
                key = "display.flex.row.item.2"
                style = {
                    width = 26.px
                    padding = 3.px
                    backgroundColor = 0xFF7A5B4A.toInt()
                }
            }) { text("3") }
            div({
                key = "display.flex.row.item.3"
                style = {
                    width = 16.px
                    padding = 1.px
                    backgroundColor = 0xFF6A4B78.toInt()
                }
            }) { text("4") }
        }
        div({
            key = "display.flex.column"
            style = {
                width = (contentWidth - 8).px
                height = 58.px
                padding = 2.px
                backgroundColor = 0xFF2A3340.toInt()
                gap = 2.px
                border(1.px, 0xFF6B7E92.toInt())
                display = Display.Flex
                flexDirection = FlexDirection.Column
            }

        }) {
            div({
                style = {
                    height = 12.px
                    backgroundColor = 0xFF4B5C70.toInt()
                }
            }) { text("header") }
            div({
                style = {
                    backgroundColor = 0xFF3E6A54.toInt()
                    flexGrow = 1f
                }
            }) {
                text("content grow")
            }
            div({
                style = {
                    height = 12.px
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
                step = 1
            ),
            {
                key = "display.grid.columns"
                style = { width = (contentWidth - 8).px }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: gridColumns.toLong()
                    window.displayGridColumns = next.coerceIn(2, 6)
                }
            }
        )
        text(
            "gridColumns=${window.displayGridColumns} (first tile spans 2 columns)",
            { style = { color = DEMO_MUTED } }
        )
        div({
            key = "display.grid.container"
            style = {
                width = (contentWidth - 8).px
                padding = 3.px
                backgroundColor = 0xFF2B3540.toInt()
                display = Display.Grid
                this.gridColumns = gridColumns
                gap = (if (window.displayGridLargeGap) 4 else 2).px
                alignItems = align.second
                justifyItems = JustifyItems.Stretch
                border(1.px, 0xFF70849A.toInt())
            }
        }) {
            repeat(10) { index ->
                div({
                    key = "display.grid.item.$index"
                    style = {
                        height = (if (index % 3 == 0) 18 else 14).px
                        padding = 1.px
                        backgroundColor = (0xFF3D5873 + index * 0x00040401).toInt()
                        if (index == 0) {
                            gridColumnSpan = 2
                        }
                        border(1.px, 0xFF93AACC.toInt())
                    }
                }) {
                    text("Cell ${index + 1}")
                }
            }
        }
    }
}


