package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.colorpicker.ColorFormatMode
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.colorPickerSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.color-picker"
        style = {
            width = contentWidth.px
            height = contentHeight.px
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

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(if (window.colorPickerAlphaEnabled) "Alpha ON" else "Alpha OFF", {
                onMouseClick = {
                    window.colorPickerAlphaEnabled = !window.colorPickerAlphaEnabled
                }
            })
            button("HEX", {
                style = { backgroundColor = if (window.colorInlineMode == ColorFormatMode.HEX) 0xFF3E5877.toInt() else null }
                onMouseClick = { window.colorInlineMode = ColorFormatMode.HEX }
            })
            button("RGB", {
                style = { backgroundColor = if (window.colorInlineMode == ColorFormatMode.RGB) 0xFF3E5877.toInt() else null }
                onMouseClick = { window.colorInlineMode = ColorFormatMode.RGB }
            })
            button("HSL", {
                style = { backgroundColor = if (window.colorInlineMode == ColorFormatMode.HSL) 0xFF3E5877.toInt() else null }
                onMouseClick = { window.colorInlineMode = ColorFormatMode.HSL }
            })
            button("HSB", {
                style = { backgroundColor = if (window.colorInlineMode == ColorFormatMode.HSB) 0xFF3E5877.toInt() else null }
                onMouseClick = { window.colorInlineMode = ColorFormatMode.HSB }
            })
        }

        div({
            style = {
                gap = 6.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            colorPicker({
                key = "demo.color.inline"
                value = window.colorInlineValue
                mode = window.colorInlineMode
                alphaEnabled = window.colorPickerAlphaEnabled
                closeOnSelect = false
                style = {
                    width = 350.px
                    height = 392.px
                }
                onPreviewColor = { window.colorInlineValue = it }
                onChangeColor = { window.colorInlineValue = it }
                onCommitColor = {
                    window.colorInlineValue = it
                    window.colorPickerLastCommit = window.colorLabel(it)
                }
            })

            div({
                style = {
                    gap = 4.px
                    width = (contentWidth - 374).coerceAtLeast(110).px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("Popup wrapper fields")
                colorPickerPopup({
                    key = "demo.color.popup.primary"
                    value = window.colorPopupValue
                    mode = ColorFormatMode.HEX
                    alphaEnabled = window.colorPickerAlphaEnabled
                    popupCloseOnOutsideClick = false
                    onPreviewColor = { window.colorPopupValue = it }
                    onChangeColor = { window.colorPopupValue = it }
                    onCommitColor = {
                        window.colorPopupValue = it
                        window.colorPickerLastCommit = window.colorLabel(it)
                    }
                    style = { width = 170.px }
                })
                colorPickerPopup({
                    key = "demo.color.popup.secondary"
                    value = window.colorPopupSecondValue
                    mode = ColorFormatMode.RGB
                    alphaEnabled = window.colorPickerAlphaEnabled
                    popupCloseOnOutsideClick = false
                    onPreviewColor = { window.colorPopupSecondValue = it }
                    onChangeColor = { window.colorPopupSecondValue = it }
                    onCommitColor = {
                        window.colorPopupSecondValue = it
                        window.colorPickerLastCommit = window.colorLabel(it)
                    }
                    style = { width = 170.px }
                })

                text("Shared manager retarget demo")
                button("Edit A (${window.colorLabel(window.colorSharedA)})", {
                    style = { width = 220.px }
                    onMouseDown = { event ->
                        window.openSharedColorPicker(event.mouseX, event.mouseY, "A")
                    }
                })
                button("Edit B (${window.colorLabel(window.colorSharedB)})", {
                    style = { width = 220.px }
                    onMouseDown = { event ->
                        window.openSharedColorPicker(event.mouseX, event.mouseY, "B")
                    }
                })

                text("Last commit: ${window.colorPickerLastCommit}", { style = { color = DEMO_MUTED } })
                text("A=${window.colorLabel(window.colorSharedA)}", { style = { color = DEMO_MUTED } })
                text("B=${window.colorLabel(window.colorSharedB)}", { style = { color = DEMO_MUTED } })
            }
        }
    }
}
