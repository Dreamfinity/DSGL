package org.dreamfinity.dsgl.core.render

sealed class RenderCommand {
    data class DrawRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val color: Int
    ) : RenderCommand()

    data class DrawText(
        val text: String,
        val x: Int,
        val y: Int,
        val color: Int
    ) : RenderCommand()

    data class DrawImage(
        val resource: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : RenderCommand()

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
