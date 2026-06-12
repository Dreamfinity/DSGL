package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ColorPickerInlineNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.style.CssLength
import org.dreamfinity.dsgl.core.style.StyleApplicationScope
import org.dreamfinity.dsgl.core.style.StyleEngine
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorPickerInlineStylingTests {
    @AfterTest
    fun cleanup() {
        StyleEngine.setStylesDirectory(null)
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `inline color picker is stylable in application scope`() {
        val stylesDir =
            createTempStylesDir(
                """
                color-picker {
                    border-width: 3px;
                    border-color: #224466;
                    padding: 7px;
                    width: 280px;
                }
                """.trimIndent(),
            )
        StyleEngine.setStylesDirectory(stylesDir)
        StyleEngine.forceReloadStylesheets()

        val root = ContainerNode(key = "root")
        val picker = ColorPickerInlineNode(key = "picker").applyParent(root)
        StyleEngine.applyStylesRecursively(root, StyleApplicationScope.Application)
        val inspection = StyleEngine.inspect(picker).computed

        assertEquals(CssLength.px(3f), inspection.borderWidth)
        assertEquals(0xFF224466.toInt(), inspection.borderColor)
        assertEquals(CssLength.px(7f), inspection.padding.left)
        assertEquals(CssLength.px(7f), inspection.padding.top)
        assertEquals(CssLength.px(280f), inspection.width)
    }

    private fun createTempStylesDir(dss: String): File {
        val root = Files.createTempDirectory("dsgl-color-picker-style-").toFile()
        root.resolve("test.dss").writeText(dss)
        return root
    }
}
