package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand

fun ApplicationOverlayHost.applicationColorPickerOnFrame(
    viewportWidth: Int,
    viewportHeight: Int,
    mouseX: Int,
    mouseY: Int,
) {
    applicationColorPickerPortal.onFrame(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        mouseX = mouseX,
        mouseY = mouseY,
    )
}

fun ApplicationOverlayHost.appendApplicationColorPickerOverlayCommands(
    measureContext: UiMeasureContext,
    viewportWidth: Int,
    viewportHeight: Int,
    out: MutableList<RenderCommand>,
) {
    applicationColorPickerPortal.appendCommands(
        measureContext = measureContext,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        out = out,
    )
}

fun ApplicationOverlayHost.isApplicationColorPickerOpen(): Boolean = applicationColorPickerPortal.isOpen

fun ApplicationOverlayHost.hasActiveApplicationColorPickerEyedropper(): Boolean =
    applicationColorPickerPortal.hasActiveEyedropper

fun ApplicationOverlayHost.captureApplicationColorPickerEyedropperSample() {
    applicationColorPickerPortal.captureEyedropperSample()
}

fun ApplicationOverlayHost.handleApplicationColorPickerKeyDown(keyCode: Int, keyChar: Char): Boolean =
    applicationColorPickerPortal.handleKeyDown(keyCode, keyChar)

fun ApplicationOverlayHost.handleApplicationColorPickerMouseMove(mouseX: Int, mouseY: Int): Boolean =
    applicationColorPickerPortal.handleMouseMove(mouseX, mouseY)

fun ApplicationOverlayHost.handleApplicationColorPickerMouseDown(
    mouseX: Int,
    mouseY: Int,
    button: MouseButton,
): Boolean = applicationColorPickerPortal.handleMouseDown(mouseX, mouseY, button)

fun ApplicationOverlayHost.handleApplicationColorPickerMouseUp(
    mouseX: Int,
    mouseY: Int,
    button: MouseButton,
): Boolean = applicationColorPickerPortal.handleMouseUp(mouseX, mouseY, button)

fun ApplicationOverlayHost.handleApplicationColorPickerMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
    applicationColorPickerPortal.handleMouseWheel(mouseX, mouseY, delta)
