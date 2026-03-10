package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanel
import org.dreamfinity.dsgl.core.render.RenderCommand

internal class SystemOverlayPanelDemoNode(
    private val overlayPanel: OverlayPanel,
    key: Any? = "dsgl-system-panel-panel-demo"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-panel-panel-demo"

    private val commandBuffer: MutableList<RenderCommand> = ArrayList(128)
    private var renderCommandsRevision: Long = 0L
    private var buttonClicks: Int = 0
    private var actionButtonRect: Rect? = null

    fun setButtonClicks(value: Int) {
        buttonClicks = value
    }

    fun currentButtonClicks(): Int = buttonClicks

    fun buttonRect(): Rect? = actionButtonRect

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        commandBuffer.clear()
        actionButtonRect = null
        overlayPanel.appendCommands(
            viewportWidth = width,
            viewportHeight = height,
            out = commandBuffer,
            appendBody = { bodyRect, out ->
                out += RenderCommand.DrawText(
                    text = "Reusable panel demo",
                    x = bodyRect.x + 6,
                    y = bodyRect.y + 4,
                    color = 0xFFFFFFFF.toInt(),
                    fontSize = 16
                )
                out += RenderCommand.DrawText(
                    text = "Button clicks: $buttonClicks",
                    x = bodyRect.x + 6,
                    y = bodyRect.y + 22,
                    color = 0xFFB8C9DA.toInt(),
                    fontSize = 16
                )
                val buttonRect = Rect(bodyRect.x + 6, bodyRect.y + 44, 120, 24)
                actionButtonRect = buttonRect
                out += RenderCommand.DrawRect(buttonRect.x, buttonRect.y, buttonRect.width, buttonRect.height, 0xFF314154.toInt())
                drawBorder(out, buttonRect, 0xFF6F879E.toInt())
                out += RenderCommand.DrawText(
                    text = "Click me",
                    x = buttonRect.x + 8,
                    y = buttonRect.y + 4,
                    color = 0xFFFFFFFF.toInt(),
                    fontSize = 16
                )
                out += RenderCommand.DrawImage(
                    resource = "minecraft:textures/gui/options_background.png",
                    x = bodyRect.x + bodyRect.width - 52,
                    y = bodyRect.y + 6,
                    width = 44,
                    height = 44
                )
                out += RenderCommand.DrawText(
                    text = "Drag the title bar to move.",
                    x = bodyRect.x + 6,
                    y = bodyRect.y + 78,
                    color = 0xFFB8C9DA.toInt(),
                    fontSize = 16
                )
            }
        )
        if (SystemOverlayCommandDslRenderer.rebuildInto(this, commandBuffer, "system-panel-panel-demo")) {
            renderCommandsRevision += 1L
            markRenderCommandsDirty()
        }
        children.forEach { child ->
            child.render(ctx, bounds.x, bounds.y, bounds.width, bounds.height)
        }
    }

    override fun volatileRenderCommandsSignature(nowMs: Long): Long {
        return renderCommandsRevision
    }

    private fun drawBorder(out: MutableList<RenderCommand>, rect: Rect, color: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y + rect.height - 1, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y, 1, rect.height, color)
        out += RenderCommand.DrawRect(rect.x + rect.width - 1, rect.y, 1, rect.height, color)
    }
}
