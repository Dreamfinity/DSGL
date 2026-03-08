package org.dreamfinity.dsgl.core.system

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand

internal class SystemOverlayRawRenderCommandNode(
    private val renderCommand: RenderCommand,
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-raw-render-command"

    override fun measure(ctx: UiMeasureContext): Size = Size(0, 0)

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        out += renderCommand
    }

    override fun volatileRenderCommandsSignature(nowMs: Long): Long {
        return renderCommand.hashCode().toLong()
    }
}
