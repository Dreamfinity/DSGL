package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputOption
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.*
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private val POSITION_MODE_OPTIONS = listOf(
    PositionMode.Static,
    PositionMode.Relative,
    PositionMode.Absolute,
    PositionMode.Fixed,
    PositionMode.Sticky
)

private const val OFFSET_MIN = -72
private const val OFFSET_MAX = 120
private const val Z_MIN = -8
private const val Z_MAX = 12

private const val CARD_BLUE = 0xFF355E91.toInt()
private const val CARD_GREEN = 0xFF3E7A56.toInt()
private const val CARD_RED = 0xFF8A4A44.toInt()

fun UiScope.positionedLayoutSection(viewportWidthPx: Int) {
    var positionedDemoModeIndex by useState(1L)
    var positionedDemoUseLeft by useState(true)
    var positionedDemoUseTop by useState(true)
    var positionedDemoLeft by useState(24L)
    var positionedDemoTop by useState(14L)
    var positionedDemoRight by useState(26L)
    var positionedDemoBottom by useState(18L)
    var positionedDemoZBlue by useState(1L)
    var positionedDemoZGreen by useState(4L)
    var positionedDemoZRed by useState(2L)
    var positionedDemoTieSwap by useState(false)
    var positionedDemoLastHover by useState("none")
    var positionedDemoLastClick by useState("none")
    var positionedDemoBlueClicks by useState(0)
    var positionedDemoGreenClicks by useState(0)
    var positionedDemoRedClicks by useState(0)
    var positionedDemoTieFirstClicks by useState(0)
    var positionedDemoTieSecondClicks by useState(0)
    var positionedDemoMixedStaticClicks by useState(0)
    var positionedDemoMixedPositionedClicks by useState(0)
    var positionedDemoScrollClicks by useState(0)
    var positionedDemoStickyTopClicks by useState(0)
    var positionedDemoStickyCombinedClicks by useState(0)

    val modeIndex = positionedDemoModeIndex.toInt().coerceIn(0, POSITION_MODE_OPTIONS.lastIndex)
    val demoMode = POSITION_MODE_OPTIONS[modeIndex]

    val leftOffset = positionedDemoLeft.toInt().coerceIn(OFFSET_MIN, OFFSET_MAX)
    val topOffset = positionedDemoTop.toInt().coerceIn(OFFSET_MIN, OFFSET_MAX)
    val rightOffset = positionedDemoRight.toInt().coerceIn(0, OFFSET_MAX)
    val bottomOffset = positionedDemoBottom.toInt().coerceIn(0, OFFSET_MAX)
    val zBlue = positionedDemoZBlue.toInt().coerceIn(Z_MIN, Z_MAX)
    val zGreen = positionedDemoZGreen.toInt().coerceIn(Z_MIN, Z_MAX)
    val zRed = positionedDemoZRed.toInt().coerceIn(Z_MIN, Z_MAX)

    val rootAnchoredLeft = (viewportWidthPx / 2).coerceAtLeast(112)
    val rootAnchoredTop = 58
    val fixedBaseLeft = (viewportWidthPx - 236).coerceAtLeast(104)
    val fixedBaseTop = 72


    div({
        key = "section.positionedLayout"
        style = {
            display = Display.Flex
            flexDirection = FlexDirection.Column
            maxHeight = 90.vh
            gap = 4.px
            overflowY = Overflow.Auto
            overflowX = Overflow.Scroll
        }
    }) {
        text("Positioned layout verification surface: static/relative/absolute/fixed + z-index + scroll + hit-testing")
        text(
            "Top-level fixed and root-anchored absolute badges are intentional: they prove root-space anchoring.",
            { style = { color = DEMO_MUTED } }
        )

        controls(
            ControlsProps(
                modeIndex = modeIndex,
                demoMode = demoMode,
                useLeft = positionedDemoUseLeft,
                useTop = positionedDemoUseTop,
                leftOffset = leftOffset,
                rightOffset = rightOffset,
                topOffset = topOffset,
                bottomOffset = bottomOffset,
                zBlue = zBlue,
                zGreen = zGreen,
                zRed = zRed,
                lastHover = positionedDemoLastHover,
                lastClick = positionedDemoLastClick,
                onModeCycle = { positionedDemoModeIndex = ((modeIndex + 1) % POSITION_MODE_OPTIONS.size).toLong() },
                onToggleUseLeft = { positionedDemoUseLeft = !positionedDemoUseLeft },
                onToggleUseTop = { positionedDemoUseTop = !positionedDemoUseTop },
                onSetLeft = { positionedDemoLeft = it },
                onSetRight = { positionedDemoRight = it },
                onSetTop = { positionedDemoTop = it },
                onSetBottom = { positionedDemoBottom = it },
                onSetZBlue = { positionedDemoZBlue = it },
                onSetZGreen = { positionedDemoZGreen = it },
                onSetZRed = { positionedDemoZRed = it },
                onReset = {
                    positionedDemoModeIndex = 1L
                    positionedDemoUseLeft = true
                    positionedDemoUseTop = true
                    positionedDemoLeft = 24L
                    positionedDemoTop = 14L
                    positionedDemoRight = 26L
                    positionedDemoBottom = 18L
                    positionedDemoZBlue = 1L
                    positionedDemoZGreen = 4L
                    positionedDemoZRed = 2L
                    positionedDemoTieSwap = false
                    positionedDemoLastHover = "none"
                    positionedDemoLastClick = "none"
                    positionedDemoBlueClicks = 0
                    positionedDemoGreenClicks = 0
                    positionedDemoRedClicks = 0
                    positionedDemoTieFirstClicks = 0
                    positionedDemoTieSecondClicks = 0
                    positionedDemoMixedStaticClicks = 0
                    positionedDemoMixedPositionedClicks = 0
                    positionedDemoScrollClicks = 0
                    positionedDemoStickyTopClicks = 0
                    positionedDemoStickyCombinedClicks = 0
                }
            )
        )

        div({
            style = {
                border(1.px, 0xFF6A7D8F.toInt())
                padding = 5.px
            }
        }) {
            text("Mode playground: change position mode with the same offsets to compare runtime behavior quickly.")
            div({
                key = "positioned.mode.playground"
                style = {
                    position = PositionMode.Relative
                    border(1.px, 0xFF6A7D8F.toInt())
                    backgroundColor = 0xFF2A3440.toInt()
                    padding = 5.px
                }
            }) {
                div({
                    key = "positioned.mode.target"
                    onMouseEnter = { positionedDemoLastHover = "mode-$demoMode" }
                    onMouseClick = { positionedDemoLastClick = "mode-$demoMode" }
                    style = {
                        position = demoMode
                        left = if (positionedDemoUseLeft) {
                            leftOffset.px
                        } else {
                            null
                        }
                        right = rightOffset.px
                        top = if (positionedDemoUseTop) {
                            topOffset.px
                        } else {
                            null
                        }
                        bottom = bottomOffset.px
                        padding = 5.px
                        zIndex = zBlue
                        backgroundColor = 0xCC476487.toInt()
                        border(1.px, 0xFFBFD8EE.toInt())
                    }
                }) {
                    text("mode=$demoMode")
                }
                text("same offsets + z are reused; static should stay neutral", { style = { color = DEMO_MUTED } })
            }
        }

        div({
            style = {
                border(1.px, 0xFF6A7D8F.toInt())
                padding = 5.px
            }
        }) {
            text("A. Static baseline: offsets exist in style state but static visible geometry ignores them.")
            div({
                key = "positioned.static.sample"
                style = {
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                    gap = 2.px
                    padding = 5.px
                    border(1.px, 0xFF667A8D.toInt())
                    backgroundColor = 0xFF2A313A.toInt()
                }
            }) {
                div({
                    style = {
                        padding = 5.px
                        backgroundColor = 0xFF364352.toInt()
                        border(1.px, 0xFF7E97AD.toInt())
                    }
                }) { text("Flow item before") }
                div({
                    key = "positioned.static.target"
                    onMouseEnter = { positionedDemoLastHover = "static-target" }
                    onMouseClick = {
                        positionedDemoLastClick = "static-target"
                    }
                    style = {
                        position = PositionMode.Static
                        left = leftOffset.px
                        right = rightOffset.px
                        top = topOffset.px
                        bottom = bottomOffset.px
                        padding = 5.px
                        backgroundColor = 0xFF3F5A73.toInt()
                        border(1.px, 0xFF9DB7CF.toInt())
                    }
                }) { text("position: static + offsets (still in normal slot)") }
                div({
                    style = {
                        padding = 5.px
                        backgroundColor = 0xFF364352.toInt()
                        border(1.px, 0xFF7E97AD.toInt())
                    }
                }) { text("Flow item after") }
            }
        }
        div({
            style = {
                border(1.px, 0xFF6A7D8F.toInt())
                padding = 5.px
            }
        }) {
            text("B. Relative: visual offset changes, but the original flow slot stays reserved.")
            div({
                key = "positioned.relative.sample"
                style = {
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                    gap = 3.px
                    padding = 5.px
                    border(1.px, 0xFF6D7C8A.toInt())
                    backgroundColor = 0xFF29323D.toInt()
                }
            }) {
                div({
                    style = {
                        padding = 5.px
                        backgroundColor = 0xFF455B70.toInt()
                        border(1.px, 0xFF91A9BF.toInt())
                    }
                }) { text("left") }
                div({
                    key = "positioned.relative.target"
                    onMouseEnter = { positionedDemoLastHover = "relative-target" }
                    onMouseClick = { positionedDemoLastClick = "relative-target" }
                    style = {
                        position = PositionMode.Relative
                        left = if (positionedDemoUseLeft) {
                            leftOffset.px
                        } else {
                            null
                        }
                        right = rightOffset.px
                        top = if (positionedDemoUseTop) {
                            topOffset.px
                        } else {
                            null
                        }
                        bottom = bottomOffset.px
                        padding = 5.px
                        backgroundColor = 0xFF4A6A87.toInt()
                        border(1.px, 0xFFB5CFE6.toInt())
                        zIndex = zBlue
                    }
                }) { text("relative target") }
                div({
                    style = {
                        padding = 5.px
                        backgroundColor = 0xFF455B70.toInt()
                        border(1.px, 0xFF91A9BF.toInt())
                    }
                }) { text("right") }
            }
        }

        div({
            style = {
                border(1.px, 0xFF6A7D8F.toInt())
                padding = 5.px
            }
        }) {
            text("C. Absolute: out-of-flow, anchored by nearest positioned ancestor (or root when none).")
            div({
                key = "positioned.absolute.sample"
                style = {
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                    gap = 2.px
                    padding = 5.px
                    border(1.px, 0xFF6A7E8C.toInt())
                    backgroundColor = 0xFF2B333E.toInt()
                }
            }) {
                text(
                    "Root-anchored absolute badge appears outside this card (near top-middle).",
                    { style = { color = DEMO_MUTED } })
                div({
                    key = "positioned.absolute.container"
                    style = {
                        position = PositionMode.Relative
                        overflowY = Overflow.Auto
                        padding = 5.px
                        border(1.px, 0xFF8AA0B4.toInt())
                        backgroundColor = 0xFF344252.toInt()
                    }
                }) {
                    div({
                        key = "positioned.absolute.inner"
                        onMouseEnter = { positionedDemoLastHover = "absolute-inside" }
                        onMouseClick = {
                            positionedDemoLastClick = "absolute-inside"
                        }
                        style = {
                            position = PositionMode.Absolute
                            left = if (positionedDemoUseLeft) {
                                leftOffset.px
                            } else {
                                null
                            }
                            right = rightOffset.px
                            top = if (positionedDemoUseTop) {
                                topOffset.px
                            } else {
                                null
                            }
                            bottom = bottomOffset.px
                            padding = 5.px
                            backgroundColor = 0xAA2B4E73.toInt()
                            border(1.px, 0xFFD2E8FF.toInt())
                            zIndex = zGreen
                        }
                    }) { text("absolute in relative ancestor") }
                    repeat(8) { index ->
                        text("flow line ${index + 1} (absolute should not reserve space)")
                    }
                }
            }
        }

        div({
            style = {
                border(1.px, 0xFF6A7D8F.toInt())
                padding = 5.px
            }
        }) {
            div({
                key = "positioned.absolute.rootBadge"
                onMouseEnter = { positionedDemoLastHover = "absolute-root" }
                onMouseClick = { positionedDemoLastClick = "absolute-root" }
                style = {
                    position = PositionMode.Absolute
                    left = rootAnchoredLeft.px
                    top = rootAnchoredTop.px
                    padding = 5.px
                    backgroundColor = 0xCC2C5A89.toInt()
                    border(1.px, 0xFFB9D9FA.toInt())
                    zIndex = 18
                }
            }) {
                text("absolute -> root")
            }

            text("D/F. Fixed under scroll: anchored to current root viewport while normal content scrolls.")
            div({
                key = "positioned.scroll.sample"
                style = {
                    position = PositionMode.Relative
                    overflowY = Overflow.Auto
                    padding = 5.px
                    border(1.px, 0xFF73879A.toInt())
                    backgroundColor = 0xFF27323E.toInt()
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                    maxHeight = 8.em
                    gap = 2.px
                }
            }) {
                div({
                    key = "positioned.scroll.relative"
                    style = {
                        position = PositionMode.Relative
                        left = (leftOffset / 2).px
                        top = (topOffset / 2).px
                        padding = 5.px
                        backgroundColor = 0xFF496B8A.toInt()
                        border(1.px, 0xFFB6D5EE.toInt())
                    }
                }) {
                    text("relative in scroller")
                }
                div({
                    key = "positioned.scroll.absolute"
                    style = {
                        position = PositionMode.Absolute
                        left = 6.px
                        top = 34.px
                        padding = 5.px
                        backgroundColor = 0xAA345469.toInt()
                        border(1.px, 0xFFCFE6F4.toInt())
                        zIndex = 7
                    }
                }) { text("absolute in scroller") }
                repeat(24) { index ->
                    text("scroll line ${index + 1}")
                }
                button("scroll action", {
                    key = "positioned.scroll.button"
                    onMouseClick = {
                        positionedDemoScrollClicks += 1
                        positionedDemoLastClick = "scroll-action"
                    }
                })
                text("scroll action clicks=${positionedDemoScrollClicks}", { style = { color = DEMO_MUTED } })
            }
        }

        div({
            key = "positioned.fixed.badge"
            onMouseEnter = { positionedDemoLastHover = "fixed-badge" }
            onMouseClick = { positionedDemoLastClick = "fixed-badge" }
            style = {
                position = PositionMode.Fixed
                left = if (positionedDemoUseLeft) {
                    (fixedBaseLeft + leftOffset).coerceAtLeast(0).px
                } else {
                    null
                }
                right = rightOffset.px
                top = if (positionedDemoUseTop) {
                    (fixedBaseTop + topOffset).coerceAtLeast(0).px
                } else {
                    null
                }
                bottom = bottomOffset.px
                padding = 5.px
                backgroundColor = 0xCC4F3C73.toInt()
                border(1.px, 0xFFE3D5F6.toInt())
                zIndex = 28
            }
        }) {
            text("fixed -> root viewport")
        }

        div({
            key = "positioned.sticky.surface"
            style = {
                border(1.px, 0xFF6A7D8F.toInt())
                padding = 5.px
                display = Display.Flex
                flexDirection = FlexDirection.Column
                gap = 3.px
            }
        }) {
            text("H. Sticky: in-flow slot + visual stick with per-axis nearest scroll container and direct-parent clamp.")
            text(
                "Inspector target key: positioned.sticky.xy.target",
                { style = { color = DEMO_MUTED } }
            )

            stickyVerticalGroup(
                onSetLastHover = { positionedDemoLastHover = it },
                onSetLastClick = { positionedDemoLastClick = it },
                stickyTopClicks = positionedDemoStickyTopClicks,
                onStickyTopClick = { positionedDemoStickyTopClicks += 1 }
            )
            stickyHorizontalGroup()
            stickyXYGroup(
                onSetLastHover = { positionedDemoLastHover = it },
                onSetLastClick = { positionedDemoLastClick = it },
                stickyCombinedClicks = positionedDemoStickyCombinedClicks,
                onStickyCombinedClick = { positionedDemoStickyCombinedClicks += 1 }
            )
            stickyNoInsets()
            stickyClamp()
        }

        text("E/G. z-index + hit-testing: topmost visible positioned node should win input.")
        div({
            key = "positioned.z.overlap"
            style = {
                position = PositionMode.Relative
                border(1.px, 0xFF6F8498.toInt())
                backgroundColor = 0xFF283340.toInt()
            }
        }) {
            positionedOverlapCard(
                key = "positioned.z.blue",
                label = "blue z=$zBlue",
                left = 8,
                top = 6,
                zIndex = zBlue,
                color = CARD_BLUE,
                onHover = { positionedDemoLastHover = "blue" },
                onClick = {
                    positionedDemoBlueClicks += 1
                    positionedDemoLastClick = "blue"
                }
            )
            positionedOverlapCard(
                key = "positioned.z.green",
                label = "green z=$zGreen",
                left = 34,
                top = 20,
                zIndex = zGreen,
                color = CARD_GREEN,
                onHover = { positionedDemoLastHover = "green" },
                onClick = {
                    positionedDemoGreenClicks += 1
                    positionedDemoLastClick = "green"
                }
            )
            positionedOverlapCard(
                key = "positioned.z.red",
                label = "red z=$zRed",
                left = 60,
                top = 34,
                zIndex = zRed,
                color = CARD_RED,
                onHover = { positionedDemoLastHover = "red" },
                onClick = {
                    positionedDemoRedClicks += 1
                    positionedDemoLastClick = "red"
                }
            )
        }
        text(
            "clicks blue=${positionedDemoBlueClicks}, green=${positionedDemoGreenClicks}, red=${positionedDemoRedClicks}",
            { style = { color = DEMO_MUTED } }
        )

        div({
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
                gap = 3.px
            }
        }) {
            button(
                if (positionedDemoTieSwap) "tie order: second->first" else "tie order: first->second",
                {
                    onMouseClick = { positionedDemoTieSwap = !positionedDemoTieSwap }
                }
            )
            text(
                "same z, later DOM child should win overlap hit",
                { style = { color = DEMO_MUTED } }
            )
        }

        div({
            key = "positioned.tie.sample"
            style = {
                position = PositionMode.Relative
                border(1.px, 0xFF6C7F91.toInt())
                backgroundColor = 0xFF283440.toInt()
            }
        }) {
            if (positionedDemoTieSwap) {
                positionedTieCard(
                    label = "second",
                    left = 28,
                    top = 16,
                    zIndex = 3,
                    color = 0xFF54718F.toInt(),
                    onSetLastHover = { positionedDemoLastHover = it },
                    onTieClick = {
                        positionedDemoTieSecondClicks += 1
                        positionedDemoLastClick = it
                    }
                )
                positionedTieCard(
                    label = "first",
                    left = 16,
                    top = 8,
                    zIndex = 3,
                    color = 0xFF6B8CB0.toInt(),
                    onSetLastHover = { positionedDemoLastHover = it },
                    onTieClick = {
                        positionedDemoTieFirstClicks += 1
                        positionedDemoLastClick = it
                    }
                )
            } else {
                positionedTieCard(
                    label = "first",
                    left = 16,
                    top = 8,
                    zIndex = 3,
                    color = 0xFF6B8CB0.toInt(),
                    onSetLastHover = { positionedDemoLastHover = it },
                    onTieClick = {
                        positionedDemoTieFirstClicks += 1
                        positionedDemoLastClick = it
                    }
                )
                positionedTieCard(
                    label = "second",
                    left = 28,
                    top = 16,
                    zIndex = 3,
                    color = 0xFF54718F.toInt(),
                    onSetLastHover = { positionedDemoLastHover = it },
                    onTieClick = {
                        positionedDemoTieSecondClicks += 1
                        positionedDemoLastClick = it
                    }
                )
            }
        }
        text(
            "tie clicks first=${positionedDemoTieFirstClicks}, second=${positionedDemoTieSecondClicks}",
            { style = { color = DEMO_MUTED } }
        )

        text("Mixed static vs positioned overlap (stage rule).")
        div({
            key = "positioned.mixed.sample"
            style = {
                position = PositionMode.Relative
                border(1.px, 0xFF6E8196.toInt())
                backgroundColor = 0xFF293541.toInt()
            }
        }) {
            div({
                key = "positioned.mixed.static"
                onMouseEnter = { positionedDemoLastHover = "mixed-static" }
                onMouseClick = {
                    positionedDemoMixedStaticClicks += 1
                    positionedDemoLastClick = "mixed-static"
                }
                style = {
                    position = PositionMode.Static
                    zIndex = 999
                    padding = 5.px
                    backgroundColor = 0xFF667F9A.toInt()
                    border(1.px, 0xFFC4D8EB.toInt())
                }
            }) {
                text("static z=999")
            }
            div({
                key = "positioned.mixed.positioned"
                onMouseEnter = { positionedDemoLastHover = "mixed-positioned" }
                onMouseClick = {
                    positionedDemoMixedPositionedClicks += 1
                    positionedDemoLastClick = "mixed-positioned"
                }
                style = {
                    position = PositionMode.Relative
                    left = 18.px
                    top = 12.px
                    zIndex = -100
                    padding = 5.px
                    backgroundColor = 0xCC2F536F.toInt()
                    border(1.px, 0xFFB8D7EE.toInt())
                }
            }) {
                text("positioned z=-100")
            }
        }
        text(
            "mixed clicks static=${positionedDemoMixedStaticClicks}, positioned=${positionedDemoMixedPositionedClicks}",
            { style = { color = DEMO_MUTED; minHeight = 1.em } }
        )
        repeat(40) {
            div({
                style = {
                    padding = 1.px
                    border(1.px, 0xFF617A90.toInt())
                }
            }) {
                text("Hi there, #$it pyj", { style { fontSize(it.px) } })
            }
        }
    }
}

