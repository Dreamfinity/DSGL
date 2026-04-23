package org.dreamfinity.dsgl.core.host

import org.dreamfinity.dsgl.core.DsglWindow

/**
 * Current viewport information as seen by the host.
 */
data class Viewport(
    val width: Int,
    val height: Int,
    val scale: Float = 1f,
    val x: Int = 0,
    val y: Int = 0,
)

data class ViewportPoint(
    val x: Int,
    val y: Int,
)

data class GlScissorRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

fun Viewport.rawMouseToDsgl(rawX: Int, rawY: Int): ViewportPoint =
    ViewportPoint(
        x = rawX - x,
        y = (y + height) - rawY - 1,
    )

fun Viewport.rawMouseToDsglX(rawX: Int): Int = rawX - x

fun Viewport.rawMouseToDsglY(rawY: Int): Int = (y + height) - rawY - 1

fun Viewport.dsglRectToGlScissor(
    dsglX: Int,
    dsglY: Int,
    dsglWidth: Int,
    dsglHeight: Int,
): GlScissorRect {
    val widthPx = dsglWidth.coerceAtLeast(0)
    val heightPx = dsglHeight.coerceAtLeast(0)
    return GlScissorRect(
        x = x + dsglX,
        y = y + height - (dsglY + heightPx),
        width = widthPx,
        height = heightPx,
    )
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
