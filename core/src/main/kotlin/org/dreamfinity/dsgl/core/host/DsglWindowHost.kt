package org.dreamfinity.dsgl.core.host

import org.dreamfinity.dsgl.core.DsglWindow

/**
 * Current viewport information as seen by the host.
 *
 * Shared DSGL code treats [width] and [height] as logical UI-space extents.
 * Backend integrations may render those logical units into a larger backing
 * framebuffer. [framebufferWidth] and [framebufferHeight] represent that pixel
 * backing size explicitly, while [scale] describes the logical-to-framebuffer
 * conversion factor.
 *
 * [x] and [y] are the framebuffer-space viewport origin used for raw input and
 * GL pixel operations such as `glViewport` / `glScissor`.
 */
data class Viewport(
    val width: Int,
    val height: Int,
    val scale: Float = 1f,
    val framebufferWidth: Int = logicalExtentToFramebufferExtent(width, scale),
    val framebufferHeight: Int = logicalExtentToFramebufferExtent(height, scale),
    val x: Int = 0,
    val y: Int = 0
) {
    val logicalWidth: Int
        get() = width

    val logicalHeight: Int
        get() = height

    val framebufferX: Int
        get() = x

    val framebufferY: Int
        get() = y

    init {
        require(scale > 0f) { "Viewport.scale must be > 0" }
        require(width >= 0) { "Viewport.width must be >= 0" }
        require(height >= 0) { "Viewport.height must be >= 0" }
        require(framebufferWidth >= 0) { "Viewport.framebufferWidth must be >= 0" }
        require(framebufferHeight >= 0) { "Viewport.framebufferHeight must be >= 0" }
    }
}

data class ViewportPoint(
    val x: Int,
    val y: Int
)

data class GlScissorRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

fun Viewport.rawMouseToDsgl(rawX: Int, rawY: Int): ViewportPoint {
    return ViewportPoint(
        x = framebufferPixelToLogical(rawX - x, scale),
        y = framebufferPixelToLogical((y + framebufferHeight) - rawY - 1, scale)
    )
}

fun Viewport.rawMouseToDsglX(rawX: Int): Int = framebufferPixelToLogical(rawX - x, scale)

fun Viewport.rawMouseToDsglY(rawY: Int): Int =
    framebufferPixelToLogical((y + framebufferHeight) - rawY - 1, scale)

fun Viewport.dsglRectToGlScissor(
    dsglX: Int,
    dsglY: Int,
    dsglWidth: Int,
    dsglHeight: Int
): GlScissorRect {
    val safeWidth = dsglWidth.coerceAtLeast(0)
    val safeHeight = dsglHeight.coerceAtLeast(0)
    val leftPx = logicalEdgeFloor(dsglX, scale)
    val rightPx = logicalEdgeCeil(dsglX + safeWidth, scale)
    val topPx = logicalEdgeFloor(dsglY, scale)
    val bottomPx = logicalEdgeCeil(dsglY + safeHeight, scale)
    return GlScissorRect(
        x = x + leftPx,
        y = y + framebufferHeight - bottomPx,
        width = (rightPx - leftPx).coerceAtLeast(0),
        height = (bottomPx - topPx).coerceAtLeast(0)
    )
}

private fun logicalExtentToFramebufferExtent(logicalExtent: Int, scale: Float): Int {
    return kotlin.math.ceil(logicalExtent.toDouble() * scale.toDouble()).toInt().coerceAtLeast(0)
}

private fun framebufferPixelToLogical(pixel: Int, scale: Float): Int {
    return kotlin.math.floor(pixel.toDouble() / scale.toDouble()).toInt()
}

private fun logicalEdgeFloor(logical: Int, scale: Float): Int {
    return kotlin.math.floor(logical.toDouble() * scale.toDouble()).toInt()
}

private fun logicalEdgeCeil(logical: Int, scale: Float): Int {
    return kotlin.math.ceil(logical.toDouble() * scale.toDouble()).toInt()
}

/**
 * Platform host contract that drives [org.dreamfinity.dsgl.core.DsglWindow] lifecycles.
 */
interface DsglWindowHost {
    val window: DsglWindow

    /** Requests a rebuild of the DOM tree. */
    fun requestRebuild(reason: String? = null)

    /** Requests a redraw without rebuild. */
    fun requestRedraw()

    /** Returns current viewport information. */
    fun getViewport(): Viewport
}
