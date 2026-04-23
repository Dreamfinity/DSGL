package org.dreamfinity.dsgl.mcForge1710.demo.sections

import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.FontStyle
import org.dreamfinity.dsgl.core.style.FontWeight
import org.dreamfinity.dsgl.core.style.TextDecoration
import org.dreamfinity.dsgl.core.style.TextFormatting
import org.dreamfinity.dsgl.core.style.TextWrap
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mcForge1710.text.MsdfRuntimeDebugSettings

private val COLOR_PRESETS =
    listOf(
        0xFFFFFFFF.toInt(),
        0xFFFFC857.toInt(),
        0xFF8EE3F5.toInt(),
        0xFFFF7E67.toInt(),
    )

private const val SAMPLE_PARAGRAPH =
    "MSDF/MTSDF text rendering demo in DSGL. This paragraph should wrap cleanly in a fixed-width panel and respect font switches, opacity, and size."
private const val SAMPLE_WORD =
    "\u4ED6\u65B9\u3001\u6210\u7E3E\u8A55long_unbroken_word_to_force_hard_break_ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789"
private const val SAMPLE_SPACES_A = "Hello   world"
private const val LONG_CH_SENTENCE =
    "\u592A\u9633\u6652\u5F97\u58A8\u9ED1\u7684\u6E05\u7626\u7684\u8138\u4E0A\uFF0C\u6709\u4E00\u5BF9\u7A0D\u7A0D\u6D3C\u8FDB\u53BB\u7684\u5927\u5927\u7684\u53CC\u773C\u76AE\u513F\u773C\u775B\uFF0C\u7709\u6BDB\u7EC6\u800C\u659C\uFF0C\u9ED1\u91CC\u5E26\u9EC4\u7684\u5934\u53D1\u7528\u82B1\u5E03\u6761\u5B50\u624E\u4E24\u6761\u77ED\u8FAB\u5B50\uFF0C\u8863\u670D\u90FD\u5F88\u65E7\uFF0C\u53F3\u88E4\u811A\u4E0A\u7684\u4E00\u4E2A\u7834\u6D1E\u522B\u4E00\u652F\u522B\u9488\uFF0C\u6625\u590F\u79CB\u4E09\u5B63\u90FD\u6253\u8D64\u811A\uFF0C\u53EA\u6709\u4E0A\u5C71\u6293\u67F4\u79BE\u7684\u65F6\u8282\uFF0C\u6015\u523A\u7834\u811A\u677F\uFF0C\u624D\u7A7F\u53CC\u978B\u5B50\uFF0C\u4F46\u4E00\u4E0B\u5C71\u5C31\u8131\u4E86\u3002"
private const val LONG_JP_SENTENCE =
    "\u4ED6\u65B9\u3001\u6210\u7E3E\u8A55\u4FA1\u306E\u7518\u3044\u6388\u696D\u304C\u9AD8\u304F\u8A55\u4FA1\u3055\u308C\u305F\u308A\u3001\u4EBA\u6C17\u53D6\u308A\u306B\u8D70\u308B\u6559\u5E2B\u304C\u51FA\u305F\u308A\u3057\u3001\u6210\u7E3E\u306E\u5B89\u58F2\u308A\u3084\u5927\u5B66\u6559\u5E2B\u306E\u30EC\u30D9\u30EB\u30C0\u30A6\u30F3\u3068\u3044\u3046\u5F0A\u5BB3\u3092\u3082\u305F\u3089\u3059\u6050\u308C\u304C\u3042\u308B\u3001\u306A\u3069\u306E\u53CD\u7701\u610F\u898B\u3082\u3042\u308B."
private const val LONG_KR_SENTENCE =
    "\uC800\uB294 \uC624\uB298 \uC544\uCE68\uC5D0 \uCE74\uD398\uC5D0\uC11C \uCE5C\uAD6C\uB791 \uD55C\uAD6D\uC5B4 \uACF5\uBD80\uB97C \uD558\uACE0 \uB098\uC11C \uB3C4\uC11C\uAD00\uC5D0 \uAC08 \uAC70\uC608\uC694."

