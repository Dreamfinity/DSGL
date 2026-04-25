package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand

interface OverlayLayerHost {
    val layerId: UiLayerId

    fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {}

    fun render(ctx: UiMeasureContext, width: Int, height: Int)

    fun paint(ctx: UiMeasureContext): List<RenderCommand>

    fun clearRefs()

    fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = false

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean = false

    fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean = false
}
