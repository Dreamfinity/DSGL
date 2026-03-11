package org.dreamfinity.dsgl.core.debug

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand

internal data class OverlayDebugControlLayout(
    val panelRect: Rect,
    val appOverlayRenderRect: Rect,
    val appOverlayTintRect: Rect,
    val appOverlayInputRect: Rect,
    val systemOverlayTintRect: Rect,
    val systemOverlayRenderRect: Rect,
    val systemOverlayInputRect: Rect,
    val resetRect: Rect
)

class OverlayDebugControlHost(
    private val state: OverlayLayerDebugState = OverlayLayerDebugState
) {
    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1
    private var layout: OverlayDebugControlLayout? = null
    private val paintBuffer: MutableList<RenderCommand> = ArrayList(96)

    fun render(viewportWidth: Int, viewportHeight: Int) {
        this.viewportWidth = viewportWidth.coerceAtLeast(1)
        this.viewportHeight = viewportHeight.coerceAtLeast(1)
        if (!state.controlsEnabled) {
            layout = null
            return
        }
        layout = buildLayout(this.viewportWidth, this.viewportHeight)
    }

    fun paint(): List<RenderCommand> {
        paintBuffer.clear()
        val currentLayout = layout ?: return paintBuffer
        val snapshot = state.snapshot()

        drawPanel(currentLayout, paintBuffer)
        drawToggleRow(
            label = "App Overlay Render",
            value = snapshot.applicationOverlayRenderEnabled,
            valueRect = currentLayout.appOverlayRenderRect,
            labelX = currentLayout.panelRect.x + 10,
            labelY = currentLayout.appOverlayRenderRect.y + 2,
            out = paintBuffer
        )
        drawToggleRow(
            label = "App Overlay Tint",
            value = snapshot.applicationOverlayTintEnabled,
            valueRect = currentLayout.appOverlayTintRect,
            labelX = currentLayout.panelRect.x + 10,
            labelY = currentLayout.appOverlayTintRect.y + 2,
            out = paintBuffer
        )
        drawToggleRow(
            label = "App Overlay Input",
            value = snapshot.applicationOverlayInputEnabled,
            valueRect = currentLayout.appOverlayInputRect,
            labelX = currentLayout.panelRect.x + 10,
            labelY = currentLayout.appOverlayInputRect.y + 2,
            out = paintBuffer
        )
        drawToggleRow(
            label = "System Overlay Render",
            value = snapshot.systemOverlayRenderEnabled,
            valueRect = currentLayout.systemOverlayRenderRect,
            labelX = currentLayout.panelRect.x + 10,
            labelY = currentLayout.systemOverlayRenderRect.y + 2,
            out = paintBuffer
        )
        drawToggleRow(
            label = "System Overlay Tint",
            value = snapshot.systemOverlayTintEnabled,
            valueRect = currentLayout.systemOverlayTintRect,
            labelX = currentLayout.panelRect.x + 10,
            labelY = currentLayout.systemOverlayTintRect.y + 2,
            out = paintBuffer
        )
        drawToggleRow(
            label = "System Overlay Input",
            value = snapshot.systemOverlayInputEnabled,
            valueRect = currentLayout.systemOverlayInputRect,
            labelX = currentLayout.panelRect.x + 10,
            labelY = currentLayout.systemOverlayInputRect.y + 2,
            out = paintBuffer
        )

        drawResetButton(currentLayout.resetRect, paintBuffer)
        val status =
            "R:${if (snapshot.applicationOverlayRenderEnabled) "A1" else "A0"}/${if (snapshot.systemOverlayRenderEnabled) "S1" else "S0"}  I:${if (snapshot.applicationOverlayInputEnabled) "A1" else "A0"}/${if (snapshot.systemOverlayInputEnabled) "S1" else "S0"}"
        paintBuffer += RenderCommand.DrawText(
            text = status,
            x = currentLayout.panelRect.x + 10,
            y = currentLayout.panelRect.y + currentLayout.panelRect.height - 14,
            color = 0xFFBAC7D6.toInt(),
            fontSize = 14
        )
        return paintBuffer
    }

    fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean {
        val currentLayout = layout ?: return false
        return currentLayout.panelRect.contains(mouseX, mouseY)
    }

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val currentLayout = layout ?: return false
        if (!currentLayout.panelRect.contains(mouseX, mouseY)) {
            return false
        }
        if (button != MouseButton.LEFT) {
            return true
        }
        when {
            currentLayout.appOverlayRenderRect.contains(mouseX, mouseY) -> {
                state.applicationOverlayRenderEnabled = !state.applicationOverlayRenderEnabled
            }

            currentLayout.appOverlayTintRect.contains(mouseX, mouseY) -> {
                state.applicationOverlayTintEnabled = !state.applicationOverlayTintEnabled
            }

            currentLayout.appOverlayInputRect.contains(mouseX, mouseY) -> {
                state.applicationOverlayInputEnabled = !state.applicationOverlayInputEnabled
            }

            currentLayout.systemOverlayRenderRect.contains(mouseX, mouseY) -> {
                state.systemOverlayRenderEnabled = !state.systemOverlayRenderEnabled
            }

            currentLayout.systemOverlayTintRect.contains(mouseX, mouseY) -> {
                state.systemOverlayTintEnabled = !state.systemOverlayTintEnabled
            }

            currentLayout.systemOverlayInputRect.contains(mouseX, mouseY) -> {
                state.systemOverlayInputEnabled = !state.systemOverlayInputEnabled
            }

            currentLayout.resetRect.contains(mouseX, mouseY) -> {
                state.resetAll()
            }
        }
        return true
    }

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val currentLayout = layout ?: return false
        if (button != MouseButton.LEFT) return false
        return currentLayout.panelRect.contains(mouseX, mouseY)
    }

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        val currentLayout = layout ?: return false
        if (delta == 0) return false
        return currentLayout.panelRect.contains(mouseX, mouseY)
    }

    fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean = false

    fun clearRefs() {
        layout = null
        paintBuffer.clear()
    }

    internal fun debugLayout(): OverlayDebugControlLayout? = layout

    private fun buildLayout(viewportWidth: Int, viewportHeight: Int): OverlayDebugControlLayout {
        val panelWidth = 300
        val panelHeight = 176 + 56
        val panelX = 8
        val panelY = (viewportHeight - panelHeight - 8).coerceAtLeast(8)
        val panelRect = Rect(
            x = panelX,
            y = panelY,
            width = panelWidth.coerceAtMost((viewportWidth - 16).coerceAtLeast(120)),
            height = panelHeight.coerceAtMost((viewportHeight - 16).coerceAtLeast(96))
        )
        val toggleWidth = 56
        val toggleHeight = 18
        val toggleX = panelRect.x + panelRect.width - toggleWidth - 10
        val firstY = panelRect.y + 34
        val rowStep = 24
        var row = 0
        return OverlayDebugControlLayout(
            panelRect = panelRect,
            appOverlayRenderRect = Rect(toggleX, firstY + rowStep * row++, toggleWidth, toggleHeight),
            appOverlayTintRect = Rect(toggleX, firstY + rowStep * row++, toggleWidth, toggleHeight),
            appOverlayInputRect = Rect(toggleX, firstY + rowStep * row++, toggleWidth, toggleHeight),
            systemOverlayRenderRect = Rect(toggleX, firstY + rowStep * row++, toggleWidth, toggleHeight),
            systemOverlayTintRect = Rect(toggleX, firstY + rowStep * row++, toggleWidth, toggleHeight),
            systemOverlayInputRect = Rect(toggleX, firstY + rowStep * row++, toggleWidth, toggleHeight),
            resetRect = Rect(
                x = panelRect.x + 10,
                y = panelRect.y + panelRect.height - 40,
                width = panelRect.width - 20,
                height = 20
            )
        )
    }

    private fun drawPanel(layout: OverlayDebugControlLayout, out: MutableList<RenderCommand>) {
        out += RenderCommand.DrawRect(
            layout.panelRect.x + 2,
            layout.panelRect.y + 2,
            layout.panelRect.width,
            layout.panelRect.height,
            0x5F000000
        )
        out += RenderCommand.DrawRect(
            layout.panelRect.x,
            layout.panelRect.y,
            layout.panelRect.width,
            layout.panelRect.height,
            0xEE1A2230.toInt()
        )
        drawBorder(layout.panelRect, 0xFF5A6B80.toInt(), out)
        out += RenderCommand.DrawText(
            text = "Overlay Debug",
            x = layout.panelRect.x + 10,
            y = layout.panelRect.y + 8,
            color = 0xFFFFFFFF.toInt(),
            fontSize = 16
        )
    }

    private fun drawToggleRow(
        label: String,
        value: Boolean,
        valueRect: Rect,
        labelX: Int,
        labelY: Int,
        out: MutableList<RenderCommand>
    ) {
        out += RenderCommand.DrawText(
            text = label,
            x = labelX,
            y = labelY,
            color = 0xFFE0E9F2.toInt(),
            fontSize = 14
        )
        out += RenderCommand.DrawRect(
            valueRect.x,
            valueRect.y,
            valueRect.width,
            valueRect.height,
            if (value) 0xFF2F7D4E.toInt() else 0xFF7A2E3A.toInt()
        )
        drawBorder(valueRect, 0xFF9AB3C9.toInt(), out)
        out += RenderCommand.DrawText(
            text = if (value) "ON" else "OFF",
            x = valueRect.x + 14,
            y = valueRect.y + 2,
            color = 0xFFFFFFFF.toInt(),
            fontSize = 14
        )
    }

    private fun drawResetButton(rect: Rect, out: MutableList<RenderCommand>) {
        out += RenderCommand.DrawRect(
            rect.x,
            rect.y,
            rect.width,
            rect.height,
            0xFF2E3A49.toInt()
        )
        drawBorder(rect, 0xFF6886A5.toInt(), out)
        out += RenderCommand.DrawText(
            text = "Reset All",
            x = rect.x + 8,
            y = rect.y + 2,
            color = 0xFFFFFFFF.toInt(),
            fontSize = 14
        )
    }

    private fun drawBorder(rect: Rect, color: Int, out: MutableList<RenderCommand>) {
        if (rect.width <= 0 || rect.height <= 0) return
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y + rect.height - 1, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y, 1, rect.height, color)
        out += RenderCommand.DrawRect(rect.x + rect.width - 1, rect.y, 1, rect.height, color)
    }
}
