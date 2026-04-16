package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.style.AlignItems
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.JustifyContent
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.mc1710.McItemStackRef
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import kotlin.math.roundToLong

data class McFeaturesShellProps(
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val mediaReady: Boolean,
    val resourceImageSource: String,
    val fileImageSource: String,
    val httpImageSource: String,
    val flatItemRef: McItemStackRef,
    val blockItemRef: McItemStackRef,
    val clippingScrollDemoText: String,
    val onClippingScrollDemoTextChange: (String) -> Unit,
    val currentGuiScale: () -> Int,
    val guiScaleLabel: (Int) -> String,
    val setGuiScale: (Int) -> Unit,
    val cycleGuiScale: (Int) -> Unit,
    val onLogHook: (String, Event, String?) -> Unit
)

fun UiScope.mcFeaturesSection(props: McFeaturesShellProps) {
    var itemRotY by useState(160.0)
    var itemRotX by useState(-11.0)

    fun itemRotYLong(): Long = itemRotY.roundToLong().coerceIn(0L, 360L)
    fun itemRotXLong(): Long = itemRotX.roundToLong().coerceIn(-89L, 89L)
    fun adjustItemRotation(deltaY: Double = 0.0, deltaX: Double = 0.0) {
        var normalized = (itemRotY + deltaY) % 360.0
        if (normalized < 0.0) normalized += 360.0
        itemRotY = normalized
        itemRotX = (itemRotX + deltaX).coerceIn(-89.0, 89.0)
    }

    val guiScaleValue = props.currentGuiScale()

    div({
        key = "section.mcFeatures"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text(
            "DSGL viewport=${props.viewportWidthPx}x${props.viewportHeightPx}px, guiScale=${props.guiScaleLabel(guiScaleValue)}",
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
                style {
                    backgroundColor = if (guiScaleValue == 0) 0xFF2F556E.toInt() else DsglColors.BUTTON
                }
                onMouseClick = { props.setGuiScale(0) }
            })
            button("1x", {
                style {
                    backgroundColor = if (guiScaleValue == 1) 0xFF2F556E.toInt() else DsglColors.BUTTON
                }
                onMouseClick = { props.setGuiScale(1) }
            })
            button("2x", {
                style {
                    backgroundColor = if (guiScaleValue == 2) 0xFF2F556E.toInt() else DsglColors.BUTTON
                }
                onMouseClick = { props.setGuiScale(2) }
            })
            button("3x", {
                style {
                    backgroundColor = if (guiScaleValue == 3) 0xFF2F556E.toInt() else DsglColors.BUTTON
                }
                onMouseClick = { props.setGuiScale(3) }
            })
            button("4x", {
                style {
                    backgroundColor = if (guiScaleValue == 4) 0xFF2F556E.toInt() else DsglColors.BUTTON
                }
                onMouseClick = { props.setGuiScale(4) }
            })
            button("-", {
                onMouseClick = { props.cycleGuiScale(-1) }
            })
            button("+", {
                onMouseClick = { props.cycleGuiScale(1) }
            })
        }

        text("Pixel board (1px borders) + nested layout + clipping + ItemStack positioning")
        div({
            key = "mc.pixel.board"
            style = {
                width = 100.percent
                maxWidth = 360.px
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
                        }) {}
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
                        itemStack(props.flatItemRef, {
                            key = "mc.pixel.item.topLeft"
                            size = 16
                            style = { width = 18.px }
                        })
                        itemStack(props.blockItemRef, {
                            key = "mc.pixel.item.topRight"
                            size = 16
                            rotYDeg = itemRotY
                            rotXDeg = itemRotX
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
                        itemStack(props.blockItemRef, {
                            key = "mc.pixel.item.center"
                            size = 20
                            rotYDeg = itemRotY
                            rotXDeg = itemRotX
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
                        itemStack(props.flatItemRef, {
                            key = "mc.pixel.item.bottomLeft"
                            size = 16
                            style = { width = 18.px }
                        })
                        itemStack(props.flatItemRef, {
                            key = "mc.pixel.item.bottomRight"
                            size = 16
                            style = { width = 18.px }
                        })
                    }
                }
                textarea({
                    key = "mc.pixel.clip.textarea"
                    placeholder = "Clipped/scrollable viewport check"
                    value = props.clippingScrollDemoText
                    style = {
                        flexGrow = 1f
                        minWidth = 96.px
                        height = 102.px
                    }
                    onInput = { event ->
                        props.onClippingScrollDemoTextChange(event.value)
                    }
                    onValueChange = { event ->
                        props.onClippingScrollDemoTextChange(event.value)
                    }
                })
            }
        }

        text("Image sources: resource + file:// + http(s):// cached path.")
        text(
            "mediaReady=${props.mediaReady} file=${if (props.mediaReady) "prepared" else "failed"}",
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
                img(props.resourceImageSource, {
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
                img(props.fileImageSource, {
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
                img(props.httpImageSource, {
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
            itemStack(props.flatItemRef, {
                size = 18
                key = "mc.item.2d"
                style = {
                    width = 64.px
                    border(1.px, 0xFF586A7A.toInt())
                }
            })
            itemStack(props.blockItemRef, {
                size = 20
                rotYDeg = itemRotY
                rotXDeg = itemRotX
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
                value = itemRotYLong(),
                min = 0,
                max = 360,
                step = 5
            ),
            {
                key = "mc.rotation.slider.yaw"
                style = { width = 100.percent }
                onMouseDown = { event ->
                    props.onLogHook("mc.rotY.onMouseDown", event, "capture-start")
                }
                onMouseUp = { event ->
                    props.onLogHook("mc.rotY.onMouseUp", event, "capture-end rotY=${itemRotYLong()}")
                }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: itemRotYLong()
                    itemRotY = next.toDouble()
                    props.onLogHook("mc.rotY.onInput", event, "rotY=${itemRotYLong()}")
                }
                onValueChange = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: itemRotYLong()
                    itemRotY = next.toDouble()
                    props.onLogHook("mc.rotY.onChange", event, "rotY=${itemRotYLong()}")
                }
            }
        )

        input(
            InputType.Range(
                value = itemRotXLong(),
                min = -89,
                max = 89,
                step = 1
            ),
            {
                key = "mc.rotation.slider.pitch"
                style = { width = 100.percent }
                onMouseDown = { event ->
                    props.onLogHook("mc.rotX.onMouseDown", event, "capture-start")
                }
                onMouseUp = { event ->
                    props.onLogHook("mc.rotX.onMouseUp", event, "capture-end rotX=${itemRotXLong()}")
                }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: itemRotXLong()
                    itemRotX = next.toDouble()
                    props.onLogHook("mc.rotX.onInput", event, "rotX=${itemRotXLong()}")
                }
                onValueChange = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: itemRotXLong()
                    itemRotX = next.toDouble()
                    props.onLogHook("mc.rotX.onChange", event, "rotX=${itemRotXLong()}")
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
                onMouseClick = { adjustItemRotation(deltaY = -15.0) }
            })
            button("Y+15", {
                onMouseClick = { adjustItemRotation(deltaY = 15.0) }
            })
            button("X-10", {
                onMouseClick = { adjustItemRotation(deltaX = -10.0) }
            })
            button("X+10", {
                onMouseClick = { adjustItemRotation(deltaX = 10.0) }
            })
            button("Reset", {
                onMouseClick = {
                    itemRotY = 160.0
                    itemRotX = -11.0
                }
            })
        }

        text(
            "rotY=${itemRotYLong()} rotX=${itemRotXLong()}",
            { style = { color = DEMO_MUTED } }
        )
    }
}
