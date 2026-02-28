package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
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
}
