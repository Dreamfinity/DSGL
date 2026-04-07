package org.dreamfinity.dsgl.mc1710.text

import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.font.FontTextureHandle
import org.dreamfinity.dsgl.core.font.LoadedMsdfFont
import org.dreamfinity.dsgl.core.font.MsdfGlyph
import org.dreamfinity.dsgl.core.font.ShapedGlyph
import org.dreamfinity.dsgl.core.font.ShapedText
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.render.TextBackendKind
import org.dreamfinity.dsgl.core.render.TextDecorations
import org.dreamfinity.dsgl.core.render.TextRenderStyle
import org.dreamfinity.dsgl.core.render.TextStyleOverride
import org.dreamfinity.dsgl.core.render.TextStyleSpan
import org.dreamfinity.dsgl.core.render.TextWeight
import org.dreamfinity.dsgl.core.style.TextFormatting
import org.dreamfinity.dsgl.core.text.DecorationFontMetrics
import org.dreamfinity.dsgl.core.text.MinecraftFormattingParser
import org.dreamfinity.dsgl.core.text.ObfuscationTextSelector
import org.dreamfinity.dsgl.core.text.TextDecorationLayout
import org.dreamfinity.dsgl.core.text.TextStyleFlags
import org.dreamfinity.dsgl.core.text.TextStyleMetrics
import org.dreamfinity.dsgl.core.text.TextVisualLine
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBFragmentShader
import org.lwjgl.opengl.ARBShaderObjects
import org.lwjgl.opengl.ARBVertexShader
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

private data class PreparedText(
    val text: String,
    val styleSpans: List<TextStyleSpan>
)

private data class LayoutCacheKey(
    val text: String,
    val primaryFontId: String,
    val fontSize: Int,
    val textFormatting: TextFormatting,
    val baseFlagsMask: Int,
    val styleSpansHash: Int
)

private data class CachedLineLayout(
    val start: Int,
    val shaped: ShapedText
)

private data class LayoutCacheEntry(val lines: List<CachedLineLayout>)

private data class RendererDebugCounters(
    var drawCalls: Long = 0L,
    var layoutCacheHits: Long = 0L,
    var layoutCacheMisses: Long = 0L,
    var glyphVectorRequests: Long = 0L,
    var glyphResolutionRequests: Long = 0L,
    var textureUploads: Long = 0L,
    var textureUploadBytes: Long = 0L
)

private data class ObfuscationBuckets(
    val byAdvanceBucket: Map<Int, List<MsdfGlyph>>,
    val expandedByAdvanceBucket: Map<Int, List<MsdfGlyph>>,
    val sortedKeys: List<Int>,
    val allGlyphs: List<MsdfGlyph>
)

private data class LineSlice(
    val start: Int,
    val endExclusive: Int
)

private data class GlyphRenderState(
    val screenPxRange: Float,
    val weightBiasPx: Float
)

internal data class DecorationRenderQuad(
    val kind: Int,
    val startX: Float,
    val endX: Float,
    val y: Float,
    val thickness: Float,
    val color: Int
)

internal class Mtsdf2DTextBackend : TextDrawBackend {
    override val kind: TextBackendKind = TextBackendKind.Mtsdf2D

