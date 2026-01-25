package org.dreamfinity.dsgl.core.host

import org.dreamfinity.dsgl.core.DsglWindow

data class Viewport(
    val width: Int,
    val height: Int,
    val scale: Float
)

interface DsglWindowHost {
    val window: DsglWindow
    fun requestRebuild(reason: String? = null)
    fun requestRedraw()
    fun getViewport(): Viewport
}
