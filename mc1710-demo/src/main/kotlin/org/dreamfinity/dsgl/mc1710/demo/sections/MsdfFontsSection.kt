package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.font.FontAssetSource
import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.font.RegisteredFontInfo
import org.dreamfinity.dsgl.core.style.TextFormatting
import org.dreamfinity.dsgl.core.style.TextWrap
import org.dreamfinity.dsgl.mc1710.text.MsdfRuntimeDebugSettings
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private val FALLBACK_FONT_IDS = listOf("minecraft", "ubuntu")
private val COLOR_PRESETS = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFFC857.toInt(),
    0xFF8EE3F5.toInt(),
    0xFFFF7E67.toInt()
)

private const val SAMPLE_PARAGRAPH =
    "MSDF/MTSDF text rendering demo in DSGL. This paragraph should wrap cleanly in a fixed-width panel and respect font switches, opacity, and size."
private const val SAMPLE_WORD = "\u4ED6\u65B9\u3001\u6210\u7E3E\u8A55long_unbroken_word_to_force_hard_break_ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789"
private const val SAMPLE_SPACES_A = "Hello   world"
private const val LONG_CH_SENTENCE =
    "\u592A\u9633\u6652\u5F97\u58A8\u9ED1\u7684\u6E05\u7626\u7684\u8138\u4E0A\uFF0C\u6709\u4E00\u5BF9\u7A0D\u7A0D\u6D3C\u8FDB\u53BB\u7684\u5927\u5927\u7684\u53CC\u773C\u76AE\u513F\u773C\u775B\uFF0C\u7709\u6BDB\u7EC6\u800C\u659C\uFF0C\u9ED1\u91CC\u5E26\u9EC4\u7684\u5934\u53D1\u7528\u82B1\u5E03\u6761\u5B50\u624E\u4E24\u6761\u77ED\u8FAB\u5B50\uFF0C\u8863\u670D\u90FD\u5F88\u65E7\uFF0C\u53F3\u88E4\u811A\u4E0A\u7684\u4E00\u4E2A\u7834\u6D1E\u522B\u4E00\u652F\u522B\u9488\uFF0C\u6625\u590F\u79CB\u4E09\u5B63\u90FD\u6253\u8D64\u811A\uFF0C\u53EA\u6709\u4E0A\u5C71\u6293\u67F4\u79BE\u7684\u65F6\u8282\uFF0C\u6015\u523A\u7834\u811A\u677F\uFF0C\u624D\u7A7F\u53CC\u978B\u5B50\uFF0C\u4F46\u4E00\u4E0B\u5C71\u5C31\u8131\u4E86\u3002"
private const val LONG_JP_SENTENCE =
    "\u4ED6\u65B9\u3001\u6210\u7E3E\u8A55\u4FA1\u306E\u7518\u3044\u6388\u696D\u304C\u9AD8\u304F\u8A55\u4FA1\u3055\u308C\u305F\u308A\u3001\u4EBA\u6C17\u53D6\u308A\u306B\u8D70\u308B\u6559\u5E2B\u304C\u51FA\u305F\u308A\u3057\u3001\u6210\u7E3E\u306E\u5B89\u58F2\u308A\u3084\u5927\u5B66\u6559\u5E2B\u306E\u30EC\u30D9\u30EB\u30C0\u30A6\u30F3\u3068\u3044\u3046\u5F0A\u5BB3\u3092\u3082\u305F\u3089\u3059\u6050\u308C\u304C\u3042\u308B\u3001\u306A\u3069\u306E\u53CD\u7701\u610F\u898B\u3082\u3042\u308B."
private const val LONG_KR_SENTENCE = "\uC800\uB294 \uC624\uB298 \uC544\uCE68\uC5D0 \uCE74\uD398\uC5D0\uC11C \uCE5C\uAD6C\uB791 \uD55C\uAD6D\uC5B4 \uACF5\uBD80\uB97C \uD558\uACE0 \uB098\uC11C \uB3C4\uC11C\uAD00\uC5D0 \uAC08 \uAC70\uC608\uC694."

private const val SAMPLE_SPACES_B = "A A  A"
private const val SAMPLE_MC_COLORS = "\u00A7aGreen \u00A7bBlue \u00A7cRed \u00A7rBackToDefault"
private const val SAMPLE_MC_FLAGS =
    "\u00A7lBold \u00A7oItalic \u00A7nUnderline \u00A7mStrike \u00A7kMagic\u00A7r Normal"

