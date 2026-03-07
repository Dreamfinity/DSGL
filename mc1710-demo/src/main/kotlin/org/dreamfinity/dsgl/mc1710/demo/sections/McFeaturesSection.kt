package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.AlignItems
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.JustifyContent
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.mcFeaturesSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.mcFeatures"
        style = {
            width = contentWidth.px
            height = contentHeight.px
            gap = 4.px

            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        val guiScaleValue = window.currentGuiScale()
        val boardWidth = (contentWidth - 10).coerceIn(220, 360)
        val boardRightColumnWidth = (boardWidth - 168).coerceAtLeast(96)

        text(
            "DSGL viewport=${window.viewportWidthPx}x${window.viewportHeightPx}px, guiScale=${
                window.guiScaleLabel(
                    guiScaleValue
                )
            }",
            { style = { color = DsglColors.WHITE } }
        )
        text(
            "Change guiScale below: vanilla UI changes, DSGL layout should stay pixel-stable.",
            { style = { color = DEMO_MUTED } }
        )
        div({
            key = "mc.guiScale.controls"
            style = {
                gap = 3.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Auto", {
                style = {
                    width = 42.px
                    backgroundColor = if (guiScaleValue == 0) 0xFF2F556E.toInt() else DsglColors.BUTTON
                }
                onMouseClick = { window.setGuiScale(0) }
            })
            button("1x", {
                style = {
                    width = 30.px
                    backgroundColor = if (guiScaleValue == 1) 0xFF2F556E.toInt() else DsglColors.BUTTON
                }
                onMouseClick = { window.setGuiScale(1) }
            })
            button("2x", {
                style = {
                    width = 30.px
                    backgroundColor = if (guiScaleValue == 2) 0xFF2F556E.toInt() else DsglColors.BUTTON
                }
                onMouseClick = { window.setGuiScale(2) }
            })
            button("3x", {
                style = {
                    width = 30.px
                    backgroundColor = if (guiScaleValue == 3) 0xFF2F556E.toInt() else DsglColors.BUTTON
                }
                onMouseClick = { window.setGuiScale(3) }
            })
            button("4x", {
                style = {
                    width = 30.px
                    backgroundColor = if (guiScaleValue == 4) 0xFF2F556E.toInt() else DsglColors.BUTTON
                }
                onMouseClick = { window.setGuiScale(4) }
            })
            button("-", {
                style = { width = 24.px }
                onMouseClick = { window.cycleGuiScale(-1) }
            })
            button("+", {
                style = { width = 24.px }
                onMouseClick = { window.cycleGuiScale(1) }
            })
        }

        text("Pixel board (1px borders) + nested layout + clipping + ItemStack positioning")
        div({
            key = "mc.pixel.board"
            style = {
                width = boardWidth.px
                padding = 4.px
                border(1.px, 0xFF5D6A76.toInt())
                backgroundColor = 0xFF1A222A.toInt()
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Column
            }
        }) {
            for (row in 0 until 4) {
                div({
                    style = {
                        gap = 2.px
                        display = Display.Flex
                        flexDirection = FlexDirection.Row
                    }
                }) {
                    for (col in 0 until 8) {
                        div({
                            style = {
                                width = 18.px
                                height = 10.px
                                border(1.px, 0xFF3F4B56.toInt())
                                backgroundColor = if ((row + col) % 2 == 0) 0xFF1F2D38.toInt() else 0xFF243544.toInt()
                            }
                        }) {
                        }
                    }
                }
            }

            div({
                key = "mc.pixel.board.content"
                style = {
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                div({
                    key = "mc.pixel.board.nested"
                    style = {
                        width = 160.px
                        height = 102.px
                        padding = 4.px
                        border(1.px, 0xFF6A7784.toInt())
                        backgroundColor = 0xFF111922.toInt()
                        gap = 3.px
                        display = Display.Flex
                        flexDirection = FlexDirection.Column
                    }
                }) {
                    div({
                        style = {
                            display = Display.Flex
                            flexDirection = FlexDirection.Row
                            justifyContent = JustifyContent.SpaceBetween
                        }
                    }) {
                        itemStack(window.flatItemRef, {
                            key = "mc.pixel.item.topLeft"
                            size = 16
                            style = { width = 18.px }
                        })
                        itemStack(window.blockItemRef, {
                            key = "mc.pixel.item.topRight"
                            size = 16
                            rotYDeg = window.itemRotY
                            rotXDeg = window.itemRotX
                            style = { width = 18.px }
                        })
                    }
                    div({
                        style = {
                            display = Display.Flex
                            flexDirection = FlexDirection.Row
                            justifyContent = JustifyContent.Center
                            alignItems = AlignItems.Center
                        }
                    }) {
                        itemStack(window.blockItemRef, {
                            key = "mc.pixel.item.center"
                            size = 20
                            rotYDeg = window.itemRotY
                            rotXDeg = window.itemRotX
                            style = {
                                width = 28.px
                                transform {
                                    rotate(5f)
                                }
                            }
                        })
                    }
                    div({
                        style = {
                            display = Display.Flex
                            flexDirection = FlexDirection.Row
                            justifyContent = JustifyContent.SpaceBetween
                        }
                    }) {
                        itemStack(window.flatItemRef, {
                            key = "mc.pixel.item.bottomLeft"
                            size = 16
                            style = { width = 18.px }
                        })
                        itemStack(window.flatItemRef, {
                            key = "mc.pixel.item.bottomRight"
                            size = 16
                            style = { width = 18.px }
                        })
                    }
                }
                textarea({
                    key = "mc.pixel.clip.textarea"
                    placeholder = "Clipped/scrollable viewport check"
                    value = window.clippingScrollDemoText
                    style = {
                        width = boardRightColumnWidth.px
                        height = 102.px
                    }
                    onInput = { event ->
                        window.clippingScrollDemoText = event.value
                    }
                    onValueChange = { event ->
                        window.clippingScrollDemoText = event.value
                    }
                })
            }
        }

        text("Image sources: resource + file:// + http(s):// cached path.")
        text(
            "mediaReady=${window.mediaReady} file=${
                if (window.mediaReady) "prepared" else "failed"
            }",
            { style = { color = DEMO_MUTED } }
        )

        div({
            style = {
                gap = 3.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            div({
                key = "mc.image.resource.col"
                style = {
                    gap = 2.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Resource", { style = { color = DEMO_MUTED } })
                img(window.resourceImageSource, {
                    key = "mc.image.resource"
                    style = {
                        width = 36.px
                        height = 36.px
                        border(1.px, 0xFF66737F.toInt())
                    }
                })
            }
            div({
                key = "mc.image.file.col"
                style = {
                    gap = 2.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("file://", { style = { color = DEMO_MUTED } })
                img(window.fileImageSource, {
                    key = "mc.image.file"
                    style = {
                        width = 36.px
                        height = 36.px
                        border(1.px, 0xFF66737F.toInt())
                    }
                })
            }
            div({
                key = "mc.image.http.col"
                style = {
                    gap = 2.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("http://", { style = { color = DEMO_MUTED } })
                img(window.httpImageSource, {
                    key = "mc.image.http"
                    style = {
                        width = 36.px
                        height = 36.px
                        border(1.px, 0xFF66737F.toInt())
                    }
                })
            }
        }

        text("Item stack render modes (2D item + 3D block)")
        div({
            key = "mc.items.row"
            style = {
                gap = 10.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            itemStack(window.flatItemRef, {
                size = 18
                key = "mc.item.2d"
                style = {
                    width = 64.px
                    border(1.px, 0xFF586A7A.toInt())
                }
            })
            itemStack(window.blockItemRef, {
                size = 20
                rotYDeg = window.itemRotY
                rotXDeg = window.itemRotX
                key = "mc.item.3d"
                style = {
                    width = 70.px
                    border(1.px, 0xFF586A7A.toInt())
                }
            })
        }

        text("Rotation controls (drag sliders or use step buttons)")
        text(
            "Drag outside slider bounds: value should keep updating until mouse up.",
            { style = { color = DEMO_MUTED } }
        )
        input(
            InputType.Range(
                value = window.itemRotYLong(),
                min = 0,
                max = 360,
                step = 5
            ),
            {
                key = "mc.rotation.slider.yaw"
                style = { width = (contentWidth - 10).px }
                onMouseDown = { event ->
                    window.logHook("mc.rotY.onMouseDown", event, "capture-start")
                }
                onMouseUp = { event ->
                    window.logHook("mc.rotY.onMouseUp", event, "capture-end rotY=${window.itemRotYLong()}")
                }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: window.itemRotYLong()
                    window.itemRotY = next.toDouble()
                    window.logHook("mc.rotY.onInput", event, "rotY=${window.itemRotYLong()}")
                }
                onValueChange = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: window.itemRotYLong()
                    window.itemRotY = next.toDouble()
                    window.logHook("mc.rotY.onChange", event, "rotY=${window.itemRotYLong()}")
                }
            }
        )

        input(
            InputType.Range(
                value = window.itemRotXLong(),
                min = -89,
                max = 89,
                step = 1
            ),
            {
                key = "mc.rotation.slider.pitch"
                style = { width = (contentWidth - 10).px }
                onMouseDown = { event ->
                    window.logHook("mc.rotX.onMouseDown", event, "capture-start")
                }
                onMouseUp = { event ->
                    window.logHook("mc.rotX.onMouseUp", event, "capture-end rotX=${window.itemRotXLong()}")
                }
                onInput = { event ->
                    val next =
                        (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: window.itemRotXLong()
                    window.itemRotX = next.toDouble()
                    window.logHook("mc.rotX.onInput", event, "rotX=${window.itemRotXLong()}")
                }
                onValueChange = { event ->
                    val next =
                        (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: window.itemRotXLong()
                    window.itemRotX = next.toDouble()
                    window.logHook("mc.rotX.onChange", event, "rotX=${window.itemRotXLong()}")
                }
            }
        )

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Y-15", {
                style = { width = 36.px }
                onMouseClick = { window.adjustItemRotation(deltaY = -15.0) }
            })
            button("Y+15", {
                style = { width = 36.px }
                onMouseClick = { window.adjustItemRotation(deltaY = 15.0) }
            })
            button("X-10", {
                style = { width = 36.px }
                onMouseClick = { window.adjustItemRotation(deltaX = -10.0) }
            })
            button("X+10", {
                style = { width = 36.px }
                onMouseClick = { window.adjustItemRotation(deltaX = 10.0) }
            })
            button("Reset", {
                style = { width = 42.px }
                onMouseClick = {
                    window.itemRotY = 160.0
                    window.itemRotX = -11.0
                }
            })
        }

        text(
            "rotY=${window.itemRotYLong()} rotX=${window.itemRotXLong()}",
            { style = { color = DEMO_MUTED } }
        )
    }
}


