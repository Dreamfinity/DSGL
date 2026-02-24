package org.dreamfinity.dsgl.core.dnd

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.render.RenderCommand

enum class DragPreviewMode {
    ORIGINAL,
    GHOST
}

class PlaceholderScope internal constructor() {
    var fillColor: Int? = 0x442A2A2A
    var borderColor: Int? = 0xFF8A94A2.toInt()
    var borderWidth: Int = 1

    fun transparent() {
        fillColor = null
        borderColor = null
        borderWidth = 0
    }

    internal fun buildCommands(bounds: Rect): List<RenderCommand> {
        if (bounds.width <= 0 || bounds.height <= 0) return emptyList()
        val out = ArrayList<RenderCommand>(5)
        fillColor?.let { color ->
            out.add(
                RenderCommand.DrawRect(
                    bounds.x,
                    bounds.y,
                    bounds.width,
                    bounds.height,
                    color
                )
            )
        }
        val border = borderColor
        val width = borderWidth.coerceAtLeast(0)
        if (border != null && width > 0) {
            out.add(RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, width, border))
            out.add(
                RenderCommand.DrawRect(
                    bounds.x,
                    bounds.y + bounds.height - width,
                    bounds.width,
                    width,
                    border
                )
            )
            out.add(RenderCommand.DrawRect(bounds.x, bounds.y, width, bounds.height, border))
            out.add(
                RenderCommand.DrawRect(
                    bounds.x + bounds.width - width,
                    bounds.y,
                    width,
                    bounds.height,
                    border
                )
            )
        }
        return out
    }
}

class DragPreviewScope internal constructor(
    val dataTransfer: DataTransfer,
    val sourceBounds: Rect,
    private val anchorX: Int,
    private val anchorY: Int
) {
    private val commands: MutableList<RenderCommand> = ArrayList(8)

    fun rect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        commands.add(
            RenderCommand.DrawRect(
                anchorX + x,
                anchorY + y,
                width,
                height,
                color
            )
        )
    }

    fun text(value: String, x: Int, y: Int, color: Int = DsglColors.WHITE) {
        commands.add(RenderCommand.DrawText(value, anchorX + x, anchorY + y, color))
    }

    fun image(resource: String, x: Int, y: Int, width: Int, height: Int) {
        commands.add(
            RenderCommand.DrawImage(
                resource,
                anchorX + x,
                anchorY + y,
                width,
                height
            )
        )
    }

    internal fun build(): List<RenderCommand> = commands
}