package org.dreamfinity.dsgl.core.render

import org.dreamfinity.dsgl.core.ItemStackRef
import org.dreamfinity.dsgl.core.style.TextFormatting

enum class TextRenderMode {
    Auto,
    Mtsdf2D,
    Raster2D
}

enum class TextWeight {
    Normal,
    Bold
}

data class TextDecorations(
    val underline: Boolean = false,
    val strikethrough: Boolean = false
) {
    companion object {
        val None = TextDecorations()
    }
}

data class TextRenderStyle(
    val color: Int,
    val weight: TextWeight = TextWeight.Normal,
    val italic: Boolean = false,
    val decorations: TextDecorations = TextDecorations.None,
    val obfuscated: Boolean = false
)

data class TextStyleOverride(
    val color: Int? = null,
    val weight: TextWeight? = null,
    val italic: Boolean? = null,
    val decorations: TextDecorations? = null,
    val obfuscated: Boolean? = null
)

data class TextStyleSpan(
    val start: Int,
    val end: Int,
    val style: TextStyleOverride
)

data class TextRenderMetrics(
    val fontSizePx: Int,
    val lineHeightPx: Int,
    val nativeLineHeightPx: Int,
    val ascenderPx: Float,
    val descenderPx: Float,
    val topLeadingPx: Float,
    val bottomLeadingPx: Float
)

enum class TextBackendKind {
    Mtsdf2D,
    Raster2D
}

data class TextBackendSelectionInput(
    val requestedMode: TextRenderMode,
    val fontId: String?,
    val metrics: TextRenderMetrics,
    val deviceScale: Float
)

data class TextBackendSelection(
    val backend: TextBackendKind,
    val effectivePixelSize: Float
)

/**
 * Platform-agnostic render commands emitted by the UI tree.
 */
sealed class RenderCommand {
    /** Solid rectangle. */
    data class DrawRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val color: Int
    ) : RenderCommand()

    /** Saturation/value picker field for color picker UI. */
    data class DrawColorField(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val hueDeg: Float
    ) : RenderCommand()

    /** Hue gradient bar for color picker UI. */
    data class DrawHueBar(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : RenderCommand()

    /** Alpha gradient bar for color picker UI over existing checker background. */
    data class DrawAlphaBar(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val rgbColor: Int
    ) : RenderCommand()

    /** Procedural checkerboard background rendered efficiently by backend. */
    data class DrawCheckerboard(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val cellSize: Int,
        val lightColor: Int,
        val darkColor: Int,
        val offsetX: Int = 0,
        val offsetY: Int = 0
    ) : RenderCommand()

    /** Text draw command. */
    data class DrawText(
        val text: String,
        val x: Int,
        val y: Int,
        val fontId: String? = null,
        val textFormatting: TextFormatting = TextFormatting.None,
        val renderMode: TextRenderMode = TextRenderMode.Auto,
        val metrics: TextRenderMetrics,
        val baseStyle: TextRenderStyle,
        val styleSpans: List<TextStyleSpan> = emptyList(),
        val sourceKey: String? = null
    ) : RenderCommand() {
        /**
         * Returns a DrawText command with replaced color while preserving
         * all other fields. This avoids relying on Kotlin synthetic copy$default ABI.
         */
        fun withColor(newColor: Int): DrawText {
            return DrawText(
                text = text,
                x = x,
                y = y,
                fontId = fontId,
                textFormatting = textFormatting,
                renderMode = renderMode,
                metrics = metrics,
                baseStyle = baseStyle.copy(color = newColor),
                styleSpans = styleSpans,
                sourceKey = sourceKey
            )
        }
    }

    /** Image draw command. */
    data class DrawImage(
        val resource: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : RenderCommand()

    /**
     * Captures a small screen region into a reusable backend texture for magnifier rendering.
     * Source coordinates are in GUI space (top-left origin).
     */
    data class CaptureScreenRegion(
        val sourceX: Int,
        val sourceY: Int,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val fallbackColor: Int
    ) : RenderCommand()

    /** Draws previously captured screen region as a magnified textured quad. */
    data class DrawCapturedScreenRegion(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : RenderCommand()

    /** Item stack draw command. */
    data class DrawItemStack(
        val stack: ItemStackRef,
        val x: Int,
        val y: Int,
        val width: Int,
        val size: Int,
        val rotYDeg: Double = 0.0,
        val rotXDeg: Double = 0.0
    ) : RenderCommand()

    /** Pushes a clipping rectangle (GUI coordinates, top-left origin). */
    data class PushClip(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : RenderCommand()

    /** Pops the current clipping rectangle. */
    object PopClip : RenderCommand()

    /** Pushes transform for subsequent commands. */
    data class PushTransform(
        val originX: Float,
        val originY: Float,
        val translateX: Float,
        val translateY: Float,
        val scaleX: Float,
        val scaleY: Float,
        val rotateDeg: Float
    ) : RenderCommand()

    /** Pops current transform. */
    object PopTransform : RenderCommand()

    /** Multiplies current alpha by opacity (0..1). */
    data class PushOpacity(
        val opacity: Float
    ) : RenderCommand()

    /** Pops current opacity multiplier. */
    object PopOpacity : RenderCommand()
}
