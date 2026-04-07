package org.dreamfinity.dsgl.mc1710.text

import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.render.TextBackendKind

internal interface TextDrawBackend {
    val kind: TextBackendKind

    fun draw(
        command: RenderCommand.DrawText,
        opacityMultiplier: Float,
        deviceScale: Float
    )
}