private fun UiScope.stickyVerticalGroup(
    onSetLastHover: (String) -> Unit,
    onSetLastClick: (String) -> Unit,
    stickyTopClicks: Int,
    onStickyTopClick: () -> Unit
) {
    div({
        key = "positioned.sticky.vertical.group"
        style = {
            border(1.px, 0xFF6C8096.toInt())
            padding = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
            gap = 2.px
        }
    }) {
        text("Vertical sticky basics: top=0, bottom=0, top+bottom => top wins")
        div({
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
                gap = 3.px
            }
        }) {
            div({
                style = {
                    width = 50.percent
                    border(1.px, 0xFF6A7E90.toInt())
                    padding = 3.px
                }
            }) {
                text("top=0 (interactive)")
                div({
                    key = "positioned.sticky.vertical.top.scroller"
                    style = {
                        overflowY = Overflow.Auto
                        border(1.px, 0xFF8097AB.toInt())
                        maxHeight = 7.em
                        display = Display.Flex
                        flexDirection = FlexDirection.Column
                        gap = 1.px
                        padding = 2.px
                    }
                }) {
                    button("sticky top action", {
                        key = "positioned.sticky.vertical.top.target"
                        onMouseEnter = { onSetLastHover("sticky-top") }
                        onMouseClick = {
                            onStickyTopClick()
                            onSetLastClick("sticky-top")
                        }
                        style = {
                            position = PositionMode.Sticky
                            top = 0.px
                            zIndex = 6
                        }
                    })
                    repeat(14) { line ->
                        text("top sticky line ${line + 1}")
                    }
                }
            }
            div({
                style = {
                    width = 50.percent
                    border(1.px, 0xFF6A7E90.toInt())
                    padding = 3.px
                }
            }) {
                text("bottom=0")
                div({
                    key = "positioned.sticky.vertical.bottom.scroller"
                    style = {
                        overflowY = Overflow.Auto
                        border(1.px, 0xFF8097AB.toInt())
                        maxHeight = 7.em
                        display = Display.Flex
                        flexDirection = FlexDirection.Column
                        gap = 1.px
                        padding = 2.px
                    }
                }) {
                    repeat(12) { line ->
                        text("bottom sticky line ${line + 1}")
                    }
                    div({
                        key = "positioned.sticky.vertical.bottom.target"
                        style = {
                            position = PositionMode.Sticky
                            bottom = 0.px
                            zIndex = 5
                            padding = 3.px
                            border(1.px, 0xFF9FC2DF.toInt())
                            backgroundColor = 0xCC446181.toInt()
                        }
                    }) {
                        text("sticky bottom block")
                    }
                    repeat(8) { line ->
                        text("tail line ${line + 1}")
                    }
                }
            }
        }
        div({
            key = "positioned.sticky.vertical.precedence.scroller"
            style = {
                overflowY = Overflow.Auto
                border(1.px, 0xFF8097AB.toInt())
                maxHeight = 6.em
                display = Display.Flex
                flexDirection = FlexDirection.Column
                gap = 1.px
                padding = 2.px
            }
        }) {
            div({
                key = "positioned.sticky.vertical.precedence.target"
                style = {
                    position = PositionMode.Sticky
                    top = 6.px
                    bottom = 0.px
                    zIndex = 5
                    padding = 3.px
                    border(1.px, 0xFF9FC2DF.toInt())
                    backgroundColor = 0xCC3F5871.toInt()
                }
            }) { text("top+bottom set -> top wins (top=6)") }
            repeat(10) { line -> text("precedence line ${line + 1}") }
        }
        text(
            "sticky top clicks=$stickyTopClicks",
            { style = { color = DEMO_MUTED } }
        )
    }
}

