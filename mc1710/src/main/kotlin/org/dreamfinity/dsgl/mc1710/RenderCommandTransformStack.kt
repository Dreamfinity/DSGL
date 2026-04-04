package org.dreamfinity.dsgl.mc1710

import kotlin.math.ceil
import kotlin.math.floor
import org.dreamfinity.dsgl.core.dom.layout.AffineTransform2D
import org.dreamfinity.dsgl.core.render.RenderCommand

internal data class GuiClipRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

internal class RenderCommandTransformStack {
    private val stack: ArrayDeque<AffineTransform2D> = ArrayDeque()
    private var current: AffineTransform2D = AffineTransform2D.IDENTITY

    fun reset() {
        stack.clear()
        current = AffineTransform2D.IDENTITY
    }

    fun push(command: RenderCommand.PushTransform) {
        stack.addLast(current)
        current = current.times(command.toAffineTransform())
    }

    fun pop() {
        current = if (stack.isNotEmpty()) stack.removeLast() else AffineTransform2D.IDENTITY
    }

    fun currentTransform(): AffineTransform2D = current

    fun transformPoint(x: Float, y: Float): Pair<Float, Float> {
        return current.transform(x, y)
    }

    fun resolveClipRect(x: Int, y: Int, width: Int, height: Int): GuiClipRect {
        val safeWidth = width.coerceAtLeast(0)
        val safeHeight = height.coerceAtLeast(0)
        if (safeWidth == 0 || safeHeight == 0) {
            return GuiClipRect(x, y, 0, 0)
        }
        if (current == AffineTransform2D.IDENTITY) {
            return GuiClipRect(x, y, safeWidth, safeHeight)
        }

        val topLeft = current.transform(x.toFloat(), y.toFloat())
        val topRight = current.transform((x + safeWidth).toFloat(), y.toFloat())
        val bottomLeft = current.transform(x.toFloat(), (y + safeHeight).toFloat())
        val bottomRight = current.transform((x + safeWidth).toFloat(), (y + safeHeight).toFloat())

        val minX = minOf(topLeft.first, topRight.first, bottomLeft.first, bottomRight.first)
        val maxX = maxOf(topLeft.first, topRight.first, bottomLeft.first, bottomRight.first)
        val minY = minOf(topLeft.second, topRight.second, bottomLeft.second, bottomRight.second)
        val maxY = maxOf(topLeft.second, topRight.second, bottomLeft.second, bottomRight.second)

        val resolvedX = floor(minX.toDouble()).toInt()
        val resolvedY = floor(minY.toDouble()).toInt()
        val resolvedRight = ceil(maxX.toDouble()).toInt()
        val resolvedBottom = ceil(maxY.toDouble()).toInt()
        val resolvedWidth = (resolvedRight - resolvedX).coerceAtLeast(0)
        val resolvedHeight = (resolvedBottom - resolvedY).coerceAtLeast(0)
        return GuiClipRect(resolvedX, resolvedY, resolvedWidth, resolvedHeight)
    }

    private fun RenderCommand.PushTransform.toAffineTransform(): AffineTransform2D {
        val toOrigin = AffineTransform2D.translation(originX, originY)
        val translate = AffineTransform2D.translation(translateX, translateY)
        val rotate = AffineTransform2D.rotation(rotateDeg)
        val scale = AffineTransform2D.scale(scaleX, scaleY)
        val fromOrigin = AffineTransform2D.translation(-originX, -originY)
        return toOrigin
            .times(translate)
            .times(rotate)
            .times(scale)
            .times(fromOrigin)
    }
}
