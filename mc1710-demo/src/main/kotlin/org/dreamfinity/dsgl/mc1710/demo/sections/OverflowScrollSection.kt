package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private val OVERFLOW_MODES = listOf(
    Overflow.Visible,
    Overflow.Hidden,
    Overflow.Scroll,
    Overflow.Auto
)

private fun Overflow.label(): String = when (this) {
    Overflow.Visible -> "visible"
    Overflow.Hidden -> "hidden"
    Overflow.Scroll -> "scroll"
    Overflow.Auto -> "auto"
}

private fun nextOverflow(current: Overflow): Overflow {
    val idx = OVERFLOW_MODES.indexOf(current)
    if (idx < 0) return OVERFLOW_MODES.first()
    return OVERFLOW_MODES[(idx + 1) % OVERFLOW_MODES.size]
}

fun UiScope.overflowScrollSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val viewportMinWidth = 88
    val viewportMaxWidth = (contentWidth - 24).coerceAtLeast(viewportMinWidth)
    val viewportMinHeight = 56
    val viewportMaxHeight = (contentHeight - 84).coerceAtLeast(viewportMinHeight)
    val contentMinWidth = 60
    val contentMaxWidth = (contentWidth + 80).coerceAtLeast(contentMinWidth)
    val contentMinHeight = 48
    val contentMaxHeight = (contentHeight + 180).coerceAtLeast(contentMinHeight)

    val viewportWidth = window.overflowDemoViewportWidth.toInt().coerceIn(viewportMinWidth, viewportMaxWidth)
    val viewportHeight = window.overflowDemoViewportHeight.toInt().coerceIn(viewportMinHeight, viewportMaxHeight)
    val demoContentWidth = window.overflowDemoContentWidth.toInt().coerceIn(contentMinWidth, contentMaxWidth)
    val demoContentHeight = window.overflowDemoContentHeight.toInt().coerceIn(contentMinHeight, contentMaxHeight)

    div({
        key = "section.overflowScroll"
        style = {
            width = contentWidth.px
            height = contentHeight.px
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
            overflowY = Overflow.Auto
        }
    }) {
        text("Overflow/scroll viewport playground")
        text(
            "Scrollbar presence is state-only for now, but gutters already reduce viewport size.",
            { style = { color = DEMO_MUTED } }
        )

        div({
            key = "section.overflowScroll.controls"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
                gap = 4.px
            }
        }) {
            button("overflow-x: ${window.overflowDemoOverflowX.label()}", {
                onMouseClick = {
                    window.overflowDemoOverflowX = nextOverflow(window.overflowDemoOverflowX)
                    window.appendInfo("Overflow demo x=${window.overflowDemoOverflowX.label()}")
                }
            })
            button("overflow-y: ${window.overflowDemoOverflowY.label()}", {
                onMouseClick = {
                    window.overflowDemoOverflowY = nextOverflow(window.overflowDemoOverflowY)
                    window.appendInfo("Overflow demo y=${window.overflowDemoOverflowY.label()}")
                }
            })
            button("Reset", {
                onMouseClick = {
                    window.overflowDemoOverflowX = Overflow.Auto
                    window.overflowDemoOverflowY = Overflow.Auto
                    window.overflowDemoViewportWidth = 118L
                    window.overflowDemoViewportHeight = 76L
                    window.overflowDemoContentWidth = 132L
                    window.overflowDemoContentHeight = 126L
                    window.overflowDemoVisibleClicks = 0
                    window.overflowDemoEdgeClicks = 0
                    window.appendInfo("Overflow demo reset")
                }
            })
        }

        input(
            InputType.Range(
                value = viewportWidth.toLong(),
                min = viewportMinWidth.toLong(),
                max = viewportMaxWidth.toLong(),
                step = 2
            ),
            {
                key = "section.overflowScroll.viewportWidth"
                style = { width = (contentWidth - 8).px }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: viewportWidth.toLong()
                    window.overflowDemoViewportWidth =
                        next.coerceIn(viewportMinWidth.toLong(), viewportMaxWidth.toLong())
                }
            }
        )
        text("Viewport width = $viewportWidth", { style = { color = DEMO_MUTED } })

        input(
            InputType.Range(
                value = viewportHeight.toLong(),
                min = viewportMinHeight.toLong(),
                max = viewportMaxHeight.toLong(),
                step = 2
            ),
            {
                key = "section.overflowScroll.viewportHeight"
                style = { width = (contentWidth - 8).px }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: viewportHeight.toLong()
                    window.overflowDemoViewportHeight =
                        next.coerceIn(viewportMinHeight.toLong(), viewportMaxHeight.toLong())
                }
            }
        )
        text("Viewport height = $viewportHeight", { style = { color = DEMO_MUTED } })

        input(
            InputType.Range(
                value = demoContentWidth.toLong(),
                min = contentMinWidth.toLong(),
                max = contentMaxWidth.toLong(),
                step = 2
            ),
            {
                key = "section.overflowScroll.contentWidth"
                style = { width = (contentWidth - 8).px }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: demoContentWidth.toLong()
                    window.overflowDemoContentWidth = next.coerceIn(contentMinWidth.toLong(), contentMaxWidth.toLong())
                }
            }
        )
        text("Content width = $demoContentWidth", { style = { color = DEMO_MUTED } })

        input(
            InputType.Range(
                value = demoContentHeight.toLong(),
                min = contentMinHeight.toLong(),
                max = contentMaxHeight.toLong(),
                step = 2
            ),
            {
                key = "section.overflowScroll.contentHeight"
                style = { width = (contentWidth - 8).px }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: demoContentHeight.toLong()
                    window.overflowDemoContentHeight =
                        next.coerceIn(contentMinHeight.toLong(), contentMaxHeight.toLong())
                }
            }
        )
        text("Content height = $demoContentHeight", { style = { color = DEMO_MUTED } })

        text(
            "Clicks: visible=${window.overflowDemoVisibleClicks} edge=${window.overflowDemoEdgeClicks} (edge click only when visible)",
            { style = { color = DEMO_MUTED } }
        )

        overflowDemoCard(
            title = "Interactive lab",
            note = "Switch overflow-x/y and sizes; verify clipping and gutter-driven viewport changes.",
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            contentWidth = demoContentWidth,
            contentHeight = demoContentHeight,
            overflowX = window.overflowDemoOverflowX,
            overflowY = window.overflowDemoOverflowY,
            keyPrefix = "section.overflowScroll.lab",
            onVisibleClick = {
                window.overflowDemoVisibleClicks += 1
                window.appendInfo("Overflow demo visible click")
            },
            onEdgeClick = {
                window.overflowDemoEdgeClicks += 1
                window.appendInfo("Overflow demo edge click")
            }
        )

        overflowDemoCard(
            title = "Preset: overflow=scroll",
            note = "Both axes reserve gutter even if content mostly fits.",
            viewportWidth = 118,
            viewportHeight = 76,
            contentWidth = 110,
            contentHeight = 68,
            overflowX = Overflow.Scroll,
            overflowY = Overflow.Scroll,
            keyPrefix = "section.overflowScroll.preset.scroll",
            onVisibleClick = {},
            onEdgeClick = {}
        )

        overflowDemoCard(
            title = "Preset: cross-axis forcing (auto/auto)",
            note = "Tall content triggers vertical gutter, reduced width can then trigger horizontal overflow.",
            viewportWidth = 118,
            viewportHeight = 76,
            contentWidth = 102,
            contentHeight = 156,
            overflowX = Overflow.Auto,
            overflowY = Overflow.Auto,
            keyPrefix = "section.overflowScroll.preset.cross",
            onVisibleClick = {},
            onEdgeClick = {}
        )
    }
}

