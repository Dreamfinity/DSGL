package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.*
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

fun UiScope.renderDisplaySection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val inlineMinWidth = 96
    val inlineMaxWidth = (contentWidth - 8).coerceAtLeast(inlineMinWidth)
    val inlineWidth = window.displayInlineWidth.toInt().coerceIn(inlineMinWidth, inlineMaxWidth)
    val gridColumns = window.displayGridColumns.toInt().coerceIn(2, 6)
    val justifyIndex = window.displayFlexJustifyIndex.mod(JUSTIFY_OPTIONS.size)
    val alignIndex = window.displayFlexAlignIndex.mod(ALIGN_OPTIONS.size)
    val justify = JUSTIFY_OPTIONS[justifyIndex]
    val align = ALIGN_OPTIONS[alignIndex]

    div(
        ComponentProps(
            key = "section.display",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("Display showcase: block / inline / none / flex / grid"))
        text(TextProps("This section is self-checking: each panel demonstrates one display mode.").apply {
            color = DEMO_MUTED
        })

        text(TextProps("Block flow (vertical stacking)"))
        button(
            ButtonProps(if (window.displayBlockLargeGap) "Block gap: large" else "Block gap: compact").apply {
                width = 92
                onMouseClick = {
                    window.displayBlockLargeGap = !window.displayBlockLargeGap
                    window.appendInfo("Display.block gap=${if (window.displayBlockLargeGap) "large" else "compact"}")
                }
            }
        )
        div(
            ComponentProps(
                key = "display.block.container",
                width = contentWidth - 8,
                padding = 3,
                backgroundColor = 0xFF2B3542.toInt()
            ).apply {
                style = {
                    display = Display.Block
                    gap = if (window.displayBlockLargeGap) 6 else 2
                    border(1, 0xFF657688.toInt())
                }
            }
        ) {
            repeat(3) { index ->
                div(
                    ComponentProps(
                        key = "display.block.item.$index",
                        padding = 2,
                        backgroundColor = (0xFF3A4B60 + index * 0x000A0A00).toInt()
                    ).apply {
                        style = { border(1, 0xFF8095AA.toInt()) }
                    }
                ) {
                    text(TextProps("Block item ${index + 1}"))
                }
            }
        }

        text(TextProps("Inline flow (chips with wrap, incl. nested flex/block items)"))
        input(
            InputProps(
                InputType.Range(
                    value = inlineWidth.toLong(),
                    min = inlineMinWidth.toLong(),
                    max = inlineMaxWidth.toLong(),
                    step = 4
                )
            ).apply {
                key = "display.inline.width"
                width = contentWidth - 8
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: inlineWidth.toLong()
                    window.displayInlineWidth = next.coerceIn(inlineMinWidth.toLong(), inlineMaxWidth.toLong())
                }
            }
        )
        text(
            TextProps { "inline container width=$inlineWidth (drag slider to force wrapping)" }
                .apply { color = DEMO_MUTED }
        )
        div(
            ComponentProps(
                key = "display.inline.container",
                width = inlineWidth,
                padding = 3,
                backgroundColor = 0xFF2E3946.toInt()
            ).apply {
                style = {
                    display = Display.Inline
                    border(1, 0xFF607181.toInt())
                    gap = 2
                }
            }
        ) {
            listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta").forEachIndexed { index, label ->
                div(
                    ComponentProps(
                        key = "display.inline.chip.$index",
                        padding = 2,
                        backgroundColor = 0xFF40556B.toInt()
                    ).apply {
                        style = {
                            display = Display.Inline
                            margin(1, 2, 1, 1)
                            border(1, 0xFF90A7BE.toInt())
                        }
                    }
                ) {
                    text(TextProps(label))
                }
            }
            div(
                ComponentProps(
                    key = "display.inline.flex.item",
                    padding = 2,
                    backgroundColor = 0xFF3C5D4A.toInt()
                ).apply {
                    style = {
                        display = Display.Inline
                        margin(1, 2, 1, 1)
                        border(1, 0xFF86B197.toInt())
                    }
                }
            ) {
                text(TextProps("flex"))
                div(
                    ComponentProps(
                        gap = 1,
                        padding = 1,
                        backgroundColor = 0xFF2E4739.toInt()
                    ).apply {
                        style = {
                            display = Display.Flex
                            flexDirection = FlexDirection.Row
                            border(1, 0xFF5B8D73.toInt())
                        }
                    }
                ) {
                    div(ComponentProps(width = 8, height = 4, backgroundColor = 0xFF6EAE8B.toInt()))
                    div(ComponentProps(width = 6, height = 4, backgroundColor = 0xFF4E7A62.toInt()))
                }
            }
            div(
                ComponentProps(
                    key = "display.inline.block.item",
                    padding = 2,
                    backgroundColor = 0xFF5E4B3C.toInt()
                ).apply {
                    style = {
                        display = Display.Inline
                        margin(1, 2, 1, 1)
                        border(1, 0xFFB58E6A.toInt())
                    }
                }
            ) {
                div(
                    ComponentProps(
                        gap = 0,
                        padding = 1,
                        backgroundColor = 0xFF4B3B30.toInt()
                    ).apply {
                        style = {
                            display = Display.Block
                            border(1, 0xFF8B6A51.toInt())
                            gap = 1
                        }
                    }
                ) {
                    text(TextProps("block"))
                    text(TextProps("A"))
                    text(TextProps("B"))
                }
            }
        }

        text(TextProps("Display none (layout + hit-test removal)"))
        div(ComponentProps(gap = 4).asFlexRow()) {
            button(
                ButtonProps(if (window.displayShowHidden) "Target visible" else "Target hidden").apply {
                    width = 86
                    onMouseClick = {
                        window.displayShowHidden = !window.displayShowHidden
                        window.appendInfo("Display.none visible=${window.displayShowHidden}")
                    }
                }
            )
            text(
                TextProps {
                    "targetClicks=${window.displayNoneClicks} (should not change while hidden)"
                }.apply { color = DEMO_MUTED }
            )
        }
        div(
            ComponentProps(
                key = "display.none.container",
                width = contentWidth - 8,
                padding = 3,
                backgroundColor = 0xFF303A46.toInt(),
                gap = 2
            ).asFlexColumn().apply {
                style = { border(1, 0xFF64788B.toInt()) }
            }
        ) {
            div(
                ComponentProps(
                    key = "display.none.target",
                    padding = 2,
                    backgroundColor = 0xFF5A3E3E.toInt(),
                    onMouseClick = {
                        window.displayNoneClicks += 1
                        window.logHook("display.none.onMouseClick", it)
                    }
                ).apply {
                    style = {
                        display = if (window.displayShowHidden) Display.Block else Display.None
                        border(1, 0xFFB07B7B.toInt())
                    }
                }
            ) {

                text(TextProps("Toggle target (click me)"))
            }
            text(TextProps("Sibling stays and reflows when target is hidden.").apply { color = DEMO_MUTED })
        }

        text(TextProps("Flex layout (row + column)"))
        div(ComponentProps(gap = 4).asFlexRow()) {
            button(
                ButtonProps("justify=${justify.first}").apply {
                    width = 84
                    onMouseClick = {
                        window.displayFlexJustifyIndex = (justifyIndex + 1) % JUSTIFY_OPTIONS.size
                    }
                }
            )
            button(
                ButtonProps("align=${align.first}").apply {
                    width = 80
                    onMouseClick = {
                        window.displayFlexAlignIndex = (alignIndex + 1) % ALIGN_OPTIONS.size
                    }
                }
            )
            button(
                ButtonProps(if (window.displayGridLargeGap) "gap: large" else "gap: compact").apply {
                    width = 78
                    onMouseClick = { window.displayGridLargeGap = !window.displayGridLargeGap }
                }
            )
        }
        text(TextProps("Row uses fixed-size items so justify spacing is easier to compare.").apply {
            color = DEMO_MUTED
        })
        div(
            ComponentProps(
                key = "display.flex.justify.playground",
                width = contentWidth - 8,
                height = 20,
                padding = 1,
                backgroundColor = 0xFF24303A.toInt()
            ).apply {
                style = {
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                    justifyContent = justify.second
                    alignItems = AlignItems.Center
                    gap = 0
                    border(1, 0xFF7E93A8.toInt())
                }
            }
        ) {
            div(
                ComponentProps(
                    key = "display.flex.justify.dot.left",
                    width = 10,
                    height = 10,
                    backgroundColor = 0xFFB3D6FF.toInt()
                ).apply {
                    style = {
                        border(1, 0xFFDEEFFF.toInt())
                    }
                }
            ) { text(TextProps("A")) }
            div(
                ComponentProps(
                    key = "display.flex.justify.dot.mid",
                    width = 10,
                    height = 10,
                    backgroundColor = 0xFF9FE3B5.toInt()
                ).apply {
                    style = {
                        border(1, 0xFFD6FFE4.toInt())
                    }
                }
            ) { text(TextProps("B")) }
            div(
                ComponentProps(
                    key = "display.flex.justify.dot.right",
                    width = 10,
                    height = 10,
                    backgroundColor = 0xFFFFC7A3.toInt()
                ).apply {
                    style = {
                        border(1, 0xFFFFE5D3.toInt())
                    }
                }
            ) { text(TextProps("C")) }
        }
        text(TextProps("Top strip isolates justify (A/B/C). Large row below combines justify + align + gap.").apply {
            color = DEMO_MUTED
        })
        div(
            ComponentProps(
                key = "display.flex.row",
                width = contentWidth - 8,
                height = 36,
                padding = 2,
                backgroundColor = 0xFF2A343F.toInt()
            ).apply {
                style = {
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                    justifyContent = justify.second
                    alignItems = align.second
                    gap = if (window.displayGridLargeGap) 8 else 2
                    border(1, 0xFF6C7E90.toInt())
                }
            }
        ) {
            div(
                ComponentProps(
                    key = "display.flex.row.item.0",
                    width = 14,
                    padding = 1,
                    backgroundColor = 0xFF46627C.toInt()
                )
            ) { text(TextProps("1")) }
            div(
                ComponentProps(
                    key = "display.flex.row.item.1",
                    width = 20,
                    padding = 2,
                    backgroundColor = 0xFF4E7A5A.toInt()
                )
            ) { text(TextProps("2")) }
            div(
                ComponentProps(
                    key = "display.flex.row.item.2",
                    width = 26,
                    padding = 3,
                    backgroundColor = 0xFF7A5B4A.toInt()
                )
            ) { text(TextProps("3")) }
            div(
                ComponentProps(
                    key = "display.flex.row.item.3",
                    width = 16,
                    padding = 1,
                    backgroundColor = 0xFF6A4B78.toInt()
                )
            ) { text(TextProps("4")) }
        }
        div(
            ComponentProps(
                key = "display.flex.column",
                width = contentWidth - 8,
                height = 58,
                padding = 2,
                backgroundColor = 0xFF2A3340.toInt()
            ).apply {
                style = {
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                    gap = 2
                    border(1, 0xFF6B7E92.toInt())
                }
            }
        ) {
            div(ComponentProps(height = 12, backgroundColor = 0xFF4B5C70.toInt())) { text(TextProps("header")) }
            div(
                ComponentProps(backgroundColor = 0xFF3E6A54.toInt()).apply {
                    style = { flexGrow = 1f }
                }
            ) { text(TextProps("content grow")) }
            div(ComponentProps(height = 12, backgroundColor = 0xFF66503D.toInt())) { text(TextProps("footer")) }
        }

        text(TextProps("Grid layout (repeat(columns, 1fr))"))
        input(
            InputProps(
                InputType.Range(
                    value = gridColumns.toLong(),
                    min = 2,
                    max = 6,
                    step = 1
                )
            ).apply {
                key = "display.grid.columns"
                width = contentWidth - 8
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: gridColumns.toLong()
                    window.displayGridColumns = next.coerceIn(2, 6)
                }
            }
        )
        text(
            TextProps {
                "gridColumns=${window.displayGridColumns} (first tile spans 2 columns)"
            }.apply { color = DEMO_MUTED }
        )
        div(
            ComponentProps(
                key = "display.grid.container",
                width = contentWidth - 8,
                padding = 3,
                backgroundColor = 0xFF2B3540.toInt()
            ).apply {
                style = {
                    display = Display.Grid
                    this.gridColumns = gridColumns
                    gap = if (window.displayGridLargeGap) 4 else 2
                    alignItems = align.second
                    justifyItems = JustifyItems.Stretch
                    border(1, 0xFF70849A.toInt())
                }
            }
        ) {
            repeat(10) { index ->
                div(
                    ComponentProps(
                        key = "display.grid.item.$index",
                        height = if (index % 3 == 0) 18 else 14,
                        padding = 1,
                        backgroundColor = (0xFF3D5873 + index * 0x00040401).toInt()
                    ).apply {
                        style = {
                            if (index == 0) {
                                gridColumnSpan = 2
                            }
                            border(1, 0xFF93AACC.toInt())
                        }
                    }
                ) {
                    text(TextProps("Cell ${index + 1}"))
                }
            }
        }
    }
}
