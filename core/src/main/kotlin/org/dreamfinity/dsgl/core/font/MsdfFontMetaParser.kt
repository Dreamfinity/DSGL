package org.dreamfinity.dsgl.core.font

import kotlinx.serialization.json.Json

object MsdfFontMetaParser {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(rawJson: String): MsdfFontMeta {
        val parsed = json.decodeFromString<MsdfMetaJson>(rawJson)
        validate(parsed)

        val atlas = MsdfAtlasInfo(
            type = parsed.atlas.type,
            distanceRange = parsed.atlas.distanceRange,
            size = parsed.atlas.size,
            width = parsed.atlas.width,
            height = parsed.atlas.height,
            yOrigin = parsed.atlas.yOrigin
        )

        val metrics = MsdfMetrics(
            emSize = parsed.metrics.emSize,
            lineHeight = parsed.metrics.lineHeight,
            ascender = parsed.metrics.ascender,
            descender = parsed.metrics.descender,
            underlineY = parsed.metrics.underlineY,
            underlineThickness = parsed.metrics.underlineThickness
        )

        val anyExplicitGlyphIndex = parsed.glyphs.any { it.glyphIndex >= 0 }
        val glyphsByIndex = linkedMapOf<Int, MsdfGlyph>()
        val glyphsByCodepoint = linkedMapOf<Int, MsdfGlyph>()

        parsed.glyphs.forEachIndexed { listIndex, glyph ->
            val resolvedGlyphIndex = when {
                glyph.glyphIndex >= 0 -> glyph.glyphIndex
                !anyExplicitGlyphIndex -> listIndex
                else -> throw IllegalArgumentException(
                    "Glyph metadata has mixed explicit/implicit glyph indices. " +
                        "Provide a stable glyph index field for every glyph entry."
                )
            }

            val runtimeGlyph = MsdfGlyph(
                glyphIndex = resolvedGlyphIndex,
                codepoint = glyph.unicodeCodepoint,
                advance = glyph.advance,
                planeBounds = glyph.planeBounds?.toRuntime(),
                atlasBounds = glyph.atlasBounds?.toRuntime()
            )

            val previous = glyphsByIndex.putIfAbsent(resolvedGlyphIndex, runtimeGlyph)
            if (previous != null) {
                throw IllegalArgumentException("Duplicate glyph index $resolvedGlyphIndex in metadata")
            }

            val codepoint = runtimeGlyph.codepoint
            if (codepoint != null && !glyphsByCodepoint.containsKey(codepoint)) {
                glyphsByCodepoint[codepoint] = runtimeGlyph
            }
        }

        val kerningByIndex = linkedMapOf<Long, Float>()
        val kerningByCodepoint = linkedMapOf<Long, Float>()
        parsed.kerning.forEach { pair ->
            kerningByIndex[MsdfFontMeta.kerningKey(pair.leftGlyphIndex, pair.rightGlyphIndex)] = pair.advance

            val leftFromIndex = glyphsByIndex[pair.leftGlyphIndex]?.codepoint
            val rightFromIndex = glyphsByIndex[pair.rightGlyphIndex]?.codepoint
            val leftCodepoint = leftFromIndex ?: pair.leftGlyphIndex.takeIf { glyphsByCodepoint.containsKey(it) }
            val rightCodepoint = rightFromIndex ?: pair.rightGlyphIndex.takeIf { glyphsByCodepoint.containsKey(it) }
            if (leftCodepoint != null && rightCodepoint != null) {
                kerningByCodepoint[MsdfFontMeta.kerningKey(leftCodepoint, rightCodepoint)] = pair.advance
            }
        }

        val replacementCodepoint = when {
            glyphsByCodepoint.containsKey(0xFFFD) -> 0xFFFD
            glyphsByCodepoint.containsKey('?'.code) -> '?'.code
            else -> glyphsByCodepoint.keys.firstOrNull()
        }
        val replacementGlyphIndex = when {
            replacementCodepoint != null -> glyphsByCodepoint[replacementCodepoint]?.glyphIndex
            glyphsByIndex.containsKey(0) -> 0
            else -> glyphsByIndex.keys.firstOrNull()
        }

        return MsdfFontMeta(
            atlas = atlas,
            metrics = metrics,
            glyphsByIndex = glyphsByIndex,
            glyphsByCodepoint = glyphsByCodepoint,
            kerningPairsByIndex = kerningByIndex,
            kerningPairsByCodepoint = kerningByCodepoint,
            replacementGlyphIndex = replacementGlyphIndex,
            replacementCodepoint = replacementCodepoint
        )
    }

    private fun validate(meta: MsdfMetaJson) {
        if (meta.atlas.width <= 0 || meta.atlas.height <= 0) {
            throw IllegalArgumentException("atlas.width and atlas.height must be > 0")
        }
        if (meta.metrics.lineHeight <= 0f) {
            throw IllegalArgumentException("metrics.lineHeight must be > 0")
        }
        if (meta.glyphs.isEmpty()) {
            throw IllegalArgumentException("glyphs list must not be empty")
        }
    }

    private fun MsdfPlaneBoundsJson.toRuntime(): MsdfPlaneBounds {
        return MsdfPlaneBounds(
            left = left,
            bottom = bottom,
            right = right,
            top = top
        )
    }

    private fun MsdfAtlasBoundsJson.toRuntime(): MsdfAtlasBounds {
        return MsdfAtlasBounds(
            left = left,
            bottom = bottom,
            right = right,
            top = top
        )
    }
}

