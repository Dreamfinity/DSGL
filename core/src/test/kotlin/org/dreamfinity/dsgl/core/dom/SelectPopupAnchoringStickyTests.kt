package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.SelectNode
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.overlay.input.LayerDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectRuntime
import org.dreamfinity.dsgl.core.select.selectModel
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleDeclarations
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SelectPopupAnchoringStickyTests {
    private val viewportWidth = 420
    private val viewportHeight = 260

    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        SelectRuntime.host.closeAll()
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `select in non-sticky container anchors popup to visible rect`() {
        val fixture = createNonStickyFixture()
        fixture.tree.paint(ctx)

        val popup = openPopupAndResolveGeometry(fixture)
        val visible = visibleRect(fixture.select)

        assertEquals(visible, popup.anchor)
        assertEquals(visible.y + visible.height, popup.panel.y)
    }

    @Test
    fun `select in sticky container anchors correctly before sticky clamp`() {
        val fixture = createStickyFixture()
        fixture.scroller.setScrollOffsets(0, 8)
        fixture.tree.paint(ctx)

        val popup = openPopupAndResolveGeometry(fixture)
        val visible = visibleRect(fixture.select)
        val bounds = fixture.select.bounds

        assertEquals(bounds.y, visible.y)
        assertEquals(visible, popup.anchor)
        assertEquals(visible.y + visible.height, popup.panel.y)
    }

    @Test
    fun `select in sticky container anchors to visible rect after sticky clamp`() {
        val fixture = createStickyFixture()
        fixture.scroller.setScrollOffsets(0, 52)
        fixture.tree.paint(ctx)

        val popup = openPopupAndResolveGeometry(fixture)
        val visible = visibleRect(fixture.select)
        val bounds = fixture.select.bounds

        assertNotEquals(bounds.y, visible.y)
        assertEquals(visible, popup.anchor)
        assertEquals(visible.y + visible.height, popup.panel.y)
        assertNotEquals(bounds.y + bounds.height, popup.anchor.y + popup.anchor.height)
    }

    @Test
    fun `select open hit behavior remains unchanged under sticky clamp`() {
        val fixture = createStickyFixture()
        fixture.scroller.setScrollOffsets(0, 52)
        fixture.tree.paint(ctx)

        val visible = visibleRect(fixture.select)
        val x = visible.x + visible.width / 2
        val y = visible.y + visible.height / 2

        assertTrue(fixture.router.handleMouseDown(x, y, MouseButton.LEFT))
        assertTrue(SelectRuntime.host.isOpenFor(fixture.ownerKey))
        assertTrue(fixture.router.handleMouseUp(x, y, MouseButton.LEFT))
    }

    private fun openPopupAndResolveGeometry(fixture: Fixture): PopupGeometry {
        val visible = visibleRect(fixture.select)
        val x = visible.x + visible.width / 2
        val y = visible.y + visible.height / 2
        assertTrue(fixture.router.handleMouseDown(x, y, MouseButton.LEFT))
        assertTrue(fixture.router.handleMouseUp(x, y, MouseButton.LEFT))
        assertTrue(SelectRuntime.host.isOpenFor(fixture.ownerKey))

        SelectRuntime.engine.onFrame(
            measureContext = ctx,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            viewportScale = 1f,
        )
        val anchor =
            SelectRuntime.engine.debugAnchorRect(fixture.ownerKey)
                ?: error("Expected select anchor rect for owner=${fixture.ownerKey}")
        val panel =
            SelectRuntime.engine.debugPanelRect(fixture.ownerKey)
                ?: error("Expected select panel rect for owner=${fixture.ownerKey}")
        return PopupGeometry(anchor = anchor, panel = panel)
    }

    private fun createNonStickyFixture(): Fixture {
        val root = ContainerNode(key = "select-anchor-root")
        val host =
            ContainerNode(key = "select-anchor-host")
                .apply {
                    width = 220
                    height = 80
                    padding = Insets(0, 20, 0, 0)
                }.applyParent(root)

        val ownerKey = "select-anchor-target"
        val select =
            SelectNode(
                model = demoModel(),
                key = ownerKey,
            ).apply {
                width = 120
                height = 18
            }.applyParent(host)

        return buildFixture(root, host, select, ownerKey)
    }

    private fun createStickyFixture(): Fixture {
        val root = ContainerNode(key = "select-anchor-sticky-root")
        val scroller =
            ContainerNode(key = "select-anchor-sticky-scroller")
                .apply {
                    width = 220
                    height = 90
                    overflowY = Overflow.Auto
                }.applyParent(root)

        ContainerNode(key = "select-anchor-top-spacer")
            .apply {
                width = 220
                height = 22
            }.applyParent(scroller)

        val sticky =
            ContainerNode(key = "select-anchor-sticky-row")
                .apply {
                    width = 220
                    height = 24
                    padding = Insets(0, 20, 0, 0)
                    inlineStyleDeclarations =
                        styleDeclarations(
                            StyleProperty.POSITION to "sticky",
                            StyleProperty.TOP to "0px",
                        )
                }.applyParent(scroller)

        val ownerKey = "select-anchor-sticky-target"
        val select =
            SelectNode(
                model = demoModel(),
                key = ownerKey,
            ).apply {
                width = 120
                height = 18
            }.applyParent(sticky)

        ContainerNode(key = "select-anchor-filler")
            .apply {
                width = 220
                height = 300
            }.applyParent(scroller)

        return buildFixture(root, scroller, select, ownerKey)
    }

    private fun buildFixture(
        root: ContainerNode,
        scroller: ContainerNode,
        select: SelectNode,
        ownerKey: String,
    ): Fixture {
        val tree = DomTree(root)
        tree.render(ctx, viewportWidth, viewportHeight)
        tree.paint(ctx)
        val router = LayerDomInputRouter { root }
        return Fixture(
            tree = tree,
            scroller = scroller,
            select = select,
            ownerKey = ownerKey,
            router = router,
        )
    }

    private fun demoModel() =
        selectModel(id = "select.anchor.model") {
            option("a", "Alpha")
            option("b", "Beta")
            option("c", "Gamma")
        }

    private fun visibleRect(node: SelectNode): Rect {
        val geometry = UsedInteractionGeometryResolver.resolveNodeGeometry(node)
        return geometry.visibleBorderRect ?: geometry.usedBorderRect
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations =
        StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }

    private data class Fixture(
        val tree: DomTree,
        val scroller: ContainerNode,
        val select: SelectNode,
        val ownerKey: String,
        val router: LayerDomInputRouter,
    )

    private data class PopupGeometry(
        val anchor: Rect,
        val panel: Rect,
    )
}
