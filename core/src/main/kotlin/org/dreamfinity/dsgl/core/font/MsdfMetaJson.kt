package org.dreamfinity.dsgl.core.font

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class MsdfMetaJson(
    val atlas: MsdfAtlasJson = MsdfAtlasJson(),
    val metrics: MsdfMetricsJson = MsdfMetricsJson(),
    val glyphs: List<MsdfGlyphJson> = emptyList(),
    val kerning: List<MsdfKerningJson> = emptyList()
)

@Serializable
internal data class MsdfAtlasJson(
    val type: String = "mtsdf",
    @Serializable(with = NumberAsFloatSerializer::class)
    val distanceRange: Float = 0f,
    @Serializable(with = NumberAsFloatSerializer::class)
    val size: Float = 0f,
    @Serializable(with = NumberAsIntSerializer::class)
    val width: Int = 0,
    @Serializable(with = NumberAsIntSerializer::class)
    val height: Int = 0,
    val yOrigin: String = "bottom"
)

@Serializable
internal data class MsdfMetricsJson(
    @Serializable(with = NumberAsFloatSerializer::class)
    val emSize: Float = 1f,
    @Serializable(with = NumberAsFloatSerializer::class)
    val lineHeight: Float = 1f,
    @Serializable(with = NumberAsFloatSerializer::class)
    val ascender: Float = 0f,
    @Serializable(with = NumberAsFloatSerializer::class)
    val descender: Float = 0f,
    val underlineY: Float? = null,
    val underlineThickness: Float? = null
)

@Serializable(with = MsdfGlyphJsonSerializer::class)
internal data class MsdfGlyphJson(
    val glyphIndex: Int = -1,
    val unicodeCodepoint: Int? = null,
    @Serializable(with = NumberAsFloatSerializer::class)
    val advance: Float = 0f,
    val planeBounds: MsdfPlaneBoundsJson? = null,
    val atlasBounds: MsdfAtlasBoundsJson? = null
)

@Serializable
internal data class MsdfPlaneBoundsJson(
    @Serializable(with = NumberAsFloatSerializer::class)
    val left: Float = 0f,
    @Serializable(with = NumberAsFloatSerializer::class)
    val bottom: Float = 0f,
    @Serializable(with = NumberAsFloatSerializer::class)
    val right: Float = 0f,
    @Serializable(with = NumberAsFloatSerializer::class)
    val top: Float = 0f
)

@Serializable
internal data class MsdfAtlasBoundsJson(
    @Serializable(with = NumberAsFloatSerializer::class)
    val left: Float = 0f,
    @Serializable(with = NumberAsFloatSerializer::class)
    val bottom: Float = 0f,
    @Serializable(with = NumberAsFloatSerializer::class)
    val right: Float = 0f,
    @Serializable(with = NumberAsFloatSerializer::class)
    val top: Float = 0f
)

@Serializable
internal data class MsdfKerningJson(
    @SerialName("index1")
    @Serializable(with = NumberAsIntSerializer::class)
    val leftGlyphIndex: Int = 0,
    @SerialName("index2")
    @Serializable(with = NumberAsIntSerializer::class)
    val rightGlyphIndex: Int = 0,
    @Serializable(with = NumberAsFloatSerializer::class)
    val advance: Float = 0f
)

internal object NumberAsFloatSerializer : kotlinx.serialization.KSerializer<Float> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NumberAsFloat", PrimitiveKind.FLOAT)

    override fun deserialize(decoder: Decoder): Float {
        val primitive = (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive
            ?: return decoder.decodeFloat()
        return primitive.toFloatStrict()
    }

    override fun serialize(encoder: Encoder, value: Float) {
        encoder.encodeFloat(value)
    }
}

internal object NumberAsIntSerializer : kotlinx.serialization.KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NumberAsInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val primitive = (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive
            ?: return decoder.decodeInt()
        return primitive.toIntStrict()
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}

