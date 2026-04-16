package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.colorpicker.*
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.hooks.useEffect
import org.dreamfinity.dsgl.core.hooks.useMemo
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.colorPickerSection() {
    var colorInlineValue by useState(RgbaColor(0.28f, 0.52f, 0.88f, 1f))
    var colorInlineMode by useState(ColorFormatMode.HEX)
    var colorPopupValue by useState(RgbaColor(0.82f, 0.31f, 0.41f, 0.9f))
    var colorPopupSecondValue by useState(RgbaColor(0.29f, 0.73f, 0.46f, 1f))
    var colorSharedA by useState(RgbaColor(0.91f, 0.73f, 0.19f, 1f))
    var colorSharedB by useState(RgbaColor(0.45f, 0.41f, 0.96f, 0.8f))
    var colorPickerLastCommit by useState("none")
    var colorPickerAlphaEnabled by useState(true)
    val sharedColorPickerManager by useMemo { ColorPickerPopupManager() }

    useEffect(sharedColorPickerManager) {
        onDispose { sharedColorPickerManager.close() }
    }

    fun openSharedColorPicker(target: String, mouseX: Int, mouseY: Int) {
        val current = if (target == "A") colorSharedA else colorSharedB
        sharedColorPickerManager.open(
            anchorRect = Rect(mouseX, mouseY, 1, 1),
            title = "Shared Picker [$target]",
            state = ColorPickerState(
                color = current,
                previous = current,
                mode = colorInlineMode,
                alphaEnabled = colorPickerAlphaEnabled,
                closeOnSelect = false
            ),
            closeOnOutsideClick = false,
            onPreview = { color ->
                if (target == "A") {
                    colorSharedA = color
                } else {
                    colorSharedB = color
                }
            },
            onChange = { color ->
                if (target == "A") {
                    colorSharedA = color
                } else {
                    colorSharedB = color
                }
            },
            onCommit = { color ->
                if (target == "A") {
                    colorSharedA = color
                } else {
                    colorSharedB = color
                }
                colorPickerLastCommit = colorLabel(color)
            }
        )
    }

    div({
        key = "section.color-picker"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Reusable color picker: inline + popup pane + shared popup manager")
        text(
            "Pipette samples current rendered game window surface. Copy/paste accepts hex/rgb/hsl/hsb.",
            { style = { color = DEMO_MUTED } }
        )
        text(
            "Inline picker follows app styling. Inspector picker (F8) is rendered in isolated system overlay styles.",
            { style = { color = DEMO_MUTED } }
        )

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(if (colorPickerAlphaEnabled) "Alpha ON" else "Alpha OFF", {
                onMouseClick = {
                    colorPickerAlphaEnabled = !colorPickerAlphaEnabled
                }
            })
            button("HEX", {
                style = { backgroundColor = if (colorInlineMode == ColorFormatMode.HEX) 0xFF3E5877.toInt() else null }
                onMouseClick = { colorInlineMode = ColorFormatMode.HEX }
            })
            button("RGB", {
                style = { backgroundColor = if (colorInlineMode == ColorFormatMode.RGB) 0xFF3E5877.toInt() else null }
                onMouseClick = { colorInlineMode = ColorFormatMode.RGB }
            })
            button("HSL", {
                style = { backgroundColor = if (colorInlineMode == ColorFormatMode.HSL) 0xFF3E5877.toInt() else null }
                onMouseClick = { colorInlineMode = ColorFormatMode.HSL }
            })
            button("HSB", {
                style = { backgroundColor = if (colorInlineMode == ColorFormatMode.HSB) 0xFF3E5877.toInt() else null }
                onMouseClick = { colorInlineMode = ColorFormatMode.HSB }
            })
        }

        div({
            style = {
                gap = 6.px
                display = Display.Flex
                flexDirection = FlexDirection.Column
            }
        }) {
            div({ style = { display = Display.Inline } }) {
                colorPicker({
                    key = "demo.color.inline"
                    value = colorInlineValue
                    mode = colorInlineMode
                    alphaEnabled = colorPickerAlphaEnabled
                    closeOnSelect = false
                    style = {}
                    onPreviewColor = { colorInlineValue = it }
                    onChangeColor = { colorInlineValue = it }
                    onCommitColor = {
                        colorInlineValue = it
                        colorPickerLastCommit = colorLabel(it)
                    }
                })
            }

            div({
                style = {
                    gap = 4.px
                    flexGrow = 1f
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Popup wrapper fields")
                colorPickerPopup({
                    key = "demo.color.popup.primary"
                    value = colorPopupValue
                    mode = ColorFormatMode.HEX
                    alphaEnabled = colorPickerAlphaEnabled
                    popupCloseOnOutsideClick = false
                    closeOnSelect = false
                    onPreviewColor = { colorPopupValue = it }
                    onChangeColor = { colorPopupValue = it }
                    onCommitColor = {
                        colorPopupValue = it
                        colorPickerLastCommit = colorLabel(it)
                    }
                    style = { width = 100.percent }
                })
                colorPickerPopup({
                    key = "demo.color.popup.secondary"
                    value = colorPopupSecondValue
                    mode = ColorFormatMode.RGB
                    alphaEnabled = colorPickerAlphaEnabled
                    popupCloseOnOutsideClick = false
                    closeOnSelect = false
                    onPreviewColor = { colorPopupSecondValue = it }
                    onChangeColor = { colorPopupSecondValue = it }
                    onCommitColor = {
                        colorPopupSecondValue = it
                        colorPickerLastCommit = colorLabel(it)
                    }
                    style = { width = 100.percent }
                })

                text("Shared manager retarget demo")
                button("Edit A (${colorLabel(colorSharedA)})", {
                    style = { width = 100.percent }
                    onMouseDown = { event ->
                        openSharedColorPicker("A", event.mouseX, event.mouseY)
                    }
                })
                button("Edit B (${colorLabel(colorSharedB)})", {
                    style = { width = 100.percent }
                    onMouseDown = { event ->
                        openSharedColorPicker("B", event.mouseX, event.mouseY)
                    }
                })

                text("Last commit: $colorPickerLastCommit", { style = { color = DEMO_MUTED } })
                text("A=${colorLabel(colorSharedA)}", { style = { color = DEMO_MUTED } })
                text("B=${colorLabel(colorSharedB)}", { style = { color = DEMO_MUTED } })
            }
        }
    }
}

private fun colorLabel(color: RgbaColor): String {
    return ColorTextCodec.format(color, ColorFormatMode.HEX, includeAlpha = true)
}