private const val SAMPLE_SPACES_B = "A A  A"
private const val SAMPLE_MC_COLORS = "\u00A7aGreen \u00A7bBlue \u00A7cRed \u00A7rBackToDefault"
private const val SAMPLE_MC_FLAGS =
    "\u00A7lBold \u00A7oItalic \u00A7nUnderline \u00A7mStrike \u00A7kMagic\u00A7r Normal"

fun UiScope.msdfFontsSection(onInfo: (String) -> Unit) {
    var msdfOpacityPercent by useState(100L)
    var msdfFontSizePx by useState(9L)
    var msdfWrapWidthPercent by useState(15L)
    var msdfColorIndex by useState(0)
    var msdfParseMinecraftFormatting by useState(true)
    var msdfShowBaselineGuides by useState(MsdfRuntimeDebugSettings.decorationGuidesEnabled)
    val selectableFonts = FontRegistry.registeredFonts()
    var selectedFont by useState(selectableFonts.first())

    val panelWidthPercent = msdfWrapWidthPercent.coerceIn(0L, 100L)
    val textOpacity = (msdfOpacityPercent.toFloat() / 100f).coerceIn(0f, 1f)
    val fontSize = msdfFontSizePx.toInt().coerceIn(6, 48)

    val textColor = COLOR_PRESETS[msdfColorIndex.coerceIn(0, COLOR_PRESETS.lastIndex)]
    val formattingMode = if (msdfParseMinecraftFormatting) TextFormatting.Minecraft else TextFormatting.None

    div({
        key = "section.msdfFonts"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("MSDF Fonts")
        text(
            "All DSGL DrawText commands go through MSDF/MTSDF rendering. Switch font/size/color/opacity and verify wrapping.",
            { style = { color = DEMO_MUTED } },
        )
        text("DREAMFINITY", { style = { fontId = "telegrafico" } })

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            select({
                value = selectedFont.fontId
                onValueChange = { event ->
                    selectedFont = selectableFonts.first { it.fontId == event.value }
                }
            }) {
                selectableFonts.forEach { font ->
                    option(font.fontId, font.fontId)
                }
            }

            button("Color", {
                onMouseClick = {
                    msdfColorIndex = (msdfColorIndex + 1) % COLOR_PRESETS.size
                }
            })
            button(
                if (msdfParseMinecraftFormatting) {
                    "Formatting ON"
                } else {
                    "Formatting OFF"
                },
                {
                    onMouseClick = {
                        msdfParseMinecraftFormatting = !msdfParseMinecraftFormatting
                        onInfo("MSDF formatting=$msdfParseMinecraftFormatting")
                    }
                },
            )
            button(
                if (msdfShowBaselineGuides) {
                    "Guides ON"
                } else {
                    "Guides OFF"
                },
                {
                    onMouseClick = {
                        msdfShowBaselineGuides = !msdfShowBaselineGuides
                        MsdfRuntimeDebugSettings.decorationGuidesEnabled = msdfShowBaselineGuides
                        System.setProperty(
                            "dsgl.msdf.debug.decorations",
                            msdfShowBaselineGuides.toString(),
                        )
                        onInfo("MSDF guides=$msdfShowBaselineGuides")
                    }
                },
            )
        }

        input(
            InputType.Range(
                value = msdfOpacityPercent,
                min = 0,
                max = 100,
                step = 1,
            ),
            {
                key = "msdf.opacity"
                style = { width = 100.percent }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: msdfOpacityPercent
                    msdfOpacityPercent = next.coerceIn(0, 100)
                }
            },
        )
        input(
            InputType.Range(
                value = msdfFontSizePx,
                min = 6,
                max = 48,
                step = 1,
            ),
            {
                key = "msdf.fontSize"
                style = { width = 100.percent }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: msdfFontSizePx
                    msdfFontSizePx = next.coerceIn(6, 48)
                }
            },
        )
        input(
            InputType.Range(
                value = panelWidthPercent,
                min = 0L,
                max = 100L,
                step = 2,
            ),
            {
                key = "msdf.wrapWidth"
                style = { width = 100.percent }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: panelWidthPercent
                    msdfWrapWidthPercent = next.coerceIn(0L, 100L)
                }
            },
        )

        text(
            "fontId=${selectedFont.fontId} source=${selectedFont.source.name.lowercase()} fontSize=$fontSize opacity=$textOpacity panelWidth=$panelWidthPercent% formatting=${formattingMode.name.lowercase()} guides=$msdfShowBaselineGuides",
            { style = { this.color = DEMO_MUTED } },
        )
        text(
            "Drop external font packages into <gameDir>/dsgl/fonts/<subdir>/<name>.ttf + -meta.json + -mtsdf.png and restart.",
            {
                style = {
                    color = DEMO_MUTED
                    textWrap = TextWrap.Wrap
                }
            },
        )
        text({
            val preview =
                selectableFonts
                    .take(8)
                    .joinToString(", ") { "${it.fontId}[${it.source.name.lowercase()}]" }
            value =
                if (selectableFonts.size > 8) {
                    "Registered fonts (${selectableFonts.size}): $preview ..."
                } else {
                    "Registered fonts (${selectableFonts.size}): $preview"
                }

            style = {
                color = DEMO_MUTED
                textWrap = TextWrap.Wrap
            }
        })

        div({
            key = "msdf.panel"
            style = {
                width = panelWidthPercent.percent
                padding = 4.px
                gap = 2.px
                backgroundColor = 0xFF233040.toInt()
                display = Display.Flex
                flexDirection = FlexDirection.Column
                border {
                    width = 1.px
                    color = 0xFF5F7288.toInt()
                }
            }
        }) {
            text("Header text", {
                style = {
                    fontId = selectedFont.fontId
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    textWrap = TextWrap.Wrap
                    textFormatting = formattingMode
                }
            })
            text("Style only: bold + italic", {
                style = {
                    fontId = selectedFont.fontId
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    fontWeight = FontWeight.Bold
                    fontStyle = FontStyle.Italic
                }
            })
            text("Style only: underline + strikethrough", {
                style = {
                    fontId = selectedFont.fontId
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    textDecoration = TextDecoration.UnderlineStrikethrough
                }
            })
            text("Style only: obfuscated text sample 12345", {
                style = {
                    fontId = selectedFont.fontId
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    obfuscated = true
                }
            })
            text(LONG_CH_SENTENCE, {
                style = {
                    fontId = "Noto_Sans_SC/NotoSansSC"
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    textWrap = TextWrap.Wrap
                }
            })
            text(LONG_CH_SENTENCE, {
                style = {
                    fontId = "Noto_Sans_TC/NotoSansTC"
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    textWrap = TextWrap.Wrap
                }
            })
            text(LONG_JP_SENTENCE, {
                style = {
                    fontId = "Noto_Sans_JP/NotoSansJP"
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    textWrap = TextWrap.Wrap
                }
            })
            text(LONG_KR_SENTENCE, {
                style = {
                    fontId = "Noto_Sans_KR/NotoSansKR"
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    textWrap = TextWrap.Wrap
                }
            })
            text(SAMPLE_PARAGRAPH, {
                style = {
                    fontId = selectedFont.fontId
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    textWrap = TextWrap.Wrap
                    textFormatting = formattingMode
                }
            })
            text(SAMPLE_WORD, {
                style = {
                    fontId = selectedFont.fontId
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    textWrap = TextWrap.Wrap
                    textFormatting = formattingMode
                }
            })
            text(SAMPLE_SPACES_A, {
                style = {
                    fontId = selectedFont.fontId
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    textWrap = TextWrap.Wrap
                    textFormatting = formattingMode
                }
            })
            text(SAMPLE_SPACES_B, {
                style = {
                    fontId = selectedFont.fontId
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    textWrap = TextWrap.Wrap
                    textFormatting = formattingMode
                }
            })
            text("Minecraft Color Codes", {
                style = { color = DEMO_MUTED }
            })
            text(SAMPLE_MC_COLORS, {
                style = {
                    fontId = selectedFont.fontId
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    textWrap = TextWrap.Wrap
                    textFormatting = formattingMode
                }
            })
            text(SAMPLE_MC_FLAGS, {
                style = {
                    fontId = selectedFont.fontId
                    this.fontSize = fontSize.px
                    foregroundColor = textColor
                    this.opacity = textOpacity
                    textWrap = TextWrap.Wrap
                    textFormatting = formattingMode
                }
            })
        }
    }
}
