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

        val glyphs = parsed.glyphs.associateTo(linkedMapOf()) { glyph ->
            glyph.codepoint to MsdfGlyph(
                codepoint = glyph.codepoint,
                advance = glyph.advance,
                planeBounds = glyph.planeBounds?.toRuntime(),
                atlasBounds = glyph.atlasBounds?.toRuntime()
            )
        }

        val kerning = parsed.kerning.associateTo(linkedMapOf()) { pair ->
            MsdfFontMeta.kerningKey(pair.leftCodepoint, pair.rightCodepoint) to pair.advance
        }

        val replacementCodepoint = when {
            glyphs.containsKey('?'.code) -> '?'.code
            glyphs.containsKey(0xFFFD) -> 0xFFFD
            else -> glyphs.keys.firstOrNull()
        }

        return MsdfFontMeta(
            atlas = atlas,
            metrics = metrics,
            glyphsByCodepoint = glyphs,
            kerningPairs = kerning,
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
