package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverflowSizeStyleContractTests {
    private val tempDirs: MutableList<File> = mutableListOf()

    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
        StyleEngine.setStylesDirectory(null)
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    @Test
    fun `overflow shorthand single value resolves both axes`() {
        assertOverflowAxes(parseOverflowShorthand("visible"), Overflow.Visible, Overflow.Visible)
        assertOverflowAxes(parseOverflowShorthand("hidden"), Overflow.Hidden, Overflow.Hidden)
        assertOverflowAxes(parseOverflowShorthand("scroll"), Overflow.Scroll, Overflow.Scroll)
        assertOverflowAxes(parseOverflowShorthand("auto"), Overflow.Auto, Overflow.Auto)
    }

    @Test
    fun `overflow shorthand two values resolves x then y`() {
        assertOverflowAxes(parseOverflowShorthand("hidden auto"), Overflow.Hidden, Overflow.Auto)
        assertOverflowAxes(parseOverflowShorthand("scroll visible"), Overflow.Scroll, Overflow.Visible)
    }

    @Test
    fun `overflow axis properties override shorthand-derived values`() {
        val target =
            styledTarget(
                """
                .target {
                  overflow: hidden auto;
                  overflow-x: visible;
                  overflow-y: scroll;
                }
                """.trimIndent(),
            )

        val style = target.appliedComputedStyleSnapshot() ?: error("Missing computed style")
        assertEquals(Overflow.Visible, style.overflowX)
        assertEquals(Overflow.Scroll, style.overflowY)
    }

    @Test
    fun `min and max size properties parse and persist in computed style`() {
        val target =
            styledTarget(
                """
                .target {
                  min-width: 120px;
                  min-height: 10vh;
                  max-width: 75%;
                  max-height: auto;
                }
                """.trimIndent(),
            )

        val style = target.appliedComputedStyleSnapshot() ?: error("Missing computed style")
        assertEquals(CssLength(120f, CssUnit.Px), style.minWidth)
        assertEquals(CssLength(10f, CssUnit.Vh), style.minHeight)
        assertEquals(CssLength(75f, CssUnit.Percent), style.maxWidth)
        assertNull(style.maxHeight)
    }

    @Test
    fun `dsl exposes overflow axis and min max size properties`() {
        val root = ContainerNode(key = "root")
        val target = ContainerNode(key = "target").applyParent(root)
        target.applyStyle {
            overflow = Overflow.Hidden
            overflowY = Overflow.Auto
            minWidth = 40.px
            minHeight = 15.percent
            maxWidth = 320.px
            maxHeight = null
        }

        StyleEngine.applyStylesRecursively(root)
        val style = target.appliedComputedStyleSnapshot() ?: error("Missing computed style")
        assertEquals(Overflow.Hidden, style.overflowX)
        assertEquals(Overflow.Auto, style.overflowY)
        assertEquals(CssLength(40f, CssUnit.Px), style.minWidth)
        assertEquals(CssLength(15f, CssUnit.Percent), style.minHeight)
        assertEquals(CssLength(320f, CssUnit.Px), style.maxWidth)
        assertNull(style.maxHeight)
    }

    private fun styledTarget(dss: String): ContainerNode {
        val dir = Files.createTempDirectory("dsgl-overflow-style-contract-").toFile()
        tempDirs += dir
        dir.resolve("contract.dss").writeText(dss)
        StyleEngine.setStylesDirectory(dir)
        StyleEngine.forceReloadStylesheets()

        val root = ContainerNode(key = "root")
        val target = ContainerNode(key = "target").applyParent(root)
        target.setClassNames("target")

        StyleEngine.applyStylesRecursively(root)
        return target
    }

    private fun assertOverflowAxes(actual: OverflowAxes, expectedX: Overflow, expectedY: Overflow) {
        assertEquals(expectedX, actual.overflowX)
        assertEquals(expectedY, actual.overflowY)
    }
}