private fun UiScope.stickyHorizontalGroup() {
    div({
        key = "positioned.sticky.horizontal.group"
        style = {
            border(1.px, 0xFF6C8096.toInt())
            padding = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
            gap = 2.px
        }
    }) {
        text("Horizontal sticky basics: left=0, right=0, left+right => left wins")
        div({
            key = "positioned.sticky.horizontal.left.scroller"
            style = {
                overflowX = Overflow.Auto
                border(1.px, 0xFF8097AB.toInt())
                display = Display.Flex
                flexDirection = FlexDirection.Row
                gap = 2.px
                padding = 2.px
            }
        }) {
            div({
                key = "positioned.sticky.horizontal.left.target"
                style = {
                    position = PositionMode.Sticky
                    left = 0.px
                    zIndex = 5
                    padding = 3.px
                    border(1.px, 0xFF9FC2DF.toInt())
                    backgroundColor = 0xCC3F617B.toInt()
                }
            }) { text("left=0") }
            repeat(16) { idx ->
                text("left track ${idx + 1}")
            }
        }
        div({
            key = "positioned.sticky.horizontal.right.scroller"
            style = {
                overflowX = Overflow.Auto
                border(1.px, 0xFF8097AB.toInt())
                display = Display.Flex
                flexDirection = FlexDirection.Row
                gap = 2.px
                padding = 2.px
            }
        }) {
            repeat(10) { idx ->
                text("right track ${idx + 1}")
            }
            div({
                key = "positioned.sticky.horizontal.right.target"
                style = {
                    position = PositionMode.Sticky
                    right = 0.px
                    zIndex = 5
                    padding = 3.px
                    border(1.px, 0xFF9FC2DF.toInt())
                    backgroundColor = 0xCC45637F.toInt()
                }
            }) { text("right=0") }
            repeat(10) { idx ->
                text("tail ${idx + 1}")
            }
        }
        div({
            key = "positioned.sticky.horizontal.precedence.scroller"
            style = {
                overflowX = Overflow.Auto
                border(1.px, 0xFF8097AB.toInt())
                display = Display.Flex
                flexDirection = FlexDirection.Row
                gap = 2.px
                padding = 2.px
            }
        }) {
            div({
                key = "positioned.sticky.horizontal.precedence.target"
                style = {
                    position = PositionMode.Sticky
                    left = 8.px
                    right = 0.px
                    zIndex = 5
                    padding = 3.px
                    border(1.px, 0xFF9FC2DF.toInt())
                    backgroundColor = 0xCC415A74.toInt()
                }
            }) { text("left+right set -> left wins (left=8)") }
            repeat(14) { idx ->
                text("precedence ${idx + 1}")
            }
        }
    }

}

