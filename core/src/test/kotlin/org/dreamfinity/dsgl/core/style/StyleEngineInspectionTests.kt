package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StyleEngineInspectionTests {
    @AfterTest
    fun cleanup() {
        StyleEngine.setStylesDirectory(null)
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `inspect reports selector and inline provenance`() {
        val stylesDir =
            createTempStylesDir(
                """
                text { color: #112233; }
                #sample { color: #445566; }
                """.trimIndent(),
            )
        StyleEngine.setStylesDirectory(stylesDir)
        StyleEngine.forceReloadStylesheets()

        val node = TextNode(TextSource.Static("hello"), key = "sample")
        node.styleId = "sample"

        val selectorInspection = StyleEngine.inspect(node)
        val selectorSource = selectorInspection.propertySources[StyleProperty.FOREGROUND_COLOR]
        assertEquals(StyleSourceKind.Selector, selectorSource?.kind)
        assertEquals(selectorSource?.source?.contains("#sample"), true)

        node.inlineStyleDeclarations.set(
            StyleProperty.FOREGROUND_COLOR,
            StyleExpression.Literal("#ABCDEF"),
        )
        val inlineInspection = StyleEngine.inspect(node)
        val inlineSource = inlineInspection.propertySources[StyleProperty.FOREGROUND_COLOR]
        assertEquals(StyleSourceKind.Inline, inlineSource?.kind)
        assertEquals("inline", inlineSource?.source)
    }

    @Test
    fun `inspector override has highest precedence`() {
        val stylesDir =
            createTempStylesDir(
                """
                text { color: #111111; }
                #sample { color: #222222; }
                """.trimIndent(),
            )
        StyleEngine.setStylesDirectory(stylesDir)
        StyleEngine.forceReloadStylesheets()

        val node = TextNode(TextSource.Static("hello"), key = "sample")
        node.styleId = "sample"
        node.inlineStyleDeclarations.set(
            StyleProperty.FOREGROUND_COLOR,
            StyleExpression.Literal("#333333"),
        )
        StyleEngine.setInspectorOverrideLiteral("sample", StyleProperty.FOREGROUND_COLOR, "#444444").getOrThrow()

        val inspection = StyleEngine.inspect(node)
        val source = inspection.propertySources[StyleProperty.FOREGROUND_COLOR]
        assertEquals(StyleSourceKind.InspectorOverride, source?.kind)
        assertEquals("inspector", source?.source)
        assertEquals(0xFF444444.toInt(), inspection.computed.foregroundColor)
    }

    @Test
    fun `inspector override works for node without key and survives equivalent rebuild`() {
        val root = ContainerNode(key = "root")
        val first = TextNode(TextSource.Static("first")).applyParent(root)
        val second = TextNode(TextSource.Static("second")).applyParent(root)

        StyleEngine.setInspectorOverrideLiteral(first, StyleProperty.FOREGROUND_COLOR, "#00AAFF").getOrThrow()
        StyleEngine.applyStylesRecursively(root)
        assertEquals(0xFF00AAFF.toInt(), first.color)
        assertEquals(DsglColors.TEXT, second.color)

        val rebuiltRoot = ContainerNode(key = "root")
        val rebuiltFirst = TextNode(TextSource.Static("first")).applyParent(rebuiltRoot)
        val rebuiltSecond = TextNode(TextSource.Static("second")).applyParent(rebuiltRoot)
        StyleEngine.applyStylesRecursively(rebuiltRoot)

        assertEquals(0xFF00AAFF.toInt(), rebuiltFirst.color)
        assertEquals(DsglColors.TEXT, rebuiltSecond.color)
    }

    private fun createTempStylesDir(dss: String): File {
        val root = Files.createTempDirectory("dsgl-style-inspect-").toFile()
        root.resolve("test.dss").writeText(dss)
        return root
    }
}
