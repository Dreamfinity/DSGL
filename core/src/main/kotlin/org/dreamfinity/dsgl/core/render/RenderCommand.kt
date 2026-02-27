package org.dreamfinity.dsgl.core.render

import org.dreamfinity.dsgl.core.style.TextFormatting

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

    /** Text draw command. */
    data class DrawText(
        val text: String,
        val x: Int,
        val y: Int,
        val color: Int,
        val fontId: String? = null,
        val fontSize: Int? = null,
        val textFormatting: TextFormatting = TextFormatting.None,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        val obfuscated: Boolean = false,
        val textStyleSpans: List<TextStyleSpan> = emptyList(),
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
                color = newColor,
                fontId = fontId,
                fontSize = fontSize,
                textFormatting = textFormatting,
                bold = bold,
                italic = italic,
                underline = underline,
                strikethrough = strikethrough,
                obfuscated = obfuscated,
                textStyleSpans = textStyleSpans,
                sourceKey = sourceKey
            )
        }
    }

    data class TextStyleSpan(
        val start: Int,
        val end: Int,
        val color: Int,
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val strikethrough: Boolean,
        val obfuscated: Boolean
    )

    /** Image draw command. */
    data class DrawImage(
        val resource: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : RenderCommand()

    /** Item stack draw command. */
    data class DrawItemStack(
        val stack: org.dreamfinity.dsgl.core.ItemStackRef,
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
