package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DynamicTextProps
import org.dreamfinity.dsgl.core.ImageProps
import org.dreamfinity.dsgl.core.InputProps
import org.dreamfinity.dsgl.core.ItemStackProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.renderMcFeaturesSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    column(
        ComponentProps(
            key = "section.mcFeatures",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        )
    ) {
        text(TextProps("Image sources: resource + file:// + http(s):// cached path."))
        dynamicText(
            DynamicTextProps {
                "mediaReady=${window.mediaReady} file=${
                    if (window.mediaReady) "prepared" else "failed"
                }"
            }.apply { color = DEMO_MUTED }
        )

        row(ComponentProps(gap = 3)) {
            column(ComponentProps(gap = 2, key = "mc.image.resource.col")) {
                text(TextProps("Resource").apply { color = DEMO_MUTED })
                img(
                    ImageProps(window.resourceImageSource).apply {
                        key = "mc.image.resource"
                        width = 36
                        height = 36
                        style = { border(1, 0xFF66737F.toInt()) }
                    }
                )
            }
            column(ComponentProps(gap = 2, key = "mc.image.file.col")) {
                text(TextProps("file://").apply { color = DEMO_MUTED })
                img(
                    ImageProps(window.fileImageSource).apply {
                        key = "mc.image.file"
                        width = 36
                        height = 36
                        style = { border(1, 0xFF66737F.toInt()) }
                    }
                )
            }
            column(ComponentProps(gap = 2, key = "mc.image.http.col")) {
                text(TextProps("http://").apply { color = DEMO_MUTED })
                img(
                    ImageProps(window.httpImageSource).apply {
                        key = "mc.image.http"
                        width = 36
                        height = 36
                        style = { border(1, 0xFF66737F.toInt()) }
                    }
                )
            }
        }

        text(TextProps("Item stack render modes (2D item + 3D block)"))
        row(ComponentProps(gap = 10, key = "mc.items.row")) {
            itemStack(
                ItemStackProps(window.flatItemRef, size = 18).apply {
                    key = "mc.item.2d"
                    width = 64
                    style = { border(1, 0xFF586A7A.toInt()) }
                }
            )
            itemStack(
                ItemStackProps(
                    stack = window.blockItemRef,
                    size = 20,
                    rotYDeg = window.itemRotY,
                    rotXDeg = window.itemRotX
                ).apply {
                    key = "mc.item.3d"
                    width = 70
                    style = { border(1, 0xFF586A7A.toInt()) }
                }
            )
        }

        text(TextProps("Rotation controls (drag sliders or use step buttons)"))
        text(TextProps("Drag outside slider bounds: value should keep updating until mouse up.").apply {
            color = DEMO_MUTED
        })
        input(
            InputProps(
                InputType.Range(
                    value = window.itemRotYLong(),
                    min = 0,
                    max = 360,
                    step = 5
                )
            ).apply {
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
            InputProps(
                InputType.Range(
                    value = window.itemRotXLong(),
                    min = -89,
                    max = 89,
                    step = 1
                )
            ).apply {
                key = "mc.rotation.slider.pitch"
                width = contentWidth - 10
                onMouseDown = { event ->
                    window.logHook("mc.rotX.onMouseDown", event, "capture-start")
                }
                onMouseUp = { event ->
                    window.logHook("mc.rotX.onMouseUp", event, "capture-end rotX=${window.itemRotXLong()}")
                }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: window.itemRotXLong()
                    window.itemRotX = next.toDouble()
                    window.logHook("mc.rotX.onInput", event, "rotX=${window.itemRotXLong()}")
                }
                onValueChange = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: window.itemRotXLong()
                    window.itemRotX = next.toDouble()
                    window.logHook("mc.rotX.onChange", event, "rotX=${window.itemRotXLong()}")
                }
            }
        )

        row(ComponentProps(gap = 4)) {
            button(
                ButtonProps("Y-15").apply {
                    width = 36
                    onMouseClick = { window.adjustItemRotation(deltaY = -15.0) }
                }
            )
            button(
                ButtonProps("Y+15").apply {
                    width = 36
                    onMouseClick = { window.adjustItemRotation(deltaY = 15.0) }
                }
            )
            button(
                ButtonProps("X-10").apply {
                    width = 36
                    onMouseClick = { window.adjustItemRotation(deltaX = -10.0) }
                }
            )
            button(
                ButtonProps("X+10").apply {
                    width = 36
                    onMouseClick = { window.adjustItemRotation(deltaX = 10.0) }
                }
            )
            button(
                ButtonProps("Reset").apply {
                    width = 42
                    onMouseClick = {
                        window.itemRotY = 160.0
                        window.itemRotX = -11.0
                    }
                }
            )
        }

        dynamicText(
            DynamicTextProps {
                "rotY=${window.itemRotYLong()} rotX=${window.itemRotXLong()}"
            }.apply { color = DEMO_MUTED }
        )
    }
}