private fun UiScope.overflowDemoCard(
    title: String,
    note: String,
    viewportWidth: Int,
    viewportHeight: Int,
    contentWidth: Int,
    contentHeight: Int,
    overflowX: Overflow,
    overflowY: Overflow,
    keyPrefix: String,
    onVisibleClick: () -> Unit,
    onEdgeClick: () -> Unit
) {
    div({
        key = "$keyPrefix.card"
        style = {
            width = 100.percent
            display = Display.Flex
            flexDirection = FlexDirection.Column
            gap = 2.px
            padding = 2.px
            backgroundColor = 0xFF2B3440.toInt()
            border(1.px, 0xFF5E7286.toInt())
        }
    }) {
        text(title)
        text(note, { style = { color = DEMO_MUTED } })
        text(
            "viewport=${viewportWidth}x$viewportHeight content=${contentWidth}x$contentHeight overflow-x=${overflowX.label()} overflow-y=${overflowY.label()}",
            { style = { color = DEMO_MUTED } }
        )

        div({
            key = "$keyPrefix.viewport"
            style = {
                width = viewportWidth.px
                height = viewportHeight.px
                this.overflowX = overflowX
                this.overflowY = overflowY
                border(1.px, 0xFF8AA0B5.toInt())
                backgroundColor = 0xFF23303D.toInt()
                padding = 2.px
            }
        }) {
            div({
                key = "$keyPrefix.content"
                style = {
                    width = contentWidth.px
                    height = contentHeight.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                    gap = 1.px
                    border(1.px, 0xFF7992AA.toInt())
                    backgroundColor = 0xFF384C60.toInt()
                    padding = 2.px
                }
            }) {
                text("content top")
                div({
                    key = "$keyPrefix.row"
                    style = {
                        width = contentWidth.px
                        display = Display.Flex
                        flexDirection = FlexDirection.Row
                        gap = 2.px
                    }
                }) {
                    button("visible", {
                        key = "$keyPrefix.visible"
                        style = { width = 50.px }
                        onMouseClick = { onVisibleClick() }
                    })
                    div({
                        key = "$keyPrefix.spacer"
                        style = {
                            width = (contentWidth - 112).coerceAtLeast(0).px
                            height = 1.px
                        }
                    }) {}
                    button("edge", {
                        key = "$keyPrefix.edge"
                        style = { width = 44.px }
                        onMouseClick = { onEdgeClick() }
                    })
                }
                repeat(8) { index ->
                    text("line ${index + 1} -> clip boundary check")
                }
            }
        }
    }
}