fun UiScope.renderMsdfFontsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val registeredFonts = FontRegistry.registeredFonts()
    val selectableFonts = registeredFonts.ifEmpty {
        FALLBACK_FONT_IDS.map { fontId ->
            RegisteredFontInfo(
                fontId = fontId,
                source = FontAssetSource.Jar,
                metaPath = "n/a",
                atlasPath = "n/a",
                ttfPath = null
            )
        }
    }
    val minWrapWidth = 120
    val maxWrapWidth = (contentWidth - 8).coerceAtLeast(minWrapWidth)
    val panelWidth = window.msdfWrapWidth.toInt().coerceIn(minWrapWidth, maxWrapWidth)
    val opacity = (window.msdfOpacityPercent.toFloat() / 100f).coerceIn(0f, 1f)
    val fontSize = window.msdfFontSizePx.toInt().coerceIn(6, 48)
    val selectedFontIndex = window.msdfFontIndex.coerceIn(0, (selectableFonts.size - 1).coerceAtLeast(0))
    val selectedFont = selectableFonts[selectedFontIndex]
    val fontId = selectedFont.fontId
    val textColor = COLOR_PRESETS[window.msdfColorIndex.coerceIn(0, COLOR_PRESETS.lastIndex)]
    val formattingMode = if (window.msdfParseMinecraftFormatting) TextFormatting.Minecraft else TextFormatting.None

    div(
        ComponentProps(
            key = "section.msdfFonts",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("MSDF Fonts"))
        text(
            TextProps(
                "All DSGL DrawText commands go through MSDF/MTSDF rendering. Switch font/size/color/opacity and verify wrapping."
            ).apply {
                this.color = DEMO_MUTED
            }
        )
        text(TextProps("DREAMFINITY").apply { style = { fontId("telegrafico") } })

        div(ComponentProps(gap = 4).asFlexRow()) {
            button(
                ButtonProps("Font: $fontId").apply {
                    width = 82
                    onMouseClick = {
                        window.msdfFontIndex = (window.msdfFontIndex + 1) % selectableFonts.size.coerceAtLeast(1)
                    }
                }
            )
            button(
                ButtonProps("Color").apply {
                    width = 58
                    onMouseClick = {
                        window.msdfColorIndex = (window.msdfColorIndex + 1) % COLOR_PRESETS.size
                    }
                }
            )
            button(
                ButtonProps(
                    if (window.msdfParseMinecraftFormatting) {
                        "Formatting ON"
                    } else {
                        "Formatting OFF"
                    }
                ).apply {
                    width = 104
                    onMouseClick = {
                        window.msdfParseMinecraftFormatting = !window.msdfParseMinecraftFormatting
                        window.appendInfo("MSDF formatting=${window.msdfParseMinecraftFormatting}")
                    }
                }
            )
            button(
                ButtonProps(
                    if (window.msdfShowBaselineGuides) {
                        "Guides ON"
                    } else {
                        "Guides OFF"
                    }
                ).apply {
                    width = 84
                    onMouseClick = {
                        window.msdfShowBaselineGuides = !window.msdfShowBaselineGuides
                        MsdfRuntimeDebugSettings.decorationGuidesEnabled = window.msdfShowBaselineGuides
                        System.setProperty(
                            "dsgl.msdf.debug.decorations",
                            window.msdfShowBaselineGuides.toString()
                        )
                        window.appendInfo("MSDF guides=${window.msdfShowBaselineGuides}")
                    }
                }
            )
        }

        input(
            InputProps(
                InputType.Range(
                    value = window.msdfOpacityPercent,
                    min = 0,
                    max = 100,
                    step = 1
                )
            ).apply {
                key = "msdf.opacity"
                width = contentWidth - 8
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: window.msdfOpacityPercent
                    window.msdfOpacityPercent = next.coerceIn(0, 100)
                }
            }
        )
        input(
            InputProps(
                InputType.Range(
                    value = window.msdfFontSizePx,
                    min = 6,
                    max = 48,
                    step = 1
                )
            ).apply {
                key = "msdf.fontSize"
                width = contentWidth - 8
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: window.msdfFontSizePx
                    window.msdfFontSizePx = next.coerceIn(6, 48)
                }
            }
        )
        input(
            InputProps(
                InputType.Range(
                    value = panelWidth.toLong(),
                    min = minWrapWidth.toLong(),
                    max = maxWrapWidth.toLong(),
                    step = 2
                )
            ).apply {
                key = "msdf.wrapWidth"
                width = contentWidth - 8
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: panelWidth.toLong()
                    window.msdfWrapWidth = next.coerceIn(minWrapWidth.toLong(), maxWrapWidth.toLong())
                }
            }
        )

        text(
            TextProps {
                "fontId=$fontId source=${selectedFont.source.name.lowercase()} fontSize=$fontSize opacity=$opacity panelWidth=$panelWidth formatting=${formattingMode.name.lowercase()} guides=${window.msdfShowBaselineGuides}"
            }.apply { this.color = DEMO_MUTED }
        )
        text(
            TextProps("Drop external font packages into <gameDir>/dsgl/fonts/<subdir>/<name>.ttf + -meta.json + -mtsdf.png and restart.").apply {
                color = DEMO_MUTED
                style = { textWrap = TextWrap.Wrap }
            }
        )
        text(
            TextProps {
                val preview = selectableFonts
                    .take(8)
                    .joinToString(", ") { "${it.fontId}[${it.source.name.lowercase()}]" }
                if (selectableFonts.size > 8) {
                    "Registered fonts (${selectableFonts.size}): $preview ..."
                } else {
                    "Registered fonts (${selectableFonts.size}): $preview"
                }
            }.apply {
                color = DEMO_MUTED
                style = { textWrap = TextWrap.Wrap }
            }
        )

        div(
            ComponentProps(
                key = "msdf.panel",
                width = panelWidth,
                padding = 4,
                gap = 2,
                backgroundColor = 0xFF233040.toInt()
            ).asFlexColumn().apply {
                style = {
                    border(1, 0xFF5F7288.toInt())
                }
            }
        ) {
            text(
                TextProps("Header text").apply {
                    style = {
                        fontId(fontId)
                        fontSize(fontSize)
                        foregroundColor(textColor)
                        this.opacity = opacity
                        textWrap = TextWrap.Wrap
                        textFormatting = formattingMode
                    }
                }
            )
            text(
                TextProps("Style only: bold + italic").apply {
                    style = {
                        fontId(fontId)
                        fontSize(fontSize)
                        foregroundColor(textColor)
                        this.opacity = opacity
                        fontWeight = org.dreamfinity.dsgl.core.style.FontWeight.Bold
                        fontStyle = org.dreamfinity.dsgl.core.style.FontStyle.Italic
                    }
                }
            )
            text(
                TextProps("Style only: underline + strikethrough").apply {
                    style = {
                        fontId(fontId)
                        fontSize(fontSize)
                        foregroundColor(textColor)
                        this.opacity = opacity
                        textDecoration = org.dreamfinity.dsgl.core.style.TextDecoration.UnderlineStrikethrough
                    }
                }
            )
            text(
                TextProps("Style only: obfuscated text sample 12345").apply {
                    style = {
                        fontId(fontId)
                        fontSize(fontSize)
                        foregroundColor(textColor)
                        this.opacity = opacity
                        obfuscated = true
                    }
                }
            )
            text(TextProps(LONG_CH_SENTENCE).apply {
                style = {
                    fontId("Noto_Sans_SC/NotoSansSC")
                    fontSize(fontSize)
                    foregroundColor(textColor)
                    this.opacity = opacity
                    textWrap = TextWrap.Wrap
                }
            })
            text(TextProps(LONG_CH_SENTENCE).apply {
                style = {
                    fontId("Noto_Sans_TC/NotoSansTC")
                    fontSize(fontSize)
                    foregroundColor(textColor)
                    this.opacity = opacity
                    textWrap = TextWrap.Wrap
                }
            })
            text(TextProps(LONG_JP_SENTENCE).apply {
                style = {
                    fontId("Noto_Sans_JP/NotoSansJP")
                    fontSize(fontSize)
                    foregroundColor(textColor)
                    this.opacity = opacity
                    textWrap = TextWrap.Wrap
                }
            })
            text(TextProps(LONG_KR_SENTENCE).apply {
                style = {
                    fontId("Noto_Sans_KR/NotoSansKR")
                    fontSize(fontSize)
                    foregroundColor(textColor)
                    this.opacity = opacity
                    textWrap = TextWrap.Wrap
                }
            })
            text(
                TextProps(SAMPLE_PARAGRAPH).apply {
                    style = {
                        fontId(fontId)
                        fontSize(fontSize)
                        foregroundColor(textColor)
                        this.opacity = opacity
                        textWrap = TextWrap.Wrap
                        textFormatting = formattingMode
                    }
                }
            )
            text(
                TextProps(SAMPLE_WORD).apply {
                    style = {
                        fontId(fontId)
                        fontSize(fontSize)
                        foregroundColor(textColor)
                        this.opacity = opacity
                        textWrap = TextWrap.Wrap
                        textFormatting = formattingMode
                    }
                }
            )
            text(
                TextProps(SAMPLE_SPACES_A).apply {
                    style = {
                        fontId(fontId)
                        fontSize(fontSize)
                        foregroundColor(textColor)
                        this.opacity = opacity
                        textWrap = TextWrap.Wrap
                        textFormatting = formattingMode
                    }
                }
            )
            text(
                TextProps(SAMPLE_SPACES_B).apply {
                    style = {
                        fontId(fontId)
                        fontSize(fontSize)
                        foregroundColor(textColor)
                        this.opacity = opacity
                        textWrap = TextWrap.Wrap
                        textFormatting = formattingMode
                    }
                }
            )
            text(
                TextProps("Minecraft Color Codes").apply {
                    color = DEMO_MUTED
                }
            )
            text(
                TextProps(SAMPLE_MC_COLORS).apply {
                    style = {
                        fontId(fontId)
                        fontSize(fontSize)
                        foregroundColor(textColor)
                        this.opacity = opacity
                        textWrap = TextWrap.Wrap
                        textFormatting = formattingMode
                    }
                }
            )
            text(
                TextProps(SAMPLE_MC_FLAGS).apply {
                    style = {
                        fontId(fontId)
                        fontSize(fontSize)
                        foregroundColor(textColor)
                        this.opacity = opacity
                        textWrap = TextWrap.Wrap
                        textFormatting = formattingMode
                    }
                }
            )
        }
    }
}
