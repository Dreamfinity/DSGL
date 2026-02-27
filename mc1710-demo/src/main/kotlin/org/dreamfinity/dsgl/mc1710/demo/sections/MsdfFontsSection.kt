package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.TextFormatting
import org.dreamfinity.dsgl.core.style.TextWrap
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private val FONT_IDS = listOf("minecraft", "ubuntu", "JetBrains Mono", "telegrafico")
private val COLOR_PRESETS = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFFC857.toInt(),
    0xFF8EE3F5.toInt(),
    0xFFFF7E67.toInt()
)

private const val SAMPLE_PARAGRAPH =
    "MSDF/MTSDF text rendering demo in DSGL. This paragraph should wrap cleanly in a fixed-width panel and respect font switches, opacity, and size."
private const val SAMPLE_WORD = "long_unbroken_word_to_force_hard_break_ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789"
private const val SAMPLE_SPACES_A = "Hello   world"
private const val SAMPLE_SPACES_B = "A A  A"
private const val SAMPLE_MC_COLORS = "\u00A7aGreen \u00A7bBlue \u00A7cRed \u00A7rBackToDefault"
private const val SAMPLE_MC_FLAGS =
    "\u00A7lBold \u00A7oItalic \u00A7nUnderline \u00A7mStrike \u00A7kMagic\u00A7r Normal"

fun UiScope.renderMsdfFontsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val minWrapWidth = 120
    val maxWrapWidth = (contentWidth - 8).coerceAtLeast(minWrapWidth)
    val panelWidth = window.msdfWrapWidth.toInt().coerceIn(minWrapWidth, maxWrapWidth)
    val opacity = (window.msdfOpacityPercent.toFloat() / 100f).coerceIn(0f, 1f)
    val fontSize = window.msdfFontSizePx.toInt().coerceIn(6, 48)
    val fontId = FONT_IDS[window.msdfFontIndex.coerceIn(0, FONT_IDS.lastIndex)]
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
                        window.msdfFontIndex = (window.msdfFontIndex + 1) % FONT_IDS.size
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
                "fontId=$fontId fontSize=$fontSize opacity=$opacity panelWidth=$panelWidth formatting=${formattingMode.name.lowercase()} guides=${window.msdfShowBaselineGuides}"
            }.apply { this.color = DEMO_MUTED }
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
                    color = DEMO_MUTED.toInt()
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
