package org.dreamfinity.dsgl.core.dom.layout

import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Version-agnostic text measurement context. Implemented by platform adapters.
 */
interface UiMeasureContext {
    /** Font height in pixels for the active UI font. */
    val fontHeight: Int
    /** Measures text width in pixels. */
    fun measureText(text: String): Int
    /** Executes render commands on the host. */
    fun paint(commands: List<RenderCommand>)
}
