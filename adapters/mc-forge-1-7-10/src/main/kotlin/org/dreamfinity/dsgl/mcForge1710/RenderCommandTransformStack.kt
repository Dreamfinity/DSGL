package org.dreamfinity.dsgl.mcForge1710

import org.dreamfinity.dsgl.core.dom.layout.AffineTransform2D
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

internal data class GuiClipRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
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

    fun transformPoint(x: Float, y: Float): Pair<Float, Float> = current.transform(x, y)

    fun resolveClipRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): GuiClipRect {
        val safeWidth = width.coerceAtLeast(0)
        val safeHeight = height.coerceAtLeast(0)
        if (safeWidth == 0 || safeHeight == 0) {
            return GuiClipRect(x, y, 0, 0)
        }
        if (current == AffineTransform2D.IDENTITY) {
            return GuiClipRect(x, y, safeWidth, safeHeight)
        }

        val transform = current
        val left = x.toFloat()
        val top = y.toFloat()
        val right = (x + safeWidth).toFloat()
        val bottom = (y + safeHeight).toFloat()
        val topLeftX = transform.a * left + transform.c * top + transform.tx
        val topLeftY = transform.b * left + transform.d * top + transform.ty
        val topRightX = transform.a * right + transform.c * top + transform.tx
        val topRightY = transform.b * right + transform.d * top + transform.ty
        val bottomLeftX = transform.a * left + transform.c * bottom + transform.tx
        val bottomLeftY = transform.b * left + transform.d * bottom + transform.ty
        val bottomRightX = transform.a * right + transform.c * bottom + transform.tx
        val bottomRightY = transform.b * right + transform.d * bottom + transform.ty

        val minX = minOf(minOf(topLeftX, topRightX), minOf(bottomLeftX, bottomRightX))
        val maxX = maxOf(maxOf(topLeftX, topRightX), maxOf(bottomLeftX, bottomRightX))
        val minY = minOf(minOf(topLeftY, topRightY), minOf(bottomLeftY, bottomRightY))
        val maxY = maxOf(maxOf(topLeftY, topRightY), maxOf(bottomLeftY, bottomRightY))

        val resolvedX = floor(minX.toDouble()).toInt()
        val resolvedY = floor(minY.toDouble()).toInt()
        val resolvedWidth = ceil((maxX - minX).toDouble()).toInt().coerceAtLeast(0)
        val resolvedHeight = ceil((maxY - minY).toDouble()).toInt().coerceAtLeast(0)
        return GuiClipRect(resolvedX, resolvedY, resolvedWidth, resolvedHeight)
    }

    private fun RenderCommand.PushTransform.toAffineTransform(): AffineTransform2D {
        // Closed form of T(origin) * T(translate) * R(rotateDeg) * S(scaleX, scaleY) * T(-origin):
        // chaining times() would allocate nine intermediate matrices per push in the paint loop.
        val rad = Math.toRadians(rotateDeg.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        val a = cos * scaleX
        val b = sin * scaleX
        val c = -sin * scaleY
        val d = cos * scaleY
        return AffineTransform2D(
            a = a,
            b = b,
            c = c,
            d = d,
            tx = originX + translateX - a * originX - c * originY,
            ty = originY + translateY - b * originX - d * originY,
        )
    }
}