private fun UiScope.stickyXYGroup(
    onSetLastHover: (String) -> Unit,
    onSetLastClick: (String) -> Unit,
    stickyCombinedClicks: Int,
    onStickyCombinedClick: () -> Unit
) {
    div({
        key = "positioned.sticky.xy.group"
        style = {
            border(1.px, 0xFF6C8096.toInt())
            padding = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
            gap = 2.px
        }
    }) {
        text("Combined-axis sticky: left=0 + top=0 (render/interaction/Inspector target)")
        div({
            key = "positioned.sticky.xy.scroller"
            style = {
                overflowX = Overflow.Auto
                overflowY = Overflow.Auto
                border(1.px, 0xFF8097AB.toInt())
                maxHeight = 7.em
                display = Display.Flex
                flexDirection = FlexDirection.Column
                gap = 2.px
                padding = 2.px
            }
        }) {
            button("sticky x+y target", {
                key = "positioned.sticky.xy.target"
                onMouseEnter = { onSetLastHover("sticky-xy") }
                onMouseClick = {
                    onStickyCombinedClick()
                    onSetLastClick("sticky-xy")
                }
                style = {
                    position = PositionMode.Sticky
                    left = 0.px
                    top = 0.px
                    zIndex = 7
                }
            })
            repeat(16) { line ->
                text("xy sticky line ${line + 1} ....................................................")
            }
        }
        text(
            "sticky x+y clicks=$stickyCombinedClicks",
            { style = { color = DEMO_MUTED } }
        )
    }
}

