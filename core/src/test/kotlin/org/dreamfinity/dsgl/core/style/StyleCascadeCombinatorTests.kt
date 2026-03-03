package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import org.dreamfinity.dsgl.core.dom.layout.Insets
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StyleCascadeCombinatorTests {
    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
        StyleEngine.setStylesDirectory(null)
    }

    @Test
    fun `descendant combinator matches nested nodes only`() {
        installStylesheet(
            """
            .panel .item { color: #00AA00; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val panel = ContainerNode(key = "panel").applyParent(root)
        panel.setClassNames("panel")
        ContainerNode(key = "row").applyParent(panel).apply {
            TextNode(TextSource.Static("inside"), key = "inside").applyParent(this).setClassNames("item")
        }
        val outside = TextNode(TextSource.Static("outside"), key = "outside").applyParent(root)
        outside.setClassNames("item")

        StyleEngine.applyStylesRecursively(root)
        val inside = findByKey(root, "inside") as TextNode
        assertEquals(0xFF00AA00.toInt(), inside.color)
        assertEquals(DsglColors.TEXT, outside.color)
    }

    @Test
    fun `child combinator matches only direct child`() {
        installStylesheet(
            """
            .panel > .item { color: #3355AA; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val panel = ContainerNode(key = "panel").applyParent(root)
        panel.setClassNames("panel")
        val direct = TextNode(TextSource.Static("direct"), key = "direct").applyParent(panel)
        direct.setClassNames("item")
        val nestedWrap = ContainerNode(key = "nestedWrap").applyParent(panel)
        val nested = TextNode(TextSource.Static("nested"), key = "nested").applyParent(nestedWrap)
        nested.setClassNames("item")

        StyleEngine.applyStylesRecursively(root)
        assertEquals(0xFF3355AA.toInt(), direct.color)
        assertEquals(DsglColors.TEXT, nested.color)
    }

    @Test
    fun `specificity id beats class and type`() {
        installStylesheet(
            """
            text { color: #111111; }
            .btn { color: #222222; }
            #target { color: #333333; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val target = TextNode(TextSource.Static("target"), key = "target").applyParent(root)
        target.setClassNames("btn")
        target.styleId = "target"

        StyleEngine.applyStylesRecursively(root)
        assertEquals(0xFF333333.toInt(), target.color)
    }

    @Test
    fun `equal specificity resolves by source order`() {
        installStylesheet(
            """
            .btn { color: #111111; }
            .btn { color: #222222; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val target = TextNode(TextSource.Static("target"), key = "target").applyParent(root)
        target.setClassNames("btn")

        StyleEngine.applyStylesRecursively(root)
        assertEquals(0xFF222222.toInt(), target.color)
    }

    @Test
    fun `important wins within same origin`() {
        installStylesheet(
            """
            .btn { color: #111111 !important; }
            .btn { color: #222222; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val target = TextNode(TextSource.Static("target"), key = "target").applyParent(root)
        target.setClassNames("btn")

        StyleEngine.applyStylesRecursively(root)
        assertEquals(0xFF111111.toInt(), target.color)
    }

    @Test
    fun `inherited text properties propagate from parent`() {
        installStylesheet(
            """
            .parent { color: #ABCDEF; font-size: 14; padding: 7; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val parent = ContainerNode(key = "parent").applyParent(root)
        parent.setClassNames("parent")
        val child = TextNode(TextSource.Static("child"), key = "child").applyParent(parent)

        StyleEngine.applyStylesRecursively(root)
        assertEquals(0xFFABCDEF.toInt(), child.color)
        assertEquals(14, child.fontSize)
        assertEquals(Insets.ZERO, child.padding)
    }

    @Test
    fun `ancestor pseudo selector updates descendant on targeted invalidation`() {
        installStylesheet(
            """
            .panel:hover .item { color: #00AAFF; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val panel = ContainerNode(key = "panel").applyParent(root)
        panel.setClassNames("panel")
        val child = TextNode(TextSource.Static("item"), key = "item").applyParent(panel)
        child.setClassNames("item")

        StyleEngine.applyStylesRecursively(root)
        assertEquals(DsglColors.TEXT, child.color)
        panel.setHoveredState(true)
        StyleEngine.applyStylesRecursively(root)
        assertEquals(0xFF00AAFF.toInt(), child.color)
    }

    @Test
    fun `adjacent sibling combinator matches only immediate next sibling`() {
        installStylesheet(
            """
            .a + .b { color: #FF22AA; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val row = ContainerNode(key = "row").applyParent(root)
        val first = TextNode(TextSource.Static("first"), key = "first").applyParent(row)
        val second = TextNode(TextSource.Static("second"), key = "second").applyParent(row)
        val third = TextNode(TextSource.Static("third"), key = "third").applyParent(row)
        second.setClassNames("b")
        third.setClassNames("b")

        StyleEngine.applyStylesRecursively(root)
        assertEquals(DsglColors.TEXT, second.color)
        assertEquals(DsglColors.TEXT, third.color)

        first.setClassNames("a")
        StyleEngine.applyStylesRecursively(root)
        assertEquals(0xFFFF22AA.toInt(), second.color)
        assertEquals(DsglColors.TEXT, third.color)
    }

    @Test
    fun `general sibling combinator matches all following siblings`() {
        installStylesheet(
            """
            .a ~ .b { color: #22AAFF; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val row = ContainerNode(key = "row").applyParent(root)
        val before = TextNode(TextSource.Static("before"), key = "before").applyParent(row)
        before.setClassNames("b")
        val marker = TextNode(TextSource.Static("marker"), key = "marker").applyParent(row)
        marker.setClassNames("a")
        val after1 = TextNode(TextSource.Static("after1"), key = "after1").applyParent(row)
        after1.setClassNames("b")
        val after2 = TextNode(TextSource.Static("after2"), key = "after2").applyParent(row)
        after2.setClassNames("b")

        StyleEngine.applyStylesRecursively(root)
        assertEquals(DsglColors.TEXT, before.color)
        assertEquals(0xFF22AAFF.toInt(), after1.color)
        assertEquals(0xFF22AAFF.toInt(), after2.color)
    }

    @Test
    fun `mixed sibling descendant and child chains are matched`() {
        installStylesheet(
            """
            .a + .b .c { color: #5566FF; }
            .a ~ .b > .c { font-size: 14; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val host = ContainerNode(key = "host").applyParent(root)
        val left = ContainerNode(key = "left").applyParent(host)
        left.setClassNames("a")
        val middle = ContainerNode(key = "middle").applyParent(host)
        middle.setClassNames("b")
        val title = TextNode(TextSource.Static("title"), key = "title").applyParent(middle)
        title.setClassNames("c")

        StyleEngine.applyStylesRecursively(root)
        assertEquals(0xFF5566FF.toInt(), title.color)
        assertEquals(14, title.fontSize)
    }

    @Test
    fun `sibling combinator rule participates in cascade`() {
        installStylesheet(
            """
            .a ~ .b { color: #2255AA; }
            .b { color: #992222 !important; }
            """.trimIndent()
        )

        val root = ContainerNode(key = "root")
        val row = ContainerNode(key = "row").applyParent(root)
        TextNode(TextSource.Static("marker"), key = "marker").applyParent(row).setClassNames("a")
        val target = TextNode(TextSource.Static("target"), key = "target").applyParent(row)
        target.setClassNames("b")

        StyleEngine.applyStylesRecursively(root)
        assertEquals(0xFF992222.toInt(), target.color)
    }

    private fun installStylesheet(contents: String) {
        val dir = Files.createTempDirectory("dsgl-style-cascade-test").toFile()
        dir.resolve("test.dss").writeText(contents)
        StyleEngine.setStylesDirectory(dir)
        StyleEngine.forceReloadStylesheets()
    }

    private fun findByKey(node: ContainerNode, key: String): DOMNode {
        val queue = ArrayDeque<DOMNode>()
        queue += node
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.key == key) return current
            current.children.forEach { queue += it }
        }
        error("Node with key '$key' not found")
    }
}
