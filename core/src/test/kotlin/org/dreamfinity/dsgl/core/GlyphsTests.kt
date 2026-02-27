package org.dreamfinity.dsgl.core

import sun.font.StandardGlyphVector
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.io.File
import kotlin.test.Test


class GlyphsTests {
    @Test
    fun `test glyphs`() {
        val file = File("Y:\\DreamfinityProject\\mods\\dsgl\\fonts\\ubuntu\\Ubuntu-Regular.ttf")
        val fontInputStream = file.inputStream()
        val frc = FontRenderContext(null, true, true)
        val font = Font.createFont(Font.TRUETYPE_FONT, fontInputStream).deriveFont(14f)
        val gv = font.createGlyphVector(frc, "Hello, Minecraft!") as StandardGlyphVector
        gv.glyphInfo
        val x = 1
    }
}
