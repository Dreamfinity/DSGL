package org.dreamfinity.dsgl.core.host

import org.dreamfinity.dsgl.core.DsglWindow

/**
 * Current viewport information as seen by the host.
 */
data class Viewport(
    val width: Int,
    val height: Int,
    val scale: Float
)

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