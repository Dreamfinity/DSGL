package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.style.*
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private val POSITION_MODE_OPTIONS = listOf(
    PositionMode.Static,
    PositionMode.Relative,
    PositionMode.Absolute,
    PositionMode.Fixed
)

private const val OFFSET_MIN = -72
private const val OFFSET_MAX = 120
private const val Z_MIN = -8
private const val Z_MAX = 12

private const val CARD_BLUE = 0xFF355E91.toInt()
private const val CARD_GREEN = 0xFF3E7A56.toInt()
private const val CARD_RED = 0xFF8A4A44.toInt()

fun UiScope.positionedLayoutSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val modeIndex = window.positionedDemoModeIndex.toInt().coerceIn(0, POSITION_MODE_OPTIONS.lastIndex)
    val demoMode = POSITION_MODE_OPTIONS[modeIndex]

    val leftOffset = window.positionedDemoLeft.toInt().coerceIn(OFFSET_MIN, OFFSET_MAX)
    val topOffset = window.positionedDemoTop.toInt().coerceIn(OFFSET_MIN, OFFSET_MAX)
    val rightOffset = window.positionedDemoRight.toInt().coerceIn(0, OFFSET_MAX)
    val bottomOffset = window.positionedDemoBottom.toInt().coerceIn(0, OFFSET_MAX)
    val zBlue = window.positionedDemoZBlue.toInt().coerceIn(Z_MIN, Z_MAX)
    val zGreen = window.positionedDemoZGreen.toInt().coerceIn(Z_MIN, Z_MAX)
    val zRed = window.positionedDemoZRed.toInt().coerceIn(Z_MIN, Z_MAX)

    val rootAnchoredLeft = (window.viewportWidthPx / 2).coerceAtLeast(112)
    val rootAnchoredTop = 58
    val fixedBaseLeft = (window.viewportWidthPx - 236).coerceAtLeast(104)
    val fixedBaseTop = 72

    val resetPositions = { _: MouseClickEvent ->
        window.positionedDemoModeIndex = 1L
        window.positionedDemoUseLeft = true
        window.positionedDemoUseTop = true
        window.positionedDemoLeft = 24L
        window.positionedDemoTop = 14L
        window.positionedDemoRight = 26L
        window.positionedDemoBottom = 18L
        window.positionedDemoZBlue = 1L
        window.positionedDemoZGreen = 4L
        window.positionedDemoZRed = 2L
        window.positionedDemoTieSwap = false
        window.positionedDemoLastHover = "none"
        window.positionedDemoLastClick = "none"
        window.positionedDemoBlueClicks = 0
        window.positionedDemoGreenClicks = 0
        window.positionedDemoRedClicks = 0
        window.positionedDemoTieFirstClicks = 0
        window.positionedDemoTieSecondClicks = 0
        window.positionedDemoMixedStaticClicks = 0
        window.positionedDemoMixedPositionedClicks = 0
        window.positionedDemoScrollClicks = 0
    }

    div({
        key = "section.positionedLayout"
        style = {
            display = Display.Flex
            flexDirection = FlexDirection.Column
            maxHeight = 90.vh
            gap = 4.px
            overflowY = Overflow.Auto
        }
    }) {
        text("Positioned layout verification surface: static/relative/absolute/fixed + z-index + scroll + hit-testing")
        text(
            "Top-level fixed and root-anchored absolute badges are intentional: they prove root-space anchoring.",
            { style = { color = DEMO_MUTED } }
        )

        div({
            key = "positioned.controls"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Column
                gap = 3.px
                padding = 5.px
                border(1.px, 0xFF617A90.toInt())
                backgroundColor = 0xFF2A3541.toInt()
            }
        }) {
            div({
                style = {
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                    gap = 10.px
                }
            }) {
                button("mode=${demoMode.name.lowercase()}", {
                    onMouseClick = {
                        window.positionedDemoModeIndex = ((modeIndex + 1) % POSITION_MODE_OPTIONS.size).toLong()
                    }
                })
                button(
                    if (window.positionedDemoUseLeft) "h: left first" else "h: right fallback",
                    {
                        onMouseClick = { window.positionedDemoUseLeft = !window.positionedDemoUseLeft }
                    }
                )
                button(
                    if (window.positionedDemoUseTop) "v: top first" else "v: bottom fallback",
                    {
                        onMouseClick = { window.positionedDemoUseTop = !window.positionedDemoUseTop }
                    }
                )
                button("Reset", {
                    onMouseClick = resetPositions
                })
            }

            positionedRangeControl(
                label = "left",
                key = "positioned.controls.left",
                value = leftOffset.toLong(),
                min = OFFSET_MIN.toLong(),
                max = OFFSET_MAX.toLong(),
                controlWidth = 0,
                onChange = { window.positionedDemoLeft = it }
            )
            positionedRangeControl(
                label = "right",
                key = "positioned.controls.right",
                value = rightOffset.toLong(),
                min = 0,
                max = OFFSET_MAX.toLong(),
                controlWidth = 0,
                onChange = { window.positionedDemoRight = it }
            )
            positionedRangeControl(
                label = "top",
                key = "positioned.controls.top",
                value = topOffset.toLong(),
                min = OFFSET_MIN.toLong(),
                max = OFFSET_MAX.toLong(),
                controlWidth = 0,
                onChange = { window.positionedDemoTop = it }
            )
            positionedRangeControl(
                label = "bottom",
                key = "positioned.controls.bottom",
                value = bottomOffset.toLong(),
                min = 0,
                max = OFFSET_MAX.toLong(),
                controlWidth = 0,
                onChange = { window.positionedDemoBottom = it }
            )
            positionedRangeControl(
                label = "z blue",
                key = "positioned.controls.zBlue",
                value = zBlue.toLong(),
                min = Z_MIN.toLong(),
                max = Z_MAX.toLong(),
                controlWidth = 0,
                onChange = { window.positionedDemoZBlue = it }
            )
            positionedRangeControl(
                label = "z green",
                key = "positioned.controls.zGreen",
                value = zGreen.toLong(),
                min = Z_MIN.toLong(),
                max = Z_MAX.toLong(),
                controlWidth = 0,
                onChange = { window.positionedDemoZGreen = it }
            )
            positionedRangeControl(
                label = "z red",
                key = "positioned.controls.zRed",
                value = zRed.toLong(),
                min = Z_MIN.toLong(),
                max = Z_MAX.toLong(),
                controlWidth = 0,
                onChange = { window.positionedDemoZRed = it }
            )
            text(
                "hover=${window.positionedDemoLastHover} click=${window.positionedDemoLastClick}",
                { style = { color = DEMO_MUTED } }
            )
        }

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
                    onMouseEnter = { window.positionedDemoLastHover = "mode-$demoMode" }
                    onMouseClick = { window.positionedDemoLastClick = "mode-$demoMode" }
                    style = {
                        width = 100.px
                        height = 50.px
                        position = demoMode
                        left = if (window.positionedDemoUseLeft) {
                            leftOffset.px
                        } else {
                            null
                        }
                        right = rightOffset.px
                        top = if (window.positionedDemoUseTop) {
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
                    onMouseEnter = { window.positionedDemoLastHover = "static-target" }
                    onMouseClick = {
                        window.positionedDemoLastClick = "static-target"
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
                    onMouseEnter = { window.positionedDemoLastHover = "relative-target" }
                    onMouseClick = { window.positionedDemoLastClick = "relative-target" }
                    style = {
                        position = PositionMode.Relative
                        left = if (window.positionedDemoUseLeft) {
                            leftOffset.px
                        } else {
                            null
                        }
                        right = rightOffset.px
                        top = if (window.positionedDemoUseTop) {
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
                        onMouseEnter = { window.positionedDemoLastHover = "absolute-inside" }
                        onMouseClick = {
                            window.positionedDemoLastClick = "absolute-inside"
                        }
                        style = {
                            position = PositionMode.Absolute
                            left = if (window.positionedDemoUseLeft) {
                                leftOffset.px
                            } else {
                                null
                            }
                            right = rightOffset.px
                            top = if (window.positionedDemoUseTop) {
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
                onMouseEnter = { window.positionedDemoLastHover = "absolute-root" }
                onMouseClick = { window.positionedDemoLastClick = "absolute-root" }
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
                        window.positionedDemoScrollClicks += 1
                        window.positionedDemoLastClick = "scroll-action"
                    }
                })
                text("scroll action clicks=${window.positionedDemoScrollClicks}", { style = { color = DEMO_MUTED } })
            }
        }

        div({
            key = "positioned.fixed.badge"
            onMouseEnter = { window.positionedDemoLastHover = "fixed-badge" }
            onMouseClick = { window.positionedDemoLastClick = "fixed-badge" }
            style = {
                position = PositionMode.Fixed
                left = if (window.positionedDemoUseLeft) {
                    (fixedBaseLeft + leftOffset).coerceAtLeast(0).px
                } else {
                    null
                }
                right = rightOffset.px
                top = if (window.positionedDemoUseTop) {
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
                onHover = { window.positionedDemoLastHover = "blue" },
                onClick = {
                    window.positionedDemoBlueClicks += 1
                    window.positionedDemoLastClick = "blue"
                }
            )
            positionedOverlapCard(
                key = "positioned.z.green",
                label = "green z=$zGreen",
                left = 34,
                top = 20,
                zIndex = zGreen,
                color = CARD_GREEN,
                onHover = { window.positionedDemoLastHover = "green" },
                onClick = {
                    window.positionedDemoGreenClicks += 1
                    window.positionedDemoLastClick = "green"
                }
            )
            positionedOverlapCard(
                key = "positioned.z.red",
                label = "red z=$zRed",
                left = 60,
                top = 34,
                zIndex = zRed,
                color = CARD_RED,
                onHover = { window.positionedDemoLastHover = "red" },
                onClick = {
                    window.positionedDemoRedClicks += 1
                    window.positionedDemoLastClick = "red"
                }
            )
        }
        text(
            "clicks blue=${window.positionedDemoBlueClicks}, green=${window.positionedDemoGreenClicks}, red=${window.positionedDemoRedClicks}",
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
                if (window.positionedDemoTieSwap) "tie order: second->first" else "tie order: first->second",
                {
                    onMouseClick = { window.positionedDemoTieSwap = !window.positionedDemoTieSwap }
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
            if (window.positionedDemoTieSwap) {
                positionedTieCard(window, "second", 28, 16, 3, 0xFF54718F.toInt())
                positionedTieCard(window, "first", 16, 8, 3, 0xFF6B8CB0.toInt())
            } else {
                positionedTieCard(window, "first", 16, 8, 3, 0xFF6B8CB0.toInt())
                positionedTieCard(window, "second", 28, 16, 3, 0xFF54718F.toInt())
            }
        }
        text(
            "tie clicks first=${window.positionedDemoTieFirstClicks}, second=${window.positionedDemoTieSecondClicks}",
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
                onMouseEnter = { window.positionedDemoLastHover = "mixed-static" }
                onMouseClick = {
                    window.positionedDemoMixedStaticClicks += 1
                    window.positionedDemoLastClick = "mixed-static"
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
                onMouseEnter = { window.positionedDemoLastHover = "mixed-positioned" }
                onMouseClick = {
                    window.positionedDemoMixedPositionedClicks += 1
                    window.positionedDemoLastClick = "mixed-positioned"
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
            "mixed clicks static=${window.positionedDemoMixedStaticClicks}, positioned=${window.positionedDemoMixedPositionedClicks}",
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

private fun UiScope.positionedRangeControl(
    label: String,
    key: String,
    value: Long,
    min: Long,
    max: Long,
    controlWidth: Int,
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
    window: ShowcaseWindow,
    label: String,
    left: Int,
    top: Int,
    zIndex: Int,
    color: Int
) {
    div({
        key = "positioned.tie.$label"
        onMouseEnter = { window.positionedDemoLastHover = "tie-$label" }
        onMouseClick = {
            if (label == "first") {
                window.positionedDemoTieFirstClicks += 1
            } else {
                window.positionedDemoTieSecondClicks += 1
            }
            window.positionedDemoLastClick = "tie-$label"
        }
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

