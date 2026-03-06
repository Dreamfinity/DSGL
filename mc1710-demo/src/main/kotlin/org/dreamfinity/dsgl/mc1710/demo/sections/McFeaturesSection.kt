package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.mcFeaturesSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.mcFeatures"
        width = contentWidth
        height = contentHeight
        gap = 4
        asFlexColumn()
    }) {
        text("Image sources: resource + file:// + http(s):// cached path.")
        text(
            "mediaReady=${window.mediaReady} file=${
                if (window.mediaReady) "prepared" else "failed"
            }",
            { color = DEMO_MUTED }
        )

        div({ gap = 3; asFlexRow() }) {
            div({ gap = 2; key = "mc.image.resource.col"; asFlexColumn() }) {
                text("Resource", { color = DEMO_MUTED })
                img(window.resourceImageSource, {
                    key = "mc.image.resource"
                    width = 36
                    height = 36
                    style = { border(1, 0xFF66737F.toInt()) }
                })
            }
            div({ gap = 2; key = "mc.image.file.col"; asFlexColumn() }) {
                text("file://", { color = DEMO_MUTED })
                img(window.fileImageSource, {
                    key = "mc.image.file"
                    width = 36
                    height = 36
                    style = { border(1, 0xFF66737F.toInt()) }
                })
            }
            div({ gap = 2; key = "mc.image.http.col"; asFlexColumn() }) {
                text("http://", { color = DEMO_MUTED })
                img(window.httpImageSource, {
                    key = "mc.image.http"
                    width = 36
                    height = 36
                    style = { border(1, 0xFF66737F.toInt()) }
                })
            }
        }

        text("Item stack render modes (2D item + 3D block)")
        div({ gap = 10; key = "mc.items.row"; asFlexRow() }) {
            itemStack(window.flatItemRef, {
                size = 18
                key = "mc.item.2d"
                width = 64
                style = { border(1, 0xFF586A7A.toInt()) }
            })
            itemStack(window.blockItemRef, {
                size = 20
                rotYDeg = window.itemRotY
                rotXDeg = window.itemRotX
                key = "mc.item.3d"
                width = 70
                style = { border(1, 0xFF586A7A.toInt()) }
            })
        }

        text("Rotation controls (drag sliders or use step buttons)")
        text(
            "Drag outside slider bounds: value should keep updating until mouse up.",
            { color = DEMO_MUTED }
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
                width = contentWidth - 10
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
                width = contentWidth - 10
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

        div({ gap = 4; asFlexRow() }) {
            button("Y-15", {
                width = 36
                onMouseClick = { window.adjustItemRotation(deltaY = -15.0) }
            })
            button("Y+15", {
                width = 36
                onMouseClick = { window.adjustItemRotation(deltaY = 15.0) }
            })
            button("X-10", {
                width = 36
                onMouseClick = { window.adjustItemRotation(deltaX = -10.0) }
            })
            button("X+10", {
                width = 36
                onMouseClick = { window.adjustItemRotation(deltaX = 10.0) }
            })
            button("Reset", {
                width = 42
                onMouseClick = {
                    window.itemRotY = 160.0
                    window.itemRotX = -11.0
                }
            })
        }

        text(
            "rotY=${window.itemRotYLong()} rotX=${window.itemRotXLong()}",
            { color = DEMO_MUTED }
        )
    }
}
