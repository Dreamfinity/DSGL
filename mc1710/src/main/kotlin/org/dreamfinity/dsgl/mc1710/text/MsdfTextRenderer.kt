package org.dreamfinity.dsgl.mc1710.text

import org.dreamfinity.dsgl.core.dom.layout.FontLineMetrics
import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.render.TextBackendKind
import org.dreamfinity.dsgl.core.render.TextRenderMode

internal class MsdfTextRenderer(
    private val mtsdf2DBackend: TextDrawBackend = Mtsdf2DTextBackend()
) {
    fun measureText(text: String, fontId: String?, fontSize: Int?): Int {
        return FontRegistry.measureText(text, fontId, fontSize)
    }

    fun measureTextRange(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        fontId: String?,
        fontSize: Int?
    ): Int {
        return FontRegistry.measureTextRange(text, startIndex, endIndexExclusive, fontId, fontSize)
    }

    fun lineHeight(fontId: String?, fontSize: Int?): Int {
        return FontRegistry.lineHeight(fontId, fontSize)
    }

    fun fontLineMetrics(fontId: String?, fontSize: Int?): FontLineMetrics? {
        val font = FontRegistry.get(fontId) ?: return null
        val metrics = font.meta.metrics
        if (metrics.emSize <= 0f || metrics.lineHeight <= 0f) return null
        return FontLineMetrics(
            emSize = metrics.emSize,
            lineHeightEm = metrics.lineHeight,
            ascenderEm = metrics.ascender,
            descenderEm = metrics.descender
        )
    }

    fun draw(
        command: RenderCommand.DrawText,
        opacityMultiplier: Float,
        deviceScale: Float
    ) {
        backendFor(command.renderMode).draw(
            command = command,
            opacityMultiplier = opacityMultiplier,
            deviceScale = deviceScale
        )
    }

    internal fun resolveBackendKind(renderMode: TextRenderMode): TextBackendKind {
        return when (renderMode) {
            TextRenderMode.Auto -> TextBackendKind.Mtsdf2D
            TextRenderMode.Mtsdf2D -> TextBackendKind.Mtsdf2D
            TextRenderMode.Raster2D -> TextBackendKind.Mtsdf2D
        }
    }

    private fun backendFor(renderMode: TextRenderMode): TextDrawBackend {
        return when (resolveBackendKind(renderMode)) {
            TextBackendKind.Mtsdf2D -> mtsdf2DBackend
            TextBackendKind.Raster2D -> mtsdf2DBackend
        }
    }
}