private fun JsonPrimitive.toFloatStrict(): Float {
    val direct = floatOrNull()
    if (direct != null) return direct
    val fallback = content.toFloatOrNull()
    if (fallback != null) return fallback
    throw IllegalArgumentException("Expected numeric float, got '$content'")
}

internal object MsdfGlyphJsonSerializer : kotlinx.serialization.KSerializer<MsdfGlyphJson> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MsdfGlyphJson") {
        element<Int>("glyphIndex", isOptional = true)
        element<Int>("unicodeCodepoint", isOptional = true)
        element<Float>("advance", isOptional = true)
        element<MsdfPlaneBoundsJson?>("planeBounds", isOptional = true)
        element<MsdfAtlasBoundsJson?>("atlasBounds", isOptional = true)
    }

    override fun deserialize(decoder: Decoder): MsdfGlyphJson {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalArgumentException("MsdfGlyphJsonSerializer requires JsonDecoder")
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val glyphIndex = resolveGlyphIndex(obj)
        val unicodeCodepoint = resolveUnicodeCodepoint(obj)
        val advance = obj["advance"]?.jsonPrimitive?.toFloatStrict() ?: 0f
        val plane = obj["planeBounds"]?.let {
            jsonDecoder.json.decodeFromJsonElement(MsdfPlaneBoundsJson.serializer(), it)
        }
        val atlas = obj["atlasBounds"]?.let {
            jsonDecoder.json.decodeFromJsonElement(MsdfAtlasBoundsJson.serializer(), it)
        }
        return MsdfGlyphJson(
            glyphIndex = glyphIndex,
            unicodeCodepoint = unicodeCodepoint,
            advance = advance,
            planeBounds = plane,
            atlasBounds = atlas
        )
    }

    override fun serialize(encoder: Encoder, value: MsdfGlyphJson) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
            ?: throw IllegalArgumentException("MsdfGlyphJsonSerializer requires JsonEncoder")
        val map = linkedMapOf<String, JsonElement>()
        if (value.glyphIndex >= 0) {
            map["index"] = JsonPrimitive(value.glyphIndex)
        }
        value.unicodeCodepoint?.let { cp ->
            map["unicode"] = JsonPrimitive(cp)
        }
        map["advance"] = JsonPrimitive(value.advance)
        value.planeBounds?.let {
            map["planeBounds"] = jsonEncoder.json.encodeToJsonElement(MsdfPlaneBoundsJson.serializer(), it)
        }
        value.atlasBounds?.let {
            map["atlasBounds"] = jsonEncoder.json.encodeToJsonElement(MsdfAtlasBoundsJson.serializer(), it)
        }
        jsonEncoder.encodeJsonElement(JsonObject(map))
    }

    private fun resolveGlyphIndex(obj: JsonObject): Int {
        val numericKeys = listOf("glyphIndex", "index", "id")
        numericKeys.forEach { key ->
            val value = obj[key]?.jsonPrimitive ?: return@forEach
            return value.toIntStrict()
        }
        return -1
    }

    private fun resolveUnicodeCodepoint(obj: JsonObject): Int? {
        val numericKeys = listOf("unicode", "codepoint")
        numericKeys.forEach { key ->
            val value = obj[key]?.jsonPrimitive ?: return@forEach
            return value.toIntStrict()
        }

        val stringKeys = listOf("char", "character", "glyph")
        stringKeys.forEach { key ->
            val value = obj[key]?.jsonPrimitive?.content ?: return@forEach
            if (value.isEmpty()) return@forEach
            return Character.codePointAt(value, 0)
        }

        return null
    }
}

private fun JsonPrimitive.toIntStrict(): Int {
    val intValue = intOrNull
    if (intValue != null) return intValue
    val doubleValue = doubleOrNull ?: content.toDoubleOrNull()
    if (doubleValue != null) return doubleValue.toInt()
    throw IllegalArgumentException("Expected numeric int, got '$content'")
}

private fun JsonPrimitive.floatOrNull(): Float? {
    val direct = content.toFloatOrNull()
    if (direct != null) return direct
    return doubleOrNull?.toFloat()
}

