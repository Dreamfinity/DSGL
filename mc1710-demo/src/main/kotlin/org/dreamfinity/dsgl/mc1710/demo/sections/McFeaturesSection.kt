package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.mcFeaturesSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.mcFeatures"
        style = {
            width = contentWidth
            height = contentHeight
            gap = 4

            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Image sources: resource + file:// + http(s):// cached path.")
        text(
            "mediaReady=${window.mediaReady} file=${
                if (window.mediaReady) "prepared" else "failed"
            }",
            { style = { color = DEMO_MUTED } }
        )

        div({
            style = {
                gap = 3
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            div({
                key = "mc.image.resource.col"
                style = {
                    gap = 2
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Resource", { style = { color = DEMO_MUTED } })
                img(window.resourceImageSource, {
                    key = "mc.image.resource"
                    style = {
                        width = 36
                        height = 36
                        border(1, 0xFF66737F.toInt())
                    }
                })
            }
            div({
                key = "mc.image.file.col"
                style = {
                    gap = 2
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("file://", { style = { color = DEMO_MUTED } })
                img(window.fileImageSource, {
                    key = "mc.image.file"
                    style = {
                        width = 36
                        height = 36
                        border(1, 0xFF66737F.toInt())
                    }
                })
            }
            div({
                key = "mc.image.http.col"
                style = {
                    gap = 2
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("http://", { style = { color = DEMO_MUTED } })
                img(window.httpImageSource, {
                    key = "mc.image.http"
                    style = {
                        width = 36
                        height = 36
                        border(1, 0xFF66737F.toInt())
                    }
                })
            }
        }

        text("Item stack render modes (2D item + 3D block)")
        div({
            key = "mc.items.row"
            style = {
                gap = 10
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            itemStack(window.flatItemRef, {
                size = 18
                key = "mc.item.2d"
                style = {
                    width = 64
                    border(1, 0xFF586A7A.toInt())
                }
            })
            itemStack(window.blockItemRef, {
                size = 20
                rotYDeg = window.itemRotY
                rotXDeg = window.itemRotX
                key = "mc.item.3d"
                style = {
                    width = 70
                    border(1, 0xFF586A7A.toInt())
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
                style = { width = contentWidth - 10 }
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
                style = { width = contentWidth - 10 }
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
                gap = 4
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Y-15", {
                style = { width = 36 }
                onMouseClick = { window.adjustItemRotation(deltaY = -15.0) }
            })
            button("Y+15", {
                style = { width = 36 }
                onMouseClick = { window.adjustItemRotation(deltaY = 15.0) }
            })
            button("X-10", {
                style = { width = 36 }
                onMouseClick = { window.adjustItemRotation(deltaX = -10.0) }
            })
            button("X+10", {
                style = { width = 36 }
                onMouseClick = { window.adjustItemRotation(deltaX = 10.0) }
            })
            button("Reset", {
                style = { width = 42 }
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
