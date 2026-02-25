package org.dreamfinity.dsgl.core.render

/**
 * Platform-agnostic render commands emitted by the UI tree.
 */
sealed class RenderCommand {
    /** Solid rectangle. */
    data class DrawRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val color: Int
    ) : RenderCommand()

    /** Text draw command. */
    data class DrawText(
        val text: String,
        val x: Int,
        val y: Int,
        val color: Int
    ) : RenderCommand()

    /** Image draw command. */
    data class DrawImage(
        val resource: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : RenderCommand()

    /** Item stack draw command. */
    data class DrawItemStack(
        val stack: org.dreamfinity.dsgl.core.ItemStackRef,
        val x: Int,
        val y: Int,
        val width: Int,
        val size: Int,
        val rotYDeg: Double = 0.0,
        val rotXDeg: Double = 0.0
    ) : RenderCommand()

    /** Pushes a clipping rectangle (GUI coordinates, top-left origin). */
    data class PushClip(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : RenderCommand()

    /** Pops the current clipping rectangle. */
    object PopClip : RenderCommand()

    /** Pushes transform for subsequent commands. */
    data class PushTransform(
        val originX: Float,
        val originY: Float,
        val translateX: Float,
        val translateY: Float,
        val scaleX: Float,
        val scaleY: Float,
        val rotateDeg: Float
    ) : RenderCommand()

    /** Pops current transform. */
    object PopTransform : RenderCommand()

    /** Multiplies current alpha by opacity (0..1). */
    data class PushOpacity(
        val opacity: Float
    ) : RenderCommand()

    /** Pops current opacity multiplier. */
    object PopOpacity : RenderCommand()
}