    private val textures: MutableMap<String, FontTextureHandle> = linkedMapOf()
    private val layoutCache: MutableMap<LayoutCacheKey, LayoutCacheEntry> =
        object : LinkedHashMap<LayoutCacheKey, LayoutCacheEntry>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutCacheKey, LayoutCacheEntry>?): Boolean {
                return size > MAX_LAYOUT_CACHE_ENTRIES
            }
        }
    private val errorLogTimes: MutableMap<String, Long> = linkedMapOf()
    private val debugLogKeys: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private val obfuscationBuckets: MutableMap<String, ObfuscationBuckets> = linkedMapOf()
    private val decorationQuads: MutableList<DecorationRenderQuad> = ArrayList(96)
    private val debugCounters = RendererDebugCounters()
    private val maxTextureSize: Int by lazy { GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE).coerceAtLeast(1) }
    private val debugGlyphResolutionEnabled: Boolean by lazy(mode = LazyThreadSafetyMode.NONE) {
        java.lang.Boolean.getBoolean("dsgl.msdf.debug")
    }
    private val debugPerformanceEnabled: Boolean by lazy(mode = LazyThreadSafetyMode.NONE) {
        java.lang.Boolean.getBoolean("dsgl.msdf.debug.performance")
    }
    private var debugLastLogMs: Long = 0L
    private var obfuscationLastNano: Long = System.nanoTime()
    private var obfuscationAccumSec: Double = 0.0
    private var obfuscationTimeSlice: Long = 0L
    private var programId: Int = 0
    private var uniformAtlas: Int = -1
    private var uniformScreenPxRange: Int = -1
    private var uniformWeightBiasPx: Int = -1

    override fun draw(
        command: RenderCommand.DrawText,
        opacityMultiplier: Float,
        deviceScale: Float
    ) {
        debugCounters.drawCalls += 1
        val primaryFont = FontRegistry.get(command.fontId) ?: return
        val runtimeFallbackFont = FontRegistry.get(FontRegistry.FALLBACK_FONT_ID)
            ?.takeIf { it.descriptor.fontId != primaryFont.descriptor.fontId }
        val missingGlyphFont = FontRegistry.get(FontRegistry.FALLBACK_FONT_ID)
            ?: FontRegistry.get(FontRegistry.DEFAULT_FONT_ID)
        val fontSize = command.metrics.fontSizePx.coerceAtLeast(1)
        val prepared = prepareText(command)
        val layoutEntry = getOrBuildLayoutCacheEntry(
            command = command,
            prepared = prepared,
            primaryFont = primaryFont,
            fontSize = fontSize
        )
        if (prepared.text.isEmpty() || layoutEntry.lines.isEmpty()) return

        val debugDecorationGuidesEnabled = MsdfRuntimeDebugSettings.decorationGuidesEnabled
        updateObfuscationClock()
        decorationQuads.clear()

        val depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        if (depthWasEnabled) {
            GL11.glDisable(GL11.GL_DEPTH_TEST)
        }

        try {
            val lineHeight = command.metrics.lineHeightPx.toFloat().coerceAtLeast(1f)
            val fontDecorationMetrics = DecorationFontMetrics(
                emSize = primaryFont.meta.metrics.emSize,
                lineHeightEm = primaryFont.meta.metrics.lineHeight,
                ascenderEm = primaryFont.meta.metrics.ascender,
                descenderEm = primaryFont.meta.metrics.descender,
                underlineYEm = primaryFont.meta.metrics.underlineY,
                underlineThicknessEm = primaryFont.meta.metrics.underlineThickness
            )
            debugGlyphResolution(prepared.text, primaryFont)

            if (!useProgram()) return

            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            var lineTop = command.y.toFloat()
            var lineIndex = 0
            var spanIndex = 0
            var globalGlyphIndex = 0
            var activeFontId: String? = null
            var activeTexture: FontTextureHandle? = null
            var glBegun = false
            var currentDrawColor: Int = Int.MIN_VALUE
            var activeResolvedSpanIndex = Int.MIN_VALUE
            var activeStyle = command.baseStyle
            var activeStyleColor = withOpacity(command.baseStyle.color, opacityMultiplier)
            var activeStyleFlags = baseFlagsMask(command.baseStyle)
            var activeScreenPxRange = Float.NaN
            var activeWeightBiasPx = Float.NaN

            fun beginForFont(font: LoadedMsdfFont): FontTextureHandle? {
                if (activeFontId == font.descriptor.fontId && activeTexture != null && glBegun) {
                    return activeTexture
                }
                if (glBegun) {
                    GL11.glEnd()
                    glBegun = false
                }
                val texture = textureFor(font) ?: return null
                GL13.glActiveTexture(GL13.GL_TEXTURE0)
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.textureId)
                ARBShaderObjects.glUniform1iARB(uniformAtlas, 0)
                GL11.glBegin(GL11.GL_QUADS)
                glBegun = true
                activeFontId = font.descriptor.fontId
                activeTexture = texture
                currentDrawColor = Int.MIN_VALUE
                activeScreenPxRange = Float.NaN
                activeWeightBiasPx = Float.NaN
                return texture
            }

            try {
                layoutEntry.lines.forEach { line ->
                    val baselineY = lineTop + command.metrics.ascenderPx
                    val shaped = line.shaped
                    val lineRecord = TextVisualLine(
                        lineIndex = lineIndex,
                        lineTopY = lineTop,
                        baselineY = baselineY,
                        lineHeightPx = lineHeight,
                        glyphStartIndex = globalGlyphIndex,
                        glyphEndIndexExclusive = globalGlyphIndex + shaped.glyphs.size
                    )
                    val lineMetrics = TextDecorationLayout.resolveLineMetrics(
                        line = lineRecord,
                        fontMetrics = fontDecorationMetrics,
                        fontPx = fontSize
                    )
                    var lineStartX = command.x.toFloat()
                    var lineEndX = lineStartX
                    var glyphIndexInLine = 0
                    var lastObfuscatedGlyphIndex: Int? = null
                    var cachedShapedFontId: String? = null
                    var cachedShapedFont: LoadedMsdfFont = primaryFont

                    shaped.glyphs.forEach { shapedGlyph ->
                        val globalCharStart = line.start + shapedGlyph.charStart
                        while (spanIndex < prepared.styleSpans.size && globalCharStart >= prepared.styleSpans[spanIndex].end) {
                            spanIndex += 1
                        }
                        val resolvedSpanIndex = if (
                            spanIndex < prepared.styleSpans.size &&
                            globalCharStart >= prepared.styleSpans[spanIndex].start &&
                            globalCharStart < prepared.styleSpans[spanIndex].end
                        ) {
                            spanIndex
                        } else {
                            -1
                        }
                        if (resolvedSpanIndex != activeResolvedSpanIndex) {
                            activeResolvedSpanIndex = resolvedSpanIndex
                            activeStyle = if (resolvedSpanIndex >= 0) {
                                resolvedStyle(command.baseStyle, prepared.styleSpans[resolvedSpanIndex].style)
                            } else {
                                command.baseStyle
                            }
                            activeStyleColor = withOpacity(activeStyle.color, opacityMultiplier)
                            activeStyleFlags = baseFlagsMask(activeStyle)
                        }

                        val shapedFont = if (shapedGlyph.fontId == cachedShapedFontId) {
                            cachedShapedFont
                        } else {
                            (FontRegistry.get(shapedGlyph.fontId) ?: primaryFont).also { resolved ->
                                cachedShapedFontId = shapedGlyph.fontId
                                cachedShapedFont = resolved
                            }
                        }
                        val forceMissingGlyphFont = if (
                            shapedGlyph.sourceCodepoint == REPLACEMENT_CODEPOINT ||
                            isShapedGlyphMissingInFont(shapedFont, shapedGlyph)
                        ) {
                            missingGlyphFont ?: runtimeFallbackFont ?: shapedFont
                        } else {
                            null
                        }

                        var glyphFont = forceMissingGlyphFont ?: shapedFont
                        var glyph = if (forceMissingGlyphFont != null) {
                            preferredMissingGlyph(glyphFont)
                        } else {
                            resolveGlyphForShapedInput(shapedFont, shapedGlyph)
                        }
                        if (glyph == null && runtimeFallbackFont != null) {
                            val fallbackByCodepoint = resolveGlyphForShapedInput(runtimeFallbackFont, shapedGlyph)
                            if (fallbackByCodepoint != null) {
                                glyphFont = runtimeFallbackFont
                                glyph = fallbackByCodepoint
                            } else {
                                preferredMissingGlyph(runtimeFallbackFont)?.let { fallbackDefault ->
                                    glyphFont = runtimeFallbackFont
                                    glyph = fallbackDefault
                                }
                            }
                        }
                        debugCounters.glyphResolutionRequests += 1

                        val styleBold = (activeStyleFlags and STYLE_FLAG_BOLD) != 0
                        val styleItalic = (activeStyleFlags and STYLE_FLAG_ITALIC) != 0
                        val styleUnderline = (activeStyleFlags and STYLE_FLAG_UNDERLINE) != 0
                        val styleStrikethrough = (activeStyleFlags and STYLE_FLAG_STRIKETHROUGH) != 0
                        val styleObfuscated = (activeStyleFlags and STYLE_FLAG_OBFUSCATED) != 0

                        val glyphAdvance = shapedGlyph.advance
                        val glyphStartX = command.x + shapedGlyph.x
                        val glyphEndX = glyphStartX + glyphAdvance
                        if (glyphStartX < lineStartX) lineStartX = glyphStartX
                        if (glyphEndX > lineEndX) lineEndX = glyphEndX

                        val resolvedGlyph = glyph
                        if (resolvedGlyph != null && resolvedGlyph.drawable) {
                            val texture = beginForFont(glyphFont)
                            if (texture == null) {
                                lastObfuscatedGlyphIndex = null
                                glyphIndexInLine += 1
                                globalGlyphIndex += 1
                                return@forEach
                            }
                            if (activeStyleColor != currentDrawColor) {
                                val r = ((activeStyleColor ushr 16) and 0xFF) / 255f
                                val g = ((activeStyleColor ushr 8) and 0xFF) / 255f
                                val b = (activeStyleColor and 0xFF) / 255f
                                val a = ((activeStyleColor ushr 24) and 0xFF) / 255f
                                GL11.glColor4f(r, g, b, a)
                                currentDrawColor = activeStyleColor
                            }

                            val drawGlyph =
                                if (styleObfuscated && ObfuscationTextSelector.shouldObfuscateCodepoint(shapedGlyph.sourceCodepoint)) {
                                    resolveObfuscatedGlyph(
                                        font = glyphFont,
                                        sourceKey = command.sourceKey ?: command.text,
                                        original = resolvedGlyph,
                                        lineIndex = lineIndex,
                                        glyphIndexInLine = glyphIndexInLine,
                                        avoidGlyphIndex = lastObfuscatedGlyphIndex
                                    )
                                } else {
                                    resolvedGlyph
                                }

                            val effectiveGlyph = drawGlyph ?: resolvedGlyph
                            val glyphScale = TextDecorationLayout.scalePx(fontSize, glyphFont.meta.metrics.emSize)
                            val renderState = glyphRenderState(
                                glyph = effectiveGlyph,
                                font = glyphFont,
                                fontScalePx = glyphScale,
                                deviceScale = deviceScale,
                                weight = activeStyle.weight
                            )
                            if (renderState.screenPxRange != activeScreenPxRange) {
                                ARBShaderObjects.glUniform1fARB(uniformScreenPxRange, renderState.screenPxRange)
                                activeScreenPxRange = renderState.screenPxRange
                            }
                            if (renderState.weightBiasPx != activeWeightBiasPx) {
                                ARBShaderObjects.glUniform1fARB(uniformWeightBiasPx, renderState.weightBiasPx)
                                activeWeightBiasPx = renderState.weightBiasPx
                            }
                            emitGlyphQuad(
                                glyph = effectiveGlyph,
                                baselineY = baselineY + shapedGlyph.y,
                                cursorX = glyphStartX,
                                atlasWidth = texture.width,
                                atlasHeight = texture.height,
                                fontScalePx = glyphScale,
                                italic = styleItalic,
                                italicSkewPx = glyphScale * 0.2f
                            )
                            lastObfuscatedGlyphIndex = if (styleObfuscated) effectiveGlyph.glyphIndex else null
                        } else {
                            lastObfuscatedGlyphIndex = null
                        }

                        if (glyphEndX > glyphStartX) {
                            if (styleUnderline) {
                                appendMergedDecorationQuad(
                                    decorationQuads,
                                    DecorationRenderQuad(
                                        kind = SEGMENT_UNDERLINE,
                                        startX = glyphStartX,
                                        endX = glyphEndX,
                                        y = lineMetrics.underlineY,
                                        thickness = lineMetrics.underlineThickness,
                                        color = activeStyleColor
                                    )
                                )
                            }
                            if (styleStrikethrough) {
                                appendMergedDecorationQuad(
                                    decorationQuads,
                                    DecorationRenderQuad(
                                        kind = SEGMENT_STRIKETHROUGH,
                                        startX = glyphStartX,
                                        endX = glyphEndX,
                                        y = lineMetrics.strikethroughY,
                                        thickness = lineMetrics.strikethroughThickness,
                                        color = activeStyleColor
                                    )
                                )
                            }
                        }

                        globalGlyphIndex += 1
                        glyphIndexInLine += 1
                    }

                    if (debugDecorationGuidesEnabled && lineEndX > lineStartX) {
                        appendMergedDecorationQuad(
                            decorationQuads,
                            DecorationRenderQuad(
                                kind = SEGMENT_DEBUG_BASELINE,
                                startX = lineStartX,
                                endX = lineEndX,
                                y = lineRecord.baselineY.coerceIn(lineRecord.lineTopY, lineRecord.lineTopY + lineRecord.lineHeightPx),
                                thickness = 1f,
                                color = 0x66FFAA00
                            )
                        )
                        appendMergedDecorationQuad(
                            decorationQuads,
                            DecorationRenderQuad(
                                kind = SEGMENT_DEBUG_UNDERLINE,
                                startX = lineStartX,
                                endX = lineEndX,
                                y = lineMetrics.underlineY,
                                thickness = lineMetrics.underlineThickness,
                                color = 0x6600FF00
                            )
                        )
                        appendMergedDecorationQuad(
                            decorationQuads,
                            DecorationRenderQuad(
                                kind = SEGMENT_DEBUG_STRIKE,
                                startX = lineStartX,
                                endX = lineEndX,
                                y = lineMetrics.strikethroughY,
                                thickness = lineMetrics.strikethroughThickness,
                                color = 0x66FF00FF
                            )
                        )
                    }

                    lineTop += lineHeight
                    lineIndex += 1
                }
            } finally {
                if (glBegun) {
                    GL11.glEnd()
                }
                ARBShaderObjects.glUseProgramObjectARB(0)
            }

            flushDecorationQuads(decorationQuads, debugDecorationGuidesEnabled)
            maybeLogPerformance()
        } finally {
            if (depthWasEnabled) {
                GL11.glEnable(GL11.GL_DEPTH_TEST)
            }
        }
    }

    private fun prepareText(command: RenderCommand.DrawText): PreparedText {
        if (command.textFormatting != TextFormatting.Minecraft) {
            return PreparedText(text = command.text, styleSpans = command.styleSpans)
        }
        if (command.styleSpans.isNotEmpty()) {
            return PreparedText(text = command.text, styleSpans = command.styleSpans)
        }

        val parsed = MinecraftFormattingParser.parse(command.text, TextFormatting.Minecraft)
        val spans = MinecraftFormattingParser.resolveStyleSpans(
            parsed = parsed,
            baseColor = command.baseStyle.color,
            baseFlags = TextStyleFlags(
                bold = command.baseStyle.weight == TextWeight.Bold,
                italic = command.baseStyle.italic,
                underline = command.baseStyle.decorations.underline,
                strikethrough = command.baseStyle.decorations.strikethrough,
                obfuscated = command.baseStyle.obfuscated
            )
        ).map { span ->
            TextStyleSpan(
                start = span.start,
                end = span.end,
                style = TextStyleOverride(
                    color = span.color,
                    weight = if (span.flags.bold) TextWeight.Bold else TextWeight.Normal,
                    italic = span.flags.italic,
                    decorations = TextDecorations(
                        underline = span.flags.underline,
                        strikethrough = span.flags.strikethrough
                    ),
                    obfuscated = span.flags.obfuscated
                )
            )
        }
        return PreparedText(text = parsed.plainText, styleSpans = spans)
    }

    private fun splitLines(text: String): List<LineSlice> {
        if (text.isEmpty()) return listOf(LineSlice(0, 0))
        val lines = ArrayList<LineSlice>(4)
        var start = 0
        var index = 0
        while (index < text.length) {
            if (text[index] == '\n') {
                lines += LineSlice(start = start, endExclusive = index)
                start = index + 1
            }
            index += 1
        }
        lines += LineSlice(start = start, endExclusive = text.length)
        return lines
    }

    private fun resolveGlyphForShapedInput(font: LoadedMsdfFont, shapedGlyph: ShapedGlyph): MsdfGlyph? {
        val sourceCodepoint = shapedGlyph.sourceCodepoint
        val canUseGlyphIndex = shapedGlyph.fontId == font.descriptor.fontId
        val fromIndex = if (canUseGlyphIndex) font.meta.glyphByIndex(shapedGlyph.glyphIndex) else null
        val indexLooksMissing = isMissingGlyphIndex(font, shapedGlyph.glyphIndex, fromIndex)
        val indexMatchesSource = if (!indexLooksMissing) {
            val fromIndexCodepoint = fromIndex?.codepoint
            fromIndex != null && (fromIndexCodepoint == null || fromIndexCodepoint == sourceCodepoint)
        } else {
            false
        }

        if (sourceCodepoint == REPLACEMENT_CODEPOINT) {
            return preferredMissingGlyph(font)
        }
        if (indexMatchesSource) return fromIndex

        val fromCodepoint = font.meta.glyph(sourceCodepoint)
        if (fromCodepoint != null) return fromCodepoint

        return preferredMissingGlyph(font)
    }

    private fun preferredMissingGlyph(font: LoadedMsdfFont): MsdfGlyph? {
        val meta = font.meta
        val replacementByCodepoint = meta.glyph(REPLACEMENT_CODEPOINT)
        if (replacementByCodepoint != null) return replacementByCodepoint
        val questionByCodepoint = meta.glyph('?'.code)
        if (questionByCodepoint != null) return questionByCodepoint

        val questionByIndex = font.preferredQuestionGlyphIndex?.let(meta::glyphByIndex)
        if (questionByIndex != null) return questionByIndex
        val replacementByIndex = font.preferredMissingGlyphIndex
            ?.takeIf { it != 0 }
            ?.let(meta::glyphByIndex)
        if (replacementByIndex != null) return replacementByIndex
        val notDef = meta.glyphByIndex(0)
        if (notDef != null) return notDef
        return meta.fallbackGlyph()
    }

    private fun isShapedGlyphMissingInFont(font: LoadedMsdfFont, shapedGlyph: ShapedGlyph): Boolean {
        if (TextStyleMetrics.isWhitespaceCodepoint(shapedGlyph.sourceCodepoint)) return false
        val fromIndex = font.meta.glyphByIndex(shapedGlyph.glyphIndex)
        return isMissingGlyphIndex(font, shapedGlyph.glyphIndex, fromIndex)
    }

    private fun isMissingGlyphIndex(font: LoadedMsdfFont, glyphIndex: Int, glyph: MsdfGlyph?): Boolean {
        if (glyph == null) return true
        val preferredMissingIndex = font.preferredMissingGlyphIndex
        if (preferredMissingIndex != null && glyphIndex == preferredMissingIndex) return true
        if (glyphIndex == 0 && (glyph.codepoint == null || glyph.codepoint == REPLACEMENT_CODEPOINT)) return true
        return false
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

    private fun getOrBuildLayoutCacheEntry(
        command: RenderCommand.DrawText,
        prepared: PreparedText,
        primaryFont: LoadedMsdfFont,
        fontSize: Int
    ): LayoutCacheEntry {
        val key = LayoutCacheKey(
            text = prepared.text,
            primaryFontId = primaryFont.descriptor.fontId,
            fontSize = fontSize,
            textFormatting = command.textFormatting,
            baseFlagsMask = baseFlagsMask(command.baseStyle),
            styleSpansHash = styleSpansFingerprint(prepared.styleSpans)
        )
        synchronized(layoutCache) {
            val cached = layoutCache[key]
            if (cached != null) {
                debugCounters.layoutCacheHits += 1
                return cached
            }
        }

        debugCounters.layoutCacheMisses += 1
        val slices = splitLines(prepared.text)
        val lines = ArrayList<CachedLineLayout>(slices.size)
        slices.forEach { slice ->
            debugCounters.glyphVectorRequests += 1
            val shaped = FontRegistry.shapeTextRange(
                text = prepared.text,
                startIndex = slice.start,
                endIndexExclusive = slice.endExclusive,
                fontId = command.fontId,
                fontSize = fontSize,
                formattingMode = command.textFormatting.name
            )
            lines += CachedLineLayout(start = slice.start, shaped = shaped)
        }

        val built = LayoutCacheEntry(lines = lines)
        synchronized(layoutCache) {
            layoutCache[key] = built
        }
        return built
    }

    private fun flushDecorationQuads(quads: List<DecorationRenderQuad>, includeDebug: Boolean) {
        if (quads.isEmpty()) return
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
            quads.forEach { quad ->
                val isDebug = quad.kind == SEGMENT_DEBUG_BASELINE ||
                    quad.kind == SEGMENT_DEBUG_UNDERLINE ||
                    quad.kind == SEGMENT_DEBUG_STRIKE
                if (isDebug && !includeDebug) return@forEach

                val color = quad.color
                val r = ((color ushr 16) and 0xFF) / 255f
                val g = ((color ushr 8) and 0xFF) / 255f
                val b = (color and 0xFF) / 255f
                val a = ((color ushr 24) and 0xFF) / 255f
                GL11.glColor4f(r, g, b, a)
                val y0 = quad.y
                val y1 = maxOf(y0 + 0.5f, y0 + quad.thickness)
                GL11.glVertex2f(quad.startX, y0)
                GL11.glVertex2f(quad.endX, y0)
                GL11.glVertex2f(quad.endX, y1)
                GL11.glVertex2f(quad.startX, y1)
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

    private fun baseFlagsMask(style: TextRenderStyle): Int {
        return flagsMask(
            bold = style.weight == TextWeight.Bold,
            italic = style.italic,
            underline = style.decorations.underline,
            strikethrough = style.decorations.strikethrough,
            obfuscated = style.obfuscated
        )
    }

    private fun flagsMask(
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        strikethrough: Boolean,
        obfuscated: Boolean
    ): Int {
        var mask = 0
        if (bold) mask = mask or STYLE_FLAG_BOLD
        if (italic) mask = mask or STYLE_FLAG_ITALIC
        if (underline) mask = mask or STYLE_FLAG_UNDERLINE
        if (strikethrough) mask = mask or STYLE_FLAG_STRIKETHROUGH
        if (obfuscated) mask = mask or STYLE_FLAG_OBFUSCATED
        return mask
    }

    private fun styleSpansFingerprint(spans: List<TextStyleSpan>): Int {
        if (spans.isEmpty()) return 0
        var hash = 1
        spans.forEach { span ->
            hash = 31 * hash + span.start
            hash = 31 * hash + span.end
            hash = 31 * hash + (span.style.color ?: Int.MIN_VALUE)
            hash = 31 * hash + when (span.style.weight) {
                null -> 0
                TextWeight.Normal -> 1
                TextWeight.Bold -> 2
            }
            hash = 31 * hash + when (span.style.italic) {
                null -> 0
                false -> 1
                true -> 2
            }
            hash = 31 * hash + (span.style.decorations?.hashCode() ?: 0)
            hash = 31 * hash + when (span.style.obfuscated) {
                null -> 0
                false -> 1
                true -> 2
            }
        }
        return hash
    }

    private fun resolvedStyle(baseStyle: TextRenderStyle, override: TextStyleOverride): TextRenderStyle {
        return TextRenderStyle(
            color = override.color ?: baseStyle.color,
            weight = override.weight ?: baseStyle.weight,
            italic = override.italic ?: baseStyle.italic,
            decorations = override.decorations ?: baseStyle.decorations,
            obfuscated = override.obfuscated ?: baseStyle.obfuscated
        )
    }

    private fun resolveObfuscatedGlyph(
        font: LoadedMsdfFont,
        sourceKey: String,
        original: MsdfGlyph,
        lineIndex: Int,
        glyphIndexInLine: Int,
        avoidGlyphIndex: Int?
    ): MsdfGlyph? {
        val buckets = obfuscationBuckets.getOrPut(font.descriptor.fontId) {
            buildObfuscationBuckets(font)
        }
        if (buckets.allGlyphs.isEmpty()) return original
        val baseBucket = advanceBucketKey(original.advance)
        val candidates = buckets.expandedByAdvanceBucket[baseBucket]
            ?: nearestExpandedCandidates(buckets, baseBucket)
            ?: buckets.allGlyphs
        if (candidates.isEmpty()) return original

        val originalKey = original.codepoint ?: original.glyphIndex
        val primaryIndex = ObfuscationTextSelector.selectCandidateIndex(
            sourceKey = sourceKey,
            lineIndex = lineIndex,
            glyphIndexInLine = glyphIndexInLine,
            timeSlice = obfuscationTimeSlice,
            originalCodepoint = originalKey,
            candidateCount = candidates.size
        )
        val primary = candidates[primaryIndex]
        if (avoidGlyphIndex == null || candidates.size <= 1 || primary.glyphIndex != avoidGlyphIndex) {
            return primary
        }
        val secondaryIndex = (primaryIndex + 1 + (obfuscationTimeSlice.toInt() and 3)) % candidates.size
        val secondary = candidates[secondaryIndex]
        if (secondary.glyphIndex != avoidGlyphIndex) return secondary
        return candidates.firstOrNull { it.glyphIndex != avoidGlyphIndex } ?: primary
    }

    private fun buildObfuscationBuckets(font: LoadedMsdfFont): ObfuscationBuckets {
        val glyphs = font.meta.glyphsByIndex.values
            .filter { glyph ->
                val codepoint = glyph.codepoint
                glyph.drawable && (codepoint == null || !TextStyleMetrics.isWhitespaceCodepoint(codepoint))
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

    private fun textureFor(font: LoadedMsdfFont): FontTextureHandle? {
        val fontId = font.descriptor.fontId
        textures[fontId]?.let { return it }
        font.handle?.let { return it }

        return runCatching {
            val handle = uploadTexture(font)
            textures[fontId] = handle
            handle
        }.onSuccess {
            font.handle = it
            font.atlasPayload.markLoadedToGPUTexture()
        }.onFailure { error ->
            logRateLimited("texture:$fontId", "[DSGL-MSDF] Failed to load atlas '$fontId': ${error.message}")
        }.getOrNull()
    }

    private fun uploadTexture(font: LoadedMsdfFont): FontTextureHandle {
        val bitmap = font.atlasPayload.ensureDecoded()
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)
        if (width > maxTextureSize || height > maxTextureSize) {
            throw IllegalStateException(
                "Atlas '${font.descriptor.fontId}' is ${width}x${height}, exceeds GL_MAX_TEXTURE_SIZE=$maxTextureSize"
            )
        }
        val buffer = BufferUtils.createByteBuffer(width * height * 4)
        buffer.put(ByteBuffer.wrap(bitmap.rgbaBytes))
        buffer.flip()

        val textureId = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP)
        val glError = scopedGlError {
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
        }
        if (glError != GL11.GL_NO_ERROR) {
            GL11.glDeleteTextures(textureId)
            throw IllegalStateException(
                "glTexImage2D failed for '${font.descriptor.fontId}' (${width}x${height}), glError=0x${
                    glError.toString(16)
                }"
            )
        }
        debugCounters.textureUploads += 1
        debugCounters.textureUploadBytes += width.toLong() * height.toLong() * 4L
        return FontTextureHandle(textureId = textureId, width = width, height = height)
    }

    private fun drainGlErrors(): Int {
        var firstError = GL11.GL_NO_ERROR
        while (true) {
            val error = GL11.glGetError()
            if (error == GL11.GL_NO_ERROR) break
            if (firstError == GL11.GL_NO_ERROR) {
                firstError = error
            }
        }
        return firstError
    }

    private inline fun scopedGlError(block: () -> Unit): Int {
        drainGlErrors()
        block()
        return drainGlErrors()
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
            uniformScreenPxRange = ARBShaderObjects.glGetUniformLocationARB(programId, "uScreenPxRange")
            uniformWeightBiasPx = ARBShaderObjects.glGetUniformLocationARB(programId, "uWeightBiasPx")
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
        val alpha = (color ushr 24) and 0xFF
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

    private fun maybeLogPerformance() {
        if (!debugPerformanceEnabled) return
        val now = System.currentTimeMillis()
        if (now - debugLastLogMs < 1_000L) return
        debugLastLogMs = now
        val layoutSize = synchronized(layoutCache) { layoutCache.size }
        println(
            "[DSGL-MSDF] drawCalls=${debugCounters.drawCalls} " +
                "layoutCache hit=${debugCounters.layoutCacheHits} miss=${debugCounters.layoutCacheMisses} size=$layoutSize " +
                "glyphVectors=${debugCounters.glyphVectorRequests} glyphResolves=${debugCounters.glyphResolutionRequests} " +
                "textureUploads=${debugCounters.textureUploads} " +
                "textureUploadBytes=${debugCounters.textureUploadBytes}"
        )
    }

    private fun debugGlyphResolution(text: String, font: LoadedMsdfFont) {
        if (!debugGlyphResolutionEnabled) return
        val sample = text.take(64)
        val key = "${font.descriptor.fontId}|$sample"
        if (!debugLogKeys.add(key)) return

        val shaped = FontRegistry.shapeText(
            text = sample,
            fontId = font.descriptor.fontId,
            fontSize = FontRegistry.DEFAULT_FONT_SIZE,
            formattingMode = "debug"
        )
        println("[DSGL-MSDF] text='$sample' glyphs=${shaped.glyphs.size} runs=${shaped.runs.size}")
        shaped.glyphs.take(32).forEach { glyph ->
            val loaded = FontRegistry.get(glyph.fontId)
            val atlasGlyph = loaded?.meta?.glyphByIndex(glyph.glyphIndex)
            println(
                "[DSGL-MSDF] font=${glyph.fontId} glyphIndex=${glyph.glyphIndex} sourceCp=U+%04X found=%s".format(
                    glyph.sourceCodepoint,
                    atlasGlyph != null
                )
            )
        }
    }

    companion object {
        private const val MAX_LAYOUT_CACHE_ENTRIES: Int = 512
        private const val MIN_OBFUSCATION_CANDIDATES: Int = 24
        private const val OBFUSCATION_TIME_STEP_SEC: Double = 0.05
        private const val STYLE_FLAG_BOLD: Int = 1 shl 0
        private const val STYLE_FLAG_ITALIC: Int = 1 shl 1
        private const val STYLE_FLAG_UNDERLINE: Int = 1 shl 2
        private const val STYLE_FLAG_STRIKETHROUGH: Int = 1 shl 3
        private const val STYLE_FLAG_OBFUSCATED: Int = 1 shl 4
        private const val SEGMENT_UNDERLINE: Int = 1
        private const val SEGMENT_STRIKETHROUGH: Int = 2
        private const val SEGMENT_DEBUG_BASELINE: Int = 3
        private const val SEGMENT_DEBUG_UNDERLINE: Int = 4
        private const val SEGMENT_DEBUG_STRIKE: Int = 5
        private const val REPLACEMENT_CODEPOINT: Int = 0xFFFD

        internal fun explicitScreenPxRange(
            glyph: MsdfGlyph,
            fontScalePx: Float,
            deviceScale: Float,
            atlasDistanceRange: Float
        ): Float {
            val plane = glyph.planeBounds ?: return 1f
            val atlas = glyph.atlasBounds ?: return 1f
            val glyphScreenWidthPx = kotlin.math.abs((plane.right - plane.left) * fontScalePx * deviceScale)
            val glyphScreenHeightPx = kotlin.math.abs((plane.top - plane.bottom) * fontScalePx * deviceScale)
            val glyphAtlasWidthPx = kotlin.math.abs(atlas.right - atlas.left)
            val glyphAtlasHeightPx = kotlin.math.abs(atlas.top - atlas.bottom)
            if (glyphAtlasWidthPx <= 0.0001f || glyphAtlasHeightPx <= 0.0001f) return 1f

            val rangeX = atlasDistanceRange * (glyphScreenWidthPx / glyphAtlasWidthPx)
            val rangeY = atlasDistanceRange * (glyphScreenHeightPx / glyphAtlasHeightPx)
            return (0.5f * (rangeX + rangeY)).coerceAtLeast(1f)
        }

        internal fun weightBiasPx(weight: TextWeight, screenPxRange: Float): Float {
            if (weight != TextWeight.Bold) return 0f
            return (0.18f + 0.035f * screenPxRange).coerceIn(0.18f, 0.42f)
        }

        internal fun appendMergedDecorationQuad(
            out: MutableList<DecorationRenderQuad>,
            quad: DecorationRenderQuad
        ) {
            if (quad.endX <= quad.startX) return
            val last = out.lastOrNull()
            if (last != null &&
                last.kind == quad.kind &&
                last.color == quad.color &&
                kotlin.math.abs(last.endX - quad.startX) <= 0.51f &&
                kotlin.math.abs(last.y - quad.y) <= 0.51f &&
                kotlin.math.abs(last.thickness - quad.thickness) <= 0.1f
            ) {
                out[out.lastIndex] = last.copy(endX = quad.endX)
                return
            }
            out += quad
        }

        private fun glyphRenderState(
            glyph: MsdfGlyph,
            font: LoadedMsdfFont,
            fontScalePx: Float,
            deviceScale: Float,
            weight: TextWeight
        ): GlyphRenderState {
            val screenPxRange = explicitScreenPxRange(
                glyph = glyph,
                fontScalePx = fontScalePx,
                deviceScale = deviceScale.coerceAtLeast(1f),
                atlasDistanceRange = font.meta.atlas.distanceRange
            )
            return GlyphRenderState(
                screenPxRange = screenPxRange,
                weightBiasPx = weightBiasPx(weight, screenPxRange)
            )
        }

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
            uniform float uScreenPxRange;
            uniform float uWeightBiasPx;
            
            varying vec2 vTexCoord;
            varying vec4 vColor;
            
            float median3(float a, float b, float c) {
                return max(min(a, b), min(max(a, b), c));
            }
            
            void main() {
                vec4 atlas = texture2D(uAtlas, vTexCoord);
                float pxRange = max(uScreenPxRange, 1.0);
                float sdMsdf = median3(atlas.r, atlas.g, atlas.b);
                float sdSdf = atlas.a;
            
                float msdfPxDist = pxRange * (sdMsdf - 0.5) + uWeightBiasPx;
                float sdfPxDist = pxRange * (sdSdf - 0.5) + uWeightBiasPx;
            
                float alphaMsdf = clamp(msdfPxDist + 0.5, 0.0, 1.0);
                float alphaSdf = clamp(sdfPxDist + 0.5, 0.0, 1.0);
                float useSdfFallback = step(pxRange, 1.35);
                float alpha = mix(alphaMsdf, alphaSdf, useSdfFallback);
            
                gl_FragColor = vec4(vColor.rgb, vColor.a * alpha);
            }
        """
    }
}
