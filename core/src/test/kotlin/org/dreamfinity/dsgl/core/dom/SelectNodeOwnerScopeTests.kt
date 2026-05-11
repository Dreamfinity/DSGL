package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.SelectNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.overlay.input.LayerDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectPortalServices
import org.dreamfinity.dsgl.core.select.selectModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectNodeOwnerScopeTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        SelectPortalServices.closeAll()
    }

    @Test
    fun `system owner scope routes select popup to system engine`() {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 300, 200)
        val ownerKey = "system-select-owner"
        val select =
            SelectNode(
                model =
                    selectModel(id = "system.select.model") {
                        option("a", "Alpha")
                        option("b", "Beta")
                    },
                ownerScope = OverlayOwnerScope.System,
                key = ownerKey,
            ).apply {
                width = 120
                height = 20
                bounds = Rect(20, 20, 120, 20)
            }
        select.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 300, 200)
        tree.paint(ctx)
        val router = LayerDomInputRouter { root }
        val clickX = select.bounds.x + (select.bounds.width / 2).coerceAtLeast(1)
        val clickY = select.bounds.y + (select.bounds.height / 2).coerceAtLeast(1)

        assertTrue(router.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertTrue(router.handleMouseUp(clickX, clickY, MouseButton.LEFT))

        assertFalse(SelectPortalServices.applicationEngine.isOpenFor(ownerKey))
        assertTrue(SelectPortalServices.systemEngine.isOpenFor(ownerKey))
    }
}