private fun UiScope.stickyNoInsets() {
    div({
        key = "positioned.sticky.inactive.group"
        style = {
            border(1.px, 0xFF6C8096.toInt())
            padding = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
            gap = 2.px
        }
    }) {
        text("Inactive comparison: position=sticky with no insets stays inactive on both axes.")
        div({
            key = "positioned.sticky.inactive.scroller"
            style = {
                overflowX = Overflow.Auto
                overflowY = Overflow.Auto
                border(1.px, 0xFF8097AB.toInt())
                maxHeight = 6.em
                display = Display.Flex
                flexDirection = FlexDirection.Column
                gap = 1.px
                padding = 2.px
            }
        }) {
            div({
                key = "positioned.sticky.inactive.target"
                style = {
                    position = PositionMode.Sticky
                    padding = 3.px
                    border(1.px, 0xFF9FC2DF.toInt())
                    backgroundColor = 0xCC455F78.toInt()
                }
            }) { text("sticky without insets") }
            repeat(12) { line ->
                text("inactive line ${line + 1} .............................")
            }
        }
    }
}

private fun UiScope.stickyClamp() {
    div({
        key = "positioned.sticky.clamp.group"
        style = {
            border(1.px, 0xFF6C8096.toInt())
            padding = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
            gap = 2.px
        }
    }) {
        text("Containment clamp: sticky movement is clamped by direct parent containing block.")
        div({
            key = "positioned.sticky.clamp.scroller"
            style = {
                overflowY = Overflow.Auto
                border(1.px, 0xFF8097AB.toInt())
                maxHeight = 7.em
                display = Display.Flex
                flexDirection = FlexDirection.Column
                gap = 2.px
                padding = 2.px
            }
        }) {
            repeat(6) { idx -> text("outer line ${idx + 1}") }
            div({
                key = "positioned.sticky.clamp.parent"
                style = {
                    border(1.px, 0xFF8FA5B9.toInt())
                    backgroundColor = 0xFF2F3D4C.toInt()
                    maxHeight = 6.em
                    overflowY = Overflow.Auto
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                    gap = 1.px
                    padding = 2.px
                }
            }) {
                div({
                    key = "positioned.sticky.clamp.target"
                    style = {
                        position = PositionMode.Sticky
                        top = 0.px
                        zIndex = 6
                        padding = 3.px
                        border(1.px, 0xFF9FC2DF.toInt())
                        backgroundColor = 0xCC3F5A74.toInt()
                    }
                }) { text("clamped sticky top") }
                repeat(14) { idx -> text("inner clamp line ${idx + 1}") }
            }
            repeat(8) { idx -> text("outer tail ${idx + 1}") }
        }
    }
}

