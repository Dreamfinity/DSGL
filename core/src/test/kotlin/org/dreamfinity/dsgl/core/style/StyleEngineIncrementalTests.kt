package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StyleEngineIncrementalTests {
    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
        StyleEngine.setStylesDirectory(null)
    }

    @Test
    fun `second style pass uses cache when nothing changed`() {
        val root = ContainerNode(key = "root")
        TextNode(TextSource.Static("one"), key = "one").applyParent(root)
        TextNode(TextSource.Static("two"), key = "two").applyParent(root)

        val first = StyleEngine.applyStylesRecursivelyDetailed(root)
        val second = StyleEngine.applyStylesRecursivelyDetailed(root)

        assertTrue(first.visitedNodes >= 3)
        assertEquals(second.visitedNodes, second.cacheHits)
        assertEquals(0, second.recomputedNodes)
    }

    @Test
    fun `inspector override bumps style revision`() {
        val root = ContainerNode(key = "root")
        val text = TextNode(TextSource.Static("demo"), key = "text").applyParent(root)
        StyleEngine.applyStylesRecursivelyDetailed(root)

        val before = StyleEngine.currentStyleRevision()
        StyleEngine.setInspectorOverrideLiteral(text, StyleProperty.FOREGROUND_COLOR, "#00AAFF").getOrThrow()
        val after = StyleEngine.currentStyleRevision()

        assertTrue(after != before)
    }

    @Test
    fun `pseudo-state change restyles only dirty subtree`() {
        val root = ContainerNode(key = "root")
        val branchA = ContainerNode(key = "branchA").applyParent(root)
        val branchB = ContainerNode(key = "branchB").applyParent(root)
        TextNode(TextSource.Static("A"), key = "aText").applyParent(branchA)
        val hoveredLeaf = TextNode(TextSource.Static("B"), key = "bText").applyParent(branchB)

        val baseline = StyleEngine.applyStylesRecursivelyDetailed(root)
        hoveredLeaf.setHoveredState(true)
        val afterHover = StyleEngine.applyStylesRecursivelyDetailed(root)

        assertTrue(baseline.visitedNodes >= 5)
        assertTrue(afterHover.visitedNodes <= 2, "Expected targeted restyle, but visited=${afterHover.visitedNodes}")
    }

    @Test
    fun `sibling selector change restyles only affected following range`() {
        installStylesheet(
            """
            .a ~ .b { color: #33AA66; }
            """.trimIndent(),
        )

        val root = ContainerNode(key = "root")
        val row = ContainerNode(key = "row").applyParent(root)
        val marker = TextNode(TextSource.Static("marker"), key = "marker").applyParent(row)
        val target1 = TextNode(TextSource.Static("target1"), key = "target1").applyParent(row)
        target1.setClassNames("b")
        val target2 = TextNode(TextSource.Static("target2"), key = "target2").applyParent(row)
        target2.setClassNames("b")
        val unaffectedBranch = ContainerNode(key = "other").applyParent(root)
        TextNode(TextSource.Static("other"), key = "other-text").applyParent(unaffectedBranch)

        val baseline = StyleEngine.applyStylesRecursivelyDetailed(root)
        marker.setClassNames("a")
        val afterChange = StyleEngine.applyStylesRecursivelyDetailed(root)

        assertTrue(baseline.visitedNodes >= 6)
        assertTrue(afterChange.visitedNodes <= 3, "Expected range-only restyle, visited=${afterChange.visitedNodes}")
    }

    @Test
    fun `detached dirty selector markers do not skip active tree styling`() {
        installStylesheet(
            """
            .target { color: #2288FF; }
            """.trimIndent(),
        )

        val root = ContainerNode(key = "root")
        val target = TextNode(TextSource.Static("target"), key = "target").applyParent(root)
        target.setClassNames("target")
        StyleEngine.applyStylesRecursivelyDetailed(root)

        val detached = TextNode(TextSource.Static("detached"), key = "detached")
        detached.setClassNames("other")

        val report = StyleEngine.applyStylesRecursivelyDetailed(root)
        assertTrue(report.visitedNodes >= 2, "Active tree should still be styled when dirty markers are detached.")
        assertEquals(0xFF2288FF.toInt(), target.color)
    }

    @Test
    fun `targeted pass skips unchanged subtree when cache hit`() {
        installStylesheet(
            """
            .node { color: #FFFFFF; }
            """.trimIndent(),
        )

        val root = ContainerNode(key = "root")
        val branch = ContainerNode(key = "branch").applyParent(root)
        branch.setClassNames("node")
        repeat(6) { index ->
            TextNode(TextSource.Static("child-$index"), key = "child-$index").applyParent(branch)
        }

        StyleEngine.applyStylesRecursivelyDetailed(root)
        StyleEngine.markSelectorStateChanged(branch)
        val report = StyleEngine.applyStylesRecursivelyDetailed(root)

        assertEquals(1, report.visitedNodes, "Expected subtree short-circuit on cache hit.")
        assertEquals(1, report.cacheHits, "Branch node should be served from style cache.")
        assertEquals(0, report.recomputedNodes)
    }

    @Test
    fun `changed node state invalidates style cache entry`() {
        installStylesheet(
            """
            .marked { color: #AA3344; }
            """.trimIndent(),
        )

        val root = ContainerNode(key = "root")
        val leaf = TextNode(TextSource.Static("leaf"), key = "leaf").applyParent(root)
        StyleEngine.applyStylesRecursivelyDetailed(root)

        val unchanged = StyleEngine.applyStylesRecursivelyDetailed(root)
        assertEquals(0, unchanged.recomputedNodes, "Unchanged nodes must be served from the style cache.")

        leaf.setClassNames("marked")
        val afterClassChange = StyleEngine.applyStylesRecursivelyDetailed(root)
        assertTrue(afterClassChange.recomputedNodes >= 1, "Class change must invalidate the cached style.")
        assertEquals(0xFFAA3344.toInt(), leaf.color)

        leaf.setHoveredState(true)
        val afterHoverChange = StyleEngine.applyStylesRecursivelyDetailed(root)
        assertTrue(afterHoverChange.recomputedNodes >= 1, "Pseudo-state change must invalidate the cached style.")
    }

    private fun installStylesheet(contents: String) {
        val dir = Files.createTempDirectory("dsgl-style-incremental-test").toFile()
        dir.resolve("test.dss").writeText(contents)
        StyleEngine.setStylesDirectory(dir)
        StyleEngine.forceReloadStylesheets()
    }
}
