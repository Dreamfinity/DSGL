package org.dreamfinity.dsgl.mc1710.text

import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.render.TextBackendKind
import org.dreamfinity.dsgl.core.render.TextDecorations
import org.dreamfinity.dsgl.core.render.TextRenderMetrics
import org.dreamfinity.dsgl.core.render.TextRenderMode
import org.dreamfinity.dsgl.core.render.TextRenderStyle
import org.dreamfinity.dsgl.core.render.TextWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MsdfTextRendererBackendRoutingTests {
    @Test
    fun `auto mtsdf and raster modes currently route to scalable backend`() {
        val renderer = MsdfTextRenderer()

        assertEquals(TextBackendKind.Mtsdf2D, renderer.resolveBackendKind(TextRenderMode.Auto))
        assertEquals(TextBackendKind.Mtsdf2D, renderer.resolveBackendKind(TextRenderMode.Mtsdf2D))
        assertEquals(TextBackendKind.Mtsdf2D, renderer.resolveBackendKind(TextRenderMode.Raster2D))
    }

    @Test
    fun `renderer forwards explicit device scale into backend draw`() {
        val backend = RecordingBackend()
        val renderer = MsdfTextRenderer(mtsdf2DBackend = backend)

        renderer.draw(
            command = sampleDrawText(renderMode = TextRenderMode.Auto),
            opacityMultiplier = 0.75f,
            deviceScale = 2.5f
        )

        assertEquals(0.75f, backend.lastOpacityMultiplier)
        assertEquals(2.5f, backend.lastDeviceScale)
    }

    @Test
    fun `explicit screen pixel range grows with device scale`() {
        val font = FontRegistry.get(FontRegistry.FONT_MINECRAFT)
        val glyph = font?.meta?.fallbackGlyph()
        requireNotNull(font)
        requireNotNull(glyph)

        val low = Mtsdf2DTextBackend.explicitScreenPxRange(
            glyph = glyph,
            fontScalePx = 16f,
            deviceScale = 1f,
            atlasDistanceRange = font.meta.atlas.distanceRange
        )
        val high = Mtsdf2DTextBackend.explicitScreenPxRange(
            glyph = glyph,
            fontScalePx = 16f,
            deviceScale = 2f,
            atlasDistanceRange = font.meta.atlas.distanceRange
        )

        assertTrue(high > low)
    }

    @Test
    fun `bold weight uses edge bias instead of second draw pass`() {
        val normal = Mtsdf2DTextBackend.weightBiasPx(TextWeight.Normal, screenPxRange = 2f)
        val bold = Mtsdf2DTextBackend.weightBiasPx(TextWeight.Bold, screenPxRange = 2f)

        assertEquals(0f, normal)
        assertTrue(bold > 0f)
    }

    @Test
    fun `decorations are merged as backend-owned quads`() {
        val quads = ArrayList<DecorationRenderQuad>()

        Mtsdf2DTextBackend.appendMergedDecorationQuad(
            quads,
            DecorationRenderQuad(
                kind = 1,
                startX = 10f,
                endX = 20f,
                y = 30f,
                thickness = 1f,
                color = 0xFFFFFFFF.toInt()
            )
        )
        Mtsdf2DTextBackend.appendMergedDecorationQuad(
            quads,
            DecorationRenderQuad(
                kind = 1,
                startX = 20.25f,
                endX = 26f,
                y = 30f,
                thickness = 1f,
                color = 0xFFFFFFFF.toInt()
            )
        )

        assertEquals(1, quads.size)
        assertEquals(10f, quads.single().startX)
        assertEquals(26f, quads.single().endX)
    }

    private fun sampleDrawText(renderMode: TextRenderMode): RenderCommand.DrawText {
        return RenderCommand.DrawText(
            text = "Demo",
            x = 4,
            y = 8,
            fontId = FontRegistry.FONT_MINECRAFT,
            renderMode = renderMode,
            metrics = TextRenderMetrics(
                fontSizePx = 16,
                lineHeightPx = 18,
                nativeLineHeightPx = 18,
                ascenderPx = 12f,
                descenderPx = 4f,
                topLeadingPx = 1f,
                bottomLeadingPx = 1f
            ),
            baseStyle = TextRenderStyle(
                color = 0xFFFFFFFF.toInt(),
                weight = TextWeight.Normal,
                italic = false,
                decorations = TextDecorations.None,
                obfuscated = false
            )
        )
    }

    private class RecordingBackend : TextDrawBackend {
        override val kind: TextBackendKind = TextBackendKind.Mtsdf2D
        var lastOpacityMultiplier: Float? = null
        var lastDeviceScale: Float? = null

        override fun draw(command: RenderCommand.DrawText, opacityMultiplier: Float, deviceScale: Float) {
            lastOpacityMultiplier = opacityMultiplier
            lastDeviceScale = deviceScale
        }
    }
}