data class ControlsProps(
    val modeIndex: Int,
    val demoMode: PositionMode,
    val useLeft: Boolean,
    val useTop: Boolean,
    val leftOffset: Int,
    val rightOffset: Int,
    val topOffset: Int,
    val bottomOffset: Int,
    val zBlue: Int,
    val zGreen: Int,
    val zRed: Int,
    val lastHover: String,
    val lastClick: String,
    val onModeCycle: () -> Unit,
    val onToggleUseLeft: () -> Unit,
    val onToggleUseTop: () -> Unit,
    val onSetLeft: (Long) -> Unit,
    val onSetRight: (Long) -> Unit,
    val onSetTop: (Long) -> Unit,
    val onSetBottom: (Long) -> Unit,
    val onSetZBlue: (Long) -> Unit,
    val onSetZGreen: (Long) -> Unit,
    val onSetZRed: (Long) -> Unit,
    val onReset: () -> Unit
)

private fun UiScope.controls(props: ControlsProps) {
    div({
        key = "positioned.controls"
        style = {
            display = Display.Flex
            flexDirection = FlexDirection.Column
            gap = 3.px
            padding = 5.px
            border(1.px, 0xFF617A90.toInt())
            backgroundColor = 0xFF2A3541.toInt()
            position = PositionMode.Sticky
            top = 0.px
            zIndex = 999
        }
    }) {
        div({
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
                gap = 10.px
            }
        }) {
            button("mode=${props.demoMode.name.lowercase()}", {
                onMouseClick = { props.onModeCycle() }
            })
            button(
                if (props.useLeft) "h: left first" else "h: right fallback",
                {
                    onMouseClick = { props.onToggleUseLeft() }
                }
            )
            button(
                if (props.useTop) "v: top first" else "v: bottom fallback",
                {
                    onMouseClick = { props.onToggleUseTop() }
                }
            )
            button("Reset", {
                onMouseClick = { props.onReset() }
            })
            select {
                for (i in 0..5) {
                    option("$i", "$i")
                }
            }
            input(type = InputType.Text())
            input(type = InputType.Checkbox(listOf(1, 2, 3).map { InputOption(id = "$it", label = "$it") }))
            input(type = InputType.Radio(listOf(1, 2, 3).map { InputOption(id = "$it", label = "$it") }))
            input(type = InputType.Number())
        }

        positionedRangeControl(
            label = "left",
            key = "positioned.controls.left",
            value = props.leftOffset.toLong(),
            min = OFFSET_MIN.toLong(),
            max = OFFSET_MAX.toLong(),
            onChange = props.onSetLeft
        )
        positionedRangeControl(
            label = "right",
            key = "positioned.controls.right",
            value = props.rightOffset.toLong(),
            min = 0,
            max = OFFSET_MAX.toLong(),
            onChange = props.onSetRight
        )
        positionedRangeControl(
            label = "top",
            key = "positioned.controls.top",
            value = props.topOffset.toLong(),
            min = OFFSET_MIN.toLong(),
            max = OFFSET_MAX.toLong(),
            onChange = props.onSetTop
        )
        positionedRangeControl(
            label = "bottom",
            key = "positioned.controls.bottom",
            value = props.bottomOffset.toLong(),
            min = 0,
            max = OFFSET_MAX.toLong(),
            onChange = props.onSetBottom
        )
        positionedRangeControl(
            label = "z blue",
            key = "positioned.controls.zBlue",
            value = props.zBlue.toLong(),
            min = Z_MIN.toLong(),
            max = Z_MAX.toLong(),
            onChange = props.onSetZBlue
        )
        positionedRangeControl(
            label = "z green",
            key = "positioned.controls.zGreen",
            value = props.zGreen.toLong(),
            min = Z_MIN.toLong(),
            max = Z_MAX.toLong(),
            onChange = props.onSetZGreen
        )
        positionedRangeControl(
            label = "z red",
            key = "positioned.controls.zRed",
            value = props.zRed.toLong(),
            min = Z_MIN.toLong(),
            max = Z_MAX.toLong(),
            onChange = props.onSetZRed
        )
        text(
            "hover=${props.lastHover} click=${props.lastClick}",
            { style = { color = DEMO_MUTED } }
        )
    }
}

