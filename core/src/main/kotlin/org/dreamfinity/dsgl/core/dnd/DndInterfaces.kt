package org.dreamfinity.dsgl.core.dnd

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseUpEvent
import org.dreamfinity.dsgl.core.render.RenderCommand

data class DndMonitorState(
    val isDragging: Boolean,
    val sourceKey: Any?,
    val overKey: Any?,
    val cursorX: Int,
    val cursorY: Int,
    val previewX: Double,
    val previewY: Double,
    val mode: DragPreviewMode?,
    val collisionCandidates: Int,
    val sourceExcludedFromHitTest: Boolean
)

interface DndMonitorRegistry {
    fun subscribe(listener: DndMonitorListener): AutoCloseable
}

interface DndHitTester

interface DndOverlayRenderer {
    fun appendPlaceholderCommands(out: MutableList<RenderCommand>)
    fun appendOverlayCommands(
        root: DOMNode,
        ctx: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>
    )
}

interface DndClock {
    fun onFrame(root: DOMNode, dtSeconds: Double)
}

interface DndEngine : DndMonitorRegistry, DndOverlayRenderer, DndClock {
    val isDragging: Boolean
    val isPointerCaptured: Boolean

    fun monitor(nodeKey: Any? = null): DndMonitorState
    fun activeDrag(): ActiveDrag?

    fun setSmoothingFactor(value: Double)
    fun getSmoothingFactor(): Double
    fun isDraggingNode(nodeKey: Any?): Boolean

    fun onMouseDown(root: DOMNode, target: DOMNode?, event: MouseDownEvent)
    fun onMouseMove(root: DOMNode, mouseX: Int, mouseY: Int)
    fun onMouseUp(root: DOMNode, event: MouseUpEvent): Boolean
    fun rebindAfterReconcile(root: DOMNode)
    fun cancelActiveDrag()
}
