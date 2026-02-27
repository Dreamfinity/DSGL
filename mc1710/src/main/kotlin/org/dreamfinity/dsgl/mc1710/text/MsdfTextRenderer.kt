package org.dreamfinity.dsgl.mc1710.text

import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.font.LoadedMsdfFont
import org.dreamfinity.dsgl.core.font.MsdfGlyph
import org.dreamfinity.dsgl.core.font.forEachCodepoint
import org.dreamfinity.dsgl.core.font.forEachCodepointIndexed
import org.dreamfinity.dsgl.core.font.toCodepointList
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.TextFormatting
import org.dreamfinity.dsgl.core.text.DecorationFontMetrics
import org.dreamfinity.dsgl.core.text.DecorationType
import org.dreamfinity.dsgl.core.text.GlyphDecorationSample
import org.dreamfinity.dsgl.core.text.MinecraftFormattingParser
import org.dreamfinity.dsgl.core.text.ObfuscationTextSelector
import org.dreamfinity.dsgl.core.text.TextStyleFlags
import org.dreamfinity.dsgl.core.text.TextDecorationLayout
import org.dreamfinity.dsgl.core.text.TextStyleMetrics
import org.dreamfinity.dsgl.core.text.TextVisualLine
import org.dreamfinity.dsgl.core.text.BOLD_ADVANCE_EXTRA_PX
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBFragmentShader
import org.lwjgl.opengl.ARBShaderObjects
import org.lwjgl.opengl.ARBVertexShader
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import java.awt.image.BufferedImage
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

internal class MsdfTextRenderer {
    private data class PreparedText(
        val text: String,
        val styleSpans: List<RenderCommand.TextStyleSpan>
    )

    private data class EffectiveGlyphStyle(
        val color: Int,
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val strikethrough: Boolean,
        val obfuscated: Boolean
    )

    private data class DecorationSegment(
        var startX: Float,
        var endX: Float,
        val y: Float,
        val thickness: Float,
        val color: Int
    )

    private data class ObfuscationBuckets(
        val byAdvanceBucket: Map<Int, List<MsdfGlyph>>,
        val expandedByAdvanceBucket: Map<Int, List<MsdfGlyph>>,
        val sortedKeys: List<Int>,
        val allGlyphs: List<MsdfGlyph>
    )

    private data class TextureHandle(
        val textureId: Int,
        val width: Int,
        val height: Int
    )

    private val textures: MutableMap<String, TextureHandle> = linkedMapOf()
    private var programId: Int = 0
    private var uniformAtlas: Int = -1
    private var uniformPxRange: Int = -1
    private val errorLogTimes: MutableMap<String, Long> = linkedMapOf()
    private val debugLogKeys: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private val debugGlyphResolutionEnabled: Boolean = java.lang.Boolean.getBoolean("dsgl.msdf.debug")
    private val obfuscationBuckets: MutableMap<String, ObfuscationBuckets> = linkedMapOf()
    private var obfuscationLastNano: Long = System.nanoTime()
    private var obfuscationAccumSec: Double = 0.0
    private var obfuscationTimeSlice: Long = 0

    fun measureText(text: String, fontId: String?, fontSize: Int?): Int {
        return FontRegistry.measureText(text, fontId, fontSize)
    }

    fun lineHeight(fontId: String?, fontSize: Int?): Int {
        return FontRegistry.lineHeight(fontId, fontSize)
    }