private fun UiScope.positionedRangeControl(
    label: String,
    key: String,
    value: Long,
    min: Long,
    max: Long,
    onChange: (Long) -> Unit
) {
    div({
        this.key = "$key-container"
        style = {
            display = Display.Flex
            flexDirection = FlexDirection.Row
            width = 100.percent
            justifyContent = JustifyContent.Start
        }
    }) {
        text("$label = $value", {
            style = {
                color = DEMO_MUTED
                width = 10.percent
            }
        })
        input(
            InputType.Range(
                value = value,
                min = min,
                max = max,
                step = 1
            ),
            {
                this.key = key
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: value
                    onChange(next.coerceIn(min, max))
                }
                style = {
                    width = 90.percent
                }
            }
        )
    }
}

private fun UiScope.positionedOverlapCard(
    key: String,
    label: String,
    left: Int,
    top: Int,
    zIndex: Int,
    color: Int,
    onHover: () -> Unit,
    onClick: () -> Unit
) {
    div({
        this.key = key
        onMouseEnter = { onHover() }
        onMouseClick = { onClick() }
        style = {
            position = PositionMode.Absolute
            this.left = left.px
            this.top = top.px
            padding = 5.px
            backgroundColor = color
            border(1.px, 0xFFE6F1FD.toInt())
            this.zIndex = zIndex
        }
    }) {
        text(label)
    }
}

private fun UiScope.positionedTieCard(
    label: String,
    left: Int,
    top: Int,
    zIndex: Int,
    color: Int,
    onSetLastHover: (String) -> Unit,
    onTieClick: (String) -> Unit
) {
    div({
        key = "positioned.tie.$label"
        onMouseEnter = { onSetLastHover("tie-$label") }
        onMouseClick = { onTieClick("tie-$label") }
        style = {
            position = PositionMode.Absolute
            this.left = left.px
            this.top = top.px
            padding = 5.px
            backgroundColor = color
            border(1.px, 0xFFDDEDFD.toInt())
            this.zIndex = zIndex
        }
    }) {
        text("$label z=$zIndex")
    }
}



