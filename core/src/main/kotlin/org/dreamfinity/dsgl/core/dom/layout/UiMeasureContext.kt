package org.dreamfinity.dsgl.core.dom.layout

import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Version-agnostic text measurement context. Implemented by platform adapters.
 */
interface UiMeasureContext {
    val fontHeight: Int
    fun measureText(text: String): Int
    fun paint(commands: List<RenderCommand>)
}