    fun draw(command: RenderCommand.DrawText, opacityMultiplier: Float) {
        val font = FontRegistry.get(command.fontId) ?: return
        val texture = textureFor(font) ?: return
        val fontSize = FontRegistry.resolveFontSize(command.fontSize)
        val prepared = prepareText(command)
        if (prepared.text.isEmpty()) return
        val debugDecorationGuidesEnabled = isDebugDecorationGuidesEnabled()
        updateObfuscationClock()
        val depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        if (depthWasEnabled) {
            GL11.glDisable(GL11.GL_DEPTH_TEST)
        }

        try {
            val fontScalePx = TextDecorationLayout.scalePx(fontSize, font.meta.metrics.emSize)
            val lineHeight = font.meta.lineHeightPx(fontSize).toFloat().coerceAtLeast(1f)
            val fontDecorationMetrics = DecorationFontMetrics(
                emSize = font.meta.metrics.emSize,
                lineHeightEm = font.meta.metrics.lineHeight,
                ascenderEm = font.meta.metrics.ascender,
                descenderEm = font.meta.metrics.descender,
                underlineYEm = font.meta.metrics.underlineY,
                underlineThicknessEm = font.meta.metrics.underlineThickness
            )

            var lineTop = command.y.toFloat()
            var baselineY = TextDecorationLayout.baselineY(
                lineTopY = lineTop,
                ascenderEm = font.meta.metrics.ascender,
                scalePx = fontScalePx
            )
            var cursorX = command.x.toFloat()
            var previousCodepoint: Int? = null
            val visualLines = ArrayList<TextVisualLine>(8)
            val glyphDecorationSamples = ArrayList<GlyphDecorationSample>(64)
            val underlineSegments = ArrayList<DecorationSegment>(24)
            val strikeSegments = ArrayList<DecorationSegment>(24)
            val baselineGuides = ArrayList<DecorationSegment>(8)
            val underlineGuides = ArrayList<DecorationSegment>(8)
            val strikeGuides = ArrayList<DecorationSegment>(8)
            var lineIndex = 0
            var glyphIndexInLine = 0
            var globalGlyphIndex = 0
            var lineGlyphStart = 0
            var lineStartX = command.x.toFloat()
            var lastObfuscatedCodepoint: Int? = null

            debugGlyphResolution(prepared.text, font)

            if (!useProgram()) return
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.textureId)
            ARBShaderObjects.glUniform1iARB(uniformAtlas, 0)
            ARBShaderObjects.glUniform1fARB(uniformPxRange, font.meta.atlas.distanceRange)

            val spans = prepared.styleSpans
            var spanIndex = 0
            var currentStyle: EffectiveGlyphStyle? = null

            GL11.glBegin(GL11.GL_QUADS)
            try {
                forEachCodepointIndexed(prepared.text) { codePoint, startIndex, _ ->
                    if (codePoint == '\n'.code) {
                        val line = TextVisualLine(
                            lineIndex = lineIndex,
                            lineTopY = lineTop,
                            baselineY = baselineY,
                            lineHeightPx = lineHeight,
                            glyphStartIndex = lineGlyphStart,
                            glyphEndIndexExclusive = globalGlyphIndex
                        )
                        visualLines += line
                        if (debugDecorationGuidesEnabled && cursorX > lineStartX) {
                            appendDecorationDebugGuides(
                                baselineGuides = baselineGuides,
                                underlineGuides = underlineGuides,
                                strikeGuides = strikeGuides,
                                startX = lineStartX,
                                endX = cursorX,
                                line = line,
                                lineMetrics = TextDecorationLayout.resolveLineMetrics(
                                    line = line,
                                    fontMetrics = fontDecorationMetrics,
                                    fontPx = fontSize
                                )
                            )
                        }
                        cursorX = command.x.toFloat()
                        lineTop += lineHeight
                        baselineY = TextDecorationLayout.baselineY(
                            lineTopY = lineTop,
                            ascenderEm = font.meta.metrics.ascender,
                            scalePx = fontScalePx
                        )
                        previousCodepoint = null
                        lineIndex += 1
                        glyphIndexInLine = 0
                        lineGlyphStart = globalGlyphIndex
                        lineStartX = cursorX
                        lastObfuscatedCodepoint = null
                        return@forEachCodepointIndexed
                    }

                    val runItem = font.meta.resolveGlyphRun(codePoint, fontSize) ?: run {
                        previousCodepoint = null
                        glyphIndexInLine += 1
                        return@forEachCodepointIndexed
                    }
                    val previous = previousCodepoint
                    if (previous != null) {
                        cursorX += font.meta.kerningPx(previous, runItem.kerningCodepoint, fontSize)
                    }

                    while (spanIndex < spans.size && startIndex >= spans[spanIndex].end) {
                        spanIndex += 1
                    }
                    val style = resolveGlyphStyle(command, spans, spanIndex, startIndex, opacityMultiplier)
                    if (style != currentStyle) {
                        val r = ((style.color ushr 16) and 0xFF) / 255f
                        val g = ((style.color ushr 8) and 0xFF) / 255f
                        val b = (style.color and 0xFF) / 255f
                        val a = ((style.color ushr 24) and 0xFF) / 255f
                        GL11.glColor4f(r, g, b, a)
                        currentStyle = style
                    }

                    val drawGlyph = if (style.obfuscated && ObfuscationTextSelector.shouldObfuscateCodepoint(codePoint)) {
                        resolveObfuscatedGlyph(
                            font = font,
                            sourceKey = command.sourceKey ?: command.text,
                            runItem = runItem,
                            lineIndex = lineIndex,
                            glyphIndexInLine = glyphIndexInLine,
                            avoidCodepoint = lastObfuscatedCodepoint
                        )
                    } else {
                        runItem.glyph
                    }

                    val boldAdvance = if (style.bold && !TextStyleMetrics.isWhitespaceCodepoint(codePoint)) {
                        BOLD_ADVANCE_EXTRA_PX.toFloat()
                    } else {
                        0f
                    }
                    val glyphAdvance = runItem.advancePx + boldAdvance

                    if (runItem.draw) {
                        val glyph = drawGlyph
                        if (glyph != null) {
                            emitGlyphQuad(
                                glyph = glyph,
                                baselineY = baselineY,
                                cursorX = cursorX,
                                atlasWidth = texture.width,
                                atlasHeight = texture.height,
                                fontScalePx = fontScalePx,
                                italic = style.italic,
                                italicSkewPx = fontScalePx * 0.2f
                            )
                            if (style.bold) {
                                emitGlyphQuad(
                                    glyph = glyph,
                                    baselineY = baselineY,
                                    cursorX = cursorX + 0.75f,
                                    atlasWidth = texture.width,
                                    atlasHeight = texture.height,
                                    fontScalePx = fontScalePx,
                                    italic = style.italic,
                                    italicSkewPx = fontScalePx * 0.2f
                                )
                            }
                        }
                    }

                    val glyphStartX = cursorX
                    val glyphEndX = glyphStartX + glyphAdvance
                    if (glyphEndX > glyphStartX) {
                        glyphDecorationSamples += GlyphDecorationSample(
                            lineIndex = lineIndex,
                            glyphIndex = globalGlyphIndex,
                            xStart = glyphStartX,
                            xEnd = glyphEndX,
                            color = style.color,
                            underline = style.underline,
                            strikethrough = style.strikethrough
                        )
                    }

                    cursorX += glyphAdvance
                    previousCodepoint = runItem.kerningCodepoint
                    glyphIndexInLine += 1
                    globalGlyphIndex += 1
                    lastObfuscatedCodepoint = if (style.obfuscated) drawGlyph?.codepoint else null
                }
            } finally {
                GL11.glEnd()
                ARBShaderObjects.glUseProgramObjectARB(0)
            }

            val lastLine = TextVisualLine(
                lineIndex = lineIndex,
                lineTopY = lineTop,
                baselineY = baselineY,
                lineHeightPx = lineHeight,
                glyphStartIndex = lineGlyphStart,
                glyphEndIndexExclusive = globalGlyphIndex
            )
            visualLines += lastLine
            if (debugDecorationGuidesEnabled && cursorX > lineStartX) {
                appendDecorationDebugGuides(
                    baselineGuides = baselineGuides,
                    underlineGuides = underlineGuides,
                    strikeGuides = strikeGuides,
                    startX = lineStartX,
                    endX = cursorX,
                    line = lastLine,
                    lineMetrics = TextDecorationLayout.resolveLineMetrics(
                        line = lastLine,
                        fontMetrics = fontDecorationMetrics,
                        fontPx = fontSize
                    )
                )
            }

            TextDecorationLayout.buildDecorationQuads(
                lines = visualLines,
                glyphs = glyphDecorationSamples,
                fontMetrics = fontDecorationMetrics,
                fontPx = fontSize
            ).forEach { quad ->
                val segment = DecorationSegment(
                    startX = quad.xStart,
                    endX = quad.xEnd,
                    y = quad.y,
                    thickness = quad.thickness,
                    color = quad.color
                )
                when (quad.type) {
                    DecorationType.Underline -> underlineSegments += segment
                    DecorationType.Strikethrough -> strikeSegments += segment
                }
            }

            drawDecorationSegments(underlineSegments)
            drawDecorationSegments(strikeSegments)
            if (debugDecorationGuidesEnabled) {
                drawDecorationSegments(baselineGuides)
                drawDecorationSegments(underlineGuides)
                drawDecorationSegments(strikeGuides)
            }
        } finally {
            if (depthWasEnabled) {
                GL11.glEnable(GL11.GL_DEPTH_TEST)
            }
        }
    }

    private fun prepareText(command: RenderCommand.DrawText): PreparedText {
        if (command.textFormatting != TextFormatting.Minecraft) {
            return PreparedText(
                text = command.text,
                styleSpans = command.textStyleSpans
            )
        }

        if (command.textStyleSpans.isNotEmpty()) {
            return PreparedText(
                text = command.text,
                styleSpans = command.textStyleSpans
            )
        }

        val parsed = MinecraftFormattingParser.parse(command.text, TextFormatting.Minecraft)
        val spans = MinecraftFormattingParser.resolveStyleSpans(
            parsed = parsed,
            baseColor = command.color,
            baseFlags = TextStyleFlags(
                bold = command.bold,
                italic = command.italic,
                underline = command.underline,
                strikethrough = command.strikethrough,
                obfuscated = command.obfuscated
            )
        ).map { span ->
            RenderCommand.TextStyleSpan(
                start = span.start,
                end = span.end,
                color = span.color,
                bold = span.flags.bold,
                italic = span.flags.italic,
                underline = span.flags.underline,
                strikethrough = span.flags.strikethrough,
                obfuscated = span.flags.obfuscated
            )
        }
        return PreparedText(
            text = parsed.plainText,
            styleSpans = spans
        )
    }

    private fun emitGlyphQuad(
        glyph: MsdfGlyph,
        baselineY: Float,
        cursorX: Float,
        atlasWidth: Int,
        atlasHeight: Int,
        fontScalePx: Float,
        italic: Boolean,
        italicSkewPx: Float
    ) {
        val plane = glyph.planeBounds ?: return
        val atlas = glyph.atlasBounds ?: return

        val x0 = cursorX + plane.left * fontScalePx
        val x1 = cursorX + plane.right * fontScalePx
        val y0 = baselineY - plane.top * fontScalePx
        val y1 = baselineY - plane.bottom * fontScalePx
        val skew = if (italic) italicSkewPx else 0f

        val u0 = atlas.left / atlasWidth.toFloat()
        val u1 = atlas.right / atlasWidth.toFloat()
        val v0 = atlas.bottom / atlasHeight.toFloat()
        val v1 = atlas.top / atlasHeight.toFloat()

        GL11.glTexCoord2f(u0, v0)
        GL11.glVertex2f(x0, y1)
        GL11.glTexCoord2f(u1, v0)
        GL11.glVertex2f(x1, y1)
        GL11.glTexCoord2f(u1, v1)
        GL11.glVertex2f(x1 + skew, y0)
        GL11.glTexCoord2f(u0, v1)
        GL11.glVertex2f(x0 + skew, y0)
    }

    private fun resolveGlyphStyle(
        command: RenderCommand.DrawText,
        spans: List<RenderCommand.TextStyleSpan>,
        spanIndex: Int,
        charStartIndex: Int,
        opacityMultiplier: Float
    ): EffectiveGlyphStyle {
        if (spanIndex < spans.size) {
            val span = spans[spanIndex]
            if (charStartIndex >= span.start && charStartIndex < span.end) {
                return EffectiveGlyphStyle(
                    color = withOpacity(span.color, opacityMultiplier),
                    bold = span.bold,
                    italic = span.italic,
                    underline = span.underline,
                    strikethrough = span.strikethrough,
                    obfuscated = span.obfuscated
                )
            }
        }
        return EffectiveGlyphStyle(
            color = withOpacity(command.color, opacityMultiplier),
            bold = command.bold,
            italic = command.italic,
            underline = command.underline,
            strikethrough = command.strikethrough,
            obfuscated = command.obfuscated
        )
    }

    private fun appendDecorationDebugGuides(
        baselineGuides: MutableList<DecorationSegment>,
        underlineGuides: MutableList<DecorationSegment>,
        strikeGuides: MutableList<DecorationSegment>,
        startX: Float,
        endX: Float,
        line: TextVisualLine,
        lineMetrics: org.dreamfinity.dsgl.core.text.ResolvedLineDecorationMetrics
    ) {
        if (endX <= startX) return
        appendDecorationSegment(
            list = baselineGuides,
            startX = startX,
            endX = endX,
            y = line.baselineY.coerceIn(line.lineTopY, line.lineTopY + line.lineHeightPx),
            thickness = 1f,
            color = 0x66FFAA00
        )
        appendDecorationSegment(
            list = underlineGuides,
            startX = startX,
            endX = endX,
            y = lineMetrics.underlineY,
            thickness = lineMetrics.underlineThickness,
            color = 0x6600FF00
        )
        appendDecorationSegment(
            list = strikeGuides,
            startX = startX,
            endX = endX,
            y = lineMetrics.strikethroughY,
            thickness = lineMetrics.strikethroughThickness,
            color = 0x66FF00FF
        )
    }

    private fun appendDecorationSegment(
        list: MutableList<DecorationSegment>,
        startX: Float,
        endX: Float,
        y: Float,
        thickness: Float,
        color: Int
    ) {
        if (endX <= startX) return
        val last = list.lastOrNull()
        if (last != null &&
            kotlin.math.abs(last.endX - startX) <= 0.51f &&
            kotlin.math.abs(last.y - y) <= 0.51f &&
            kotlin.math.abs(last.thickness - thickness) <= 0.1f &&
            last.color == color
        ) {
            last.endX = endX
            return
        }
        list += DecorationSegment(
            startX = startX,
            endX = endX,
            y = y,
            thickness = thickness,
            color = color
        )
    }

    private fun drawDecorationSegments(segments: List<DecorationSegment>) {
        if (segments.isEmpty()) return
        val texture2dWasEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
        val blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        val alphaTestWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
        val lightingWasEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING)
        val cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        ARBShaderObjects.glUseProgramObjectARB(0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
        if (lightingWasEnabled) GL11.glDisable(GL11.GL_LIGHTING)
        if (alphaTestWasEnabled) GL11.glDisable(GL11.GL_ALPHA_TEST)
        if (cullWasEnabled) GL11.glDisable(GL11.GL_CULL_FACE)
        if (!blendWasEnabled) GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        if (texture2dWasEnabled) GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glBegin(GL11.GL_QUADS)
        try {
            segments.forEach { segment ->
                val color = segment.color
                val r = ((color ushr 16) and 0xFF) / 255f
                val g = ((color ushr 8) and 0xFF) / 255f
                val b = (color and 0xFF) / 255f
                val a = ((color ushr 24) and 0xFF) / 255f
                GL11.glColor4f(r, g, b, a)
                val y0 = segment.y
                val y1 = maxOf(
                    y0 + 0.5f,
                    segment.y + segment.thickness
                )
                GL11.glVertex2f(segment.startX, y0)
                GL11.glVertex2f(segment.endX, y0)
                GL11.glVertex2f(segment.endX, y1)
                GL11.glVertex2f(segment.startX, y1)
            }
        } finally {
            GL11.glEnd()
            if (texture2dWasEnabled) GL11.glEnable(GL11.GL_TEXTURE_2D)
            if (!blendWasEnabled) GL11.glDisable(GL11.GL_BLEND)
            if (alphaTestWasEnabled) GL11.glEnable(GL11.GL_ALPHA_TEST)
            if (lightingWasEnabled) GL11.glEnable(GL11.GL_LIGHTING)
            if (cullWasEnabled) GL11.glEnable(GL11.GL_CULL_FACE)
        }
    }

    private fun resolveObfuscatedGlyph(
        font: LoadedMsdfFont,
        sourceKey: String,
        runItem: org.dreamfinity.dsgl.core.font.MsdfGlyphRunItem,
        lineIndex: Int,
        glyphIndexInLine: Int,
        avoidCodepoint: Int?
    ): MsdfGlyph? {
        val original = runItem.glyph ?: return null
        val buckets = obfuscationBuckets.getOrPut(font.descriptor.fontId) {
            buildObfuscationBuckets(font)
        }
        if (buckets.allGlyphs.isEmpty()) return original
        val baseBucket = advanceBucketKey(original.advance)
        val candidates = buckets.expandedByAdvanceBucket[baseBucket]
            ?: nearestExpandedCandidates(buckets, baseBucket)
            ?: buckets.allGlyphs
        if (candidates.isEmpty()) return original
        val primaryIndex = ObfuscationTextSelector.selectCandidateIndex(
            sourceKey = sourceKey,
            lineIndex,
            glyphIndexInLine = glyphIndexInLine,
            timeSlice = obfuscationTimeSlice,
            originalCodepoint = original.codepoint,
            candidateCount = candidates.size
        )
        val primary = candidates[primaryIndex]
        if (avoidCodepoint == null || candidates.size <= 1 || primary.codepoint != avoidCodepoint) {
            return primary
        }
        val secondaryIndex = (primaryIndex + 1 + (obfuscationTimeSlice.toInt() and 3)) % candidates.size
        val secondary = candidates[secondaryIndex]
        if (secondary.codepoint != avoidCodepoint) return secondary
        return candidates.firstOrNull { it.codepoint != avoidCodepoint } ?: primary
    }

    private fun buildObfuscationBuckets(font: LoadedMsdfFont): ObfuscationBuckets {
        val glyphs = font.meta.glyphsByCodepoint.values
            .filter { glyph ->
                glyph.drawable && !TextStyleMetrics.isWhitespaceCodepoint(glyph.codepoint)
            }
        if (glyphs.isEmpty()) {
            return ObfuscationBuckets(
                byAdvanceBucket = emptyMap(),
                expandedByAdvanceBucket = emptyMap(),
                sortedKeys = emptyList(),
                allGlyphs = emptyList()
            )
        }
        val grouped = linkedMapOf<Int, MutableList<MsdfGlyph>>()
        glyphs.forEach { glyph ->
            grouped.getOrPut(advanceBucketKey(glyph.advance)) { ArrayList() }.add(glyph)
        }
        val sorted = grouped.keys.sorted()
        val frozenGrouped = grouped.mapValues { (_, value) -> value.toList() }
        val expanded = linkedMapOf<Int, List<MsdfGlyph>>()
        sorted.forEach { key ->
            expanded[key] = expandCandidatesForBucket(
                grouped = frozenGrouped,
                sortedKeys = sorted,
                baseKey = key
            )
        }
        return ObfuscationBuckets(
            byAdvanceBucket = frozenGrouped,
            expandedByAdvanceBucket = expanded,
            sortedKeys = sorted,
            allGlyphs = glyphs
        )
    }

    private fun advanceBucketKey(advance: Float): Int {
        return (advance * 100f).toInt()
    }

    private fun nearestExpandedCandidates(buckets: ObfuscationBuckets, key: Int): List<MsdfGlyph>? {
        val keys = buckets.sortedKeys
        if (keys.isEmpty()) return null
        var nearest = keys.first()
        var distance = kotlin.math.abs(nearest - key)
        keys.forEach { candidate ->
            val nextDistance = kotlin.math.abs(candidate - key)
            if (nextDistance < distance) {
                distance = nextDistance
                nearest = candidate
            }
        }
        return buckets.expandedByAdvanceBucket[nearest]
    }

    private fun expandCandidatesForBucket(
        grouped: Map<Int, List<MsdfGlyph>>,
        sortedKeys: List<Int>,
        baseKey: Int
    ): List<MsdfGlyph> {
        val byDistance = sortedKeys.sortedBy { key -> kotlin.math.abs(key - baseKey) }
        val out = ArrayList<MsdfGlyph>(MIN_OBFUSCATION_CANDIDATES)
        byDistance.forEach { key ->
            val candidates = grouped[key].orEmpty()
            if (candidates.isNotEmpty()) {
                out.addAll(candidates)
            }
            if (out.size >= MIN_OBFUSCATION_CANDIDATES) {
                return@forEach
            }
        }
        return if (out.isEmpty()) grouped.values.flatten() else out
    }

    private fun updateObfuscationClock() {
        val now = System.nanoTime()
        val dt = (now - obfuscationLastNano).coerceAtLeast(0L) / 1_000_000_000.0
        obfuscationLastNano = now
        obfuscationAccumSec += dt
        val step = OBFUSCATION_TIME_STEP_SEC
        if (obfuscationAccumSec >= step) {
            val ticks = (obfuscationAccumSec / step).toLong()
            obfuscationAccumSec -= ticks * step
            obfuscationTimeSlice += ticks
        }
    }

    private fun textureFor(font: LoadedMsdfFont): TextureHandle? {
        val atlasPath = font.descriptor.atlasResourcePath
        textures[atlasPath]?.let { return it }
        return runCatching {
            val image = readImage(atlasPath) ?: return null
            val handle = uploadTexture(image)
            textures[atlasPath] = handle
            handle
        }.onFailure { error ->
            logRateLimited("texture:$atlasPath", "[DSGL-MSDF] Failed to load atlas '$atlasPath': ${error.message}")
        }.getOrNull()
    }

    private fun readImage(resourcePath: String): BufferedImage? {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath) ?: return null
        return stream.use { input -> ImageIO.read(input) }
    }

    private fun uploadTexture(image: BufferedImage): TextureHandle {
        val width = image.width.coerceAtLeast(1)
        val height = image.height.coerceAtLeast(1)
        val buffer = BufferUtils.createByteBuffer(width * height * 4)

        for (y in (height - 1) downTo 0) {
            for (x in 0 until width) {
                val argb = image.getRGB(x, y)
                buffer.put(((argb shr 16) and 0xFF).toByte())
                buffer.put(((argb shr 8) and 0xFF).toByte())
                buffer.put((argb and 0xFF).toByte())
                buffer.put(((argb ushr 24) and 0xFF).toByte())
            }
        }
        buffer.flip()

        val textureId = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA8,
            width,
            height,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            buffer
        )
        return TextureHandle(textureId = textureId, width = width, height = height)
    }

    private fun useProgram(): Boolean {
        if (programId == 0) {
            val loaded = runCatching { createProgram() }
                .onFailure { error ->
                    logRateLimited("shader:init", "[DSGL-MSDF] Failed to initialize shader: ${error.message}")
                }
                .getOrNull() ?: return false
            programId = loaded
            uniformAtlas = ARBShaderObjects.glGetUniformLocationARB(programId, "uAtlas")
            uniformPxRange = ARBShaderObjects.glGetUniformLocationARB(programId, "uPxRange")
        }
        ARBShaderObjects.glUseProgramObjectARB(programId)
        return true
    }

    private fun createProgram(): Int {
        val vertexShader = compileShader(
            type = ARBVertexShader.GL_VERTEX_SHADER_ARB,
            source = VERTEX_SHADER_SOURCE
        )
        val fragmentShader = compileShader(
            type = ARBFragmentShader.GL_FRAGMENT_SHADER_ARB,
            source = FRAGMENT_SHADER_SOURCE
        )

        val program = ARBShaderObjects.glCreateProgramObjectARB()
        ARBShaderObjects.glAttachObjectARB(program, vertexShader)
        ARBShaderObjects.glAttachObjectARB(program, fragmentShader)
        ARBShaderObjects.glLinkProgramARB(program)
        val linkStatus = ARBShaderObjects.glGetObjectParameteriARB(
            program,
            ARBShaderObjects.GL_OBJECT_LINK_STATUS_ARB
        )
        if (linkStatus == GL11.GL_FALSE) {
            val info = ARBShaderObjects.glGetInfoLogARB(program, 4096)
            throw IllegalStateException("Program link failed: $info")
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = ARBShaderObjects.glCreateShaderObjectARB(type)
        ARBShaderObjects.glShaderSourceARB(shader, source)
        ARBShaderObjects.glCompileShaderARB(shader)
        val compileStatus = ARBShaderObjects.glGetObjectParameteriARB(
            shader,
            ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB
        )
        if (compileStatus == GL11.GL_FALSE) {
            val info = ARBShaderObjects.glGetInfoLogARB(shader, 4096)
            throw IllegalStateException("Shader compile failed: $info")
        }
        return shader
    }

    private fun withOpacity(color: Int, opacityMultiplier: Float): Int {
        if (opacityMultiplier >= 0.999f) return color
        val alpha = ((color ushr 24) and 0xFF)
        val scaled = (alpha * opacityMultiplier).toInt().coerceIn(0, 255)
        return (color and 0x00FF_FFFF) or (scaled shl 24)
    }

    private fun logRateLimited(key: String, message: String) {
        val now = System.currentTimeMillis()
        val previous = errorLogTimes[key] ?: 0L
        if (now - previous < 3_000L) return
        errorLogTimes[key] = now
        println(message)
    }

    private fun isDebugDecorationGuidesEnabled(): Boolean {
        return java.lang.Boolean.getBoolean("dsgl.msdf.debug.decorations")
    }

    private fun debugGlyphResolution(text: String, font: LoadedMsdfFont) {
        if (!debugGlyphResolutionEnabled) return
        val sample = text.take(64)
        val key = "${font.descriptor.fontId}|$sample"
        if (!debugLogKeys.add(key)) return
        val codepoints = sample.toCodepointList()
        println(
            "[DSGL-MSDF] text='$sample' codepoints=" +
                codepoints.joinToString(",") { "U+%04X".format(it) }
        )

        codepoints.forEach { codepoint ->
            val direct = font.meta.glyph(codepoint)
            val runItem = font.meta.resolveGlyphRun(codepoint, FontRegistry.DEFAULT_FONT_SIZE)
            val resolved = runItem?.glyph
            val resolvedCp = runItem?.kerningCodepoint?.let { "U+%04X".format(it) } ?: "null"
            val uv = resolved?.atlasBounds?.let { "[${it.left},${it.bottom},${it.right},${it.top}]" } ?: "null"
            val plane = resolved?.planeBounds?.let { "[${it.left},${it.bottom},${it.right},${it.top}]" } ?: "null"
            println(
                "[DSGL-MSDF] cp=U+%04X found=%s resolved=%s draw=%s fallback=%s uv=%s plane=%s".format(
                    codepoint,
                    direct != null,
                    resolvedCp,
                    runItem?.draw ?: false,
                    runItem?.usedFallback ?: false,
                    uv,
                    plane
                )
            )
        }
    }

    companion object {
        private const val MIN_OBFUSCATION_CANDIDATES: Int = 24
        private const val OBFUSCATION_TIME_STEP_SEC: Double = 0.05

        private const val VERTEX_SHADER_SOURCE: String = """
            #version 120
            varying vec2 vTexCoord;
            varying vec4 vColor;

            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                vTexCoord = gl_MultiTexCoord0.st;
                vColor = gl_Color;
            }
        """

        private const val FRAGMENT_SHADER_SOURCE: String = """
            #version 120
            uniform sampler2D uAtlas;
            uniform float uPxRange;
            varying vec2 vTexCoord;
            varying vec4 vColor;

            float median(float a, float b, float c) {
                return max(min(a, b), min(max(a, b), c));
            }

            void main() {
                vec4 sample = texture2D(uAtlas, vTexCoord);
                float dist = max(median(sample.r, sample.g, sample.b), sample.a) - 0.5;
                float w = 0.5 / max(uPxRange, 0.0001);
                float alpha = smoothstep(-w, w, dist);
                gl_FragColor = vec4(vColor.rgb, vColor.a * alpha);
            }
        """
    }
}
