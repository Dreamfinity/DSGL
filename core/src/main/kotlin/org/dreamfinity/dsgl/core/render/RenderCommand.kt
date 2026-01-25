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
}
