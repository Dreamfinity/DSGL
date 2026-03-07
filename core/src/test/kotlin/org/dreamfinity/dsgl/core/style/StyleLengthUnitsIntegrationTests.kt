package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StyleLengthUnitsIntegrationTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 10
        override fun measureText(text: String): Int = text.length * 6
        override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * ((fontSize ?: 10) / 2).coerceAtLeast(1)
        override fun fontHeight(fontId: String?, fontSize: Int?): Int = fontSize ?: 10
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        StyleEngine.setStylesDirectory(null)
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `font-size em uses inherited font size`() {
        installStylesheet(
            """
            .parent { font-size: 20px; }
            .child { font-size: 1.2em; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val parent = ContainerNode(key = "parent").applyParent(root)
        parent.setClassNames("parent")
        val child = ContainerNode(key = "child").applyParent(parent)
        child.setClassNames("child")

        StyleEngine.setViewportSize(320, 200)
        StyleEngine.applyStylesRecursively(root)

        assertEquals(24, child.fontSize)
    }

    @Test
    fun `padding em uses current element computed font size`() {
        installStylesheet(
            """
            .parent { font-size: 20px; }
            .child { font-size: 10px; padding: 1.5em; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val parent = ContainerNode(key = "parent").applyParent(root)
        parent.setClassNames("parent")
        val child = ContainerNode(key = "child").applyParent(parent)
        child.setClassNames("child")

        DomTree(root).render(ctx, 200, 120)

        assertEquals(15, child.padding.top)
        assertEquals(15, child.padding.right)
        assertEquals(15, child.padding.bottom)
        assertEquals(15, child.padding.left)
    }

    @Test
    fun `percent width and height resolve against containing block axes`() {
        installStylesheet(
            """
            .child {
              width: 50%;
              height: 25%;
              margin: 10% 5%;
            }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val child = ContainerNode(key = "child").applyParent(root)
        child.setClassNames("child")

        DomTree(root).render(ctx, 200, 120)

        assertEquals(100, child.width)
        assertEquals(30, child.height)
        assertEquals(12, child.margin.top)
        assertEquals(10, child.margin.left)
        assertEquals(12, child.margin.bottom)
        assertEquals(10, child.margin.right)
    }

    @Test
    fun `vw and vh resolve against viewport size`() {
        installStylesheet(
            """
            .child {
              width: 10vw;
              height: 20vh;
              padding: 1vh 1vw;
            }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val child = ContainerNode(key = "child").applyParent(root)
        child.setClassNames("child")

        DomTree(root).render(ctx, 300, 200)

        assertEquals(30, child.width)
        assertEquals(40, child.height)
        assertEquals(2, child.padding.top)
        assertEquals(3, child.padding.left)
    }

    @Test
    fun `rem resolves against root font size`() {
        installStylesheet(
            """
            :root { font-size: 20px; }
            .child {
              width: 2rem;
              font-size: 0.5rem;
              padding: 1rem;
            }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val child = ContainerNode(key = "child").applyParent(root)
        child.setClassNames("child")

        DomTree(root).render(ctx, 240, 160)

        assertEquals(40, child.width)
        assertEquals(10, child.fontSize)
        assertEquals(20, child.padding.top)
        assertEquals(20, child.padding.left)
    }

    private fun installStylesheet(contents: String) {
        val dir = Files.createTempDirectory("dsgl-style-units-test").toFile()
        dir.resolve("test.dss").writeText(contents)
        StyleEngine.setStylesDirectory(dir)
        StyleEngine.forceReloadStylesheets()
    }
}
