package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.postChange
import org.dreamfinity.dsgl.core.event.postInput
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Checkbox group input node.
 */
class CheckboxGroupNode(
    var variants: List<InputOption>,
    selected: Set<String> = emptySet(),
    var minSelected: Int? = null,
    var maxSelected: Int? = null,
    key: Any? = null
) : DOMNode(key) {
    override val styleType: String = "input"
    override val focusable: Boolean = true
    private val selectedIds: MutableSet<String> = selected.toMutableSet()
    var textColor: Int = DsglColors.TEXT
    var boxColor: Int = 0xFF3A3A40.toInt()
    var checkColor: Int = DsglColors.TEXT
    private var lineHeight: Int = 0
    private var boxSize: Int = 10

    init {
        EventBus.run {
            this@CheckboxGroupNode.addEventListener(Events.CLICK) { event: MouseClickEvent ->
                if (this@CheckboxGroupNode.styleDisabled) return@addEventListener
                val index = hitIndex(event.mouseX, event.mouseY)
                if (index != null) {
                    val before = valueString()
                    toggle(variants[index].id)
                    val after = valueString()
                    if (after != before) {
                        val parsed = selected()
                        postInput(this@CheckboxGroupNode, after, parsed)
                        postChange(this@CheckboxGroupNode, after, parsed)
                    }
                }
            }
        }
    }

    fun selected(): Set<String> = selectedIds.toSet()

    override fun measure(ctx: UiMeasureContext): Size {
        boxSize = maxOf(10, ctx.fontHeight - 2)
        lineHeight = ctx.fontHeight + 4
        val maxLabelWidth = variants.maxOfOrNull { ctx.measureText(it.label) } ?: 0
        val contentWidth = width ?: (boxSize + 6 + maxLabelWidth)
        val contentHeight = height ?: (lineHeight * variants.size)
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        boxSize = maxOf(10, ctx.fontHeight - 2)
        lineHeight = ctx.fontHeight + 4
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        addBorderCommands(out)
        val startX = contentX()
        var cursorY = contentY()
        variants.forEach { option ->
            val boxY = cursorY + (lineHeight - boxSize) / 2
            out.add(RenderCommand.DrawRect(startX, boxY, boxSize, boxSize, boxColor))
            if (selectedIds.contains(option.id)) {
                out.add(RenderCommand.DrawRect(startX + 2, boxY + 2, boxSize - 4, boxSize - 4, checkColor))
            }
            val textX = startX + boxSize + 6
            val textY = cursorY + (lineHeight - ctx.fontHeight) / 2
            out.add(RenderCommand.DrawText(option.label, textX, textY, textColor))
            cursorY += lineHeight
        }
    }

    private fun hitIndex(mouseX: Int, mouseY: Int): Int? {
        val cx = contentX()
        val cy = contentY()
        val contentWidth = contentWidth()
        val contentHeight = contentHeight()
        if (mouseX < cx || mouseX > cx + contentWidth) return null
        if (mouseY < cy || mouseY > cy + contentHeight) return null
        if (lineHeight <= 0) return null
        val index = (mouseY - cy) / lineHeight
        if (index < 0 || index >= variants.size) return null
        return index
    }

    private fun toggle(id: String) {
        val isSelected = selectedIds.contains(id)
        if (isSelected) {
            val min = minSelected
            if (min != null && selectedIds.size <= min) return
            selectedIds.remove(id)
        } else {
            val max = maxSelected
            if (max != null && selectedIds.size >= max) return
            selectedIds.add(id)
        }
    }

    private fun valueString(): String {
        return selectedIds.toList().sorted().joinToString(",")
    }

    override fun defaultForegroundColor(): Int = textColor

    override fun applyForegroundColor(value: Int) {
        textColor = value
        checkColor = value
    }

    override fun defaultBackgroundColor(): Int? = boxColor

    override fun applyBackgroundColor(value: Int?) {
        if (value != null) {
            boxColor = value
        }
    }
}
