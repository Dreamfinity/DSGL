package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.SelectNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.portal.ApplicationPortalHost
import org.dreamfinity.dsgl.core.portal.DomainPortalServices
import org.dreamfinity.dsgl.core.portal.ScreenDomainId
import org.dreamfinity.dsgl.core.portal.handlePortalPointerAfterDom
import org.dreamfinity.dsgl.core.portal.input.SurfaceDomInputRouter
import org.dreamfinity.dsgl.core.portal.syncPortalFrame
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectStyle
import org.dreamfinity.dsgl.core.select.selectModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectNodeOwnerDomainTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        DomainPortalServices.closeAllSelects()
        DomainPortalServices.applicationSelectEngine.setStyle(SelectStyle())
        DomainPortalServices.systemSelectEngine.setStyle(SelectStyle())
        FocusManager.clearFocus()
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
                ownerDomain = ScreenDomainId.System,
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
        val router = SurfaceDomInputRouter { root }
        val clickX = select.bounds.x + (select.bounds.width / 2).coerceAtLeast(1)
        val clickY = select.bounds.y + (select.bounds.height / 2).coerceAtLeast(1)

        assertTrue(router.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertTrue(router.handleMouseUp(clickX, clickY, MouseButton.LEFT))

        assertFalse(DomainPortalServices.applicationSelectEngine.isOpenFor(ownerKey))
        assertTrue(DomainPortalServices.systemSelectEngine.isOpenFor(ownerKey))
    }

    @Test
    fun `anchor press reopens select while previous close animation is still active`() {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 300, 200)
        val ownerKey = "application-select-reopen-owner"
        DomainPortalServices.applicationSelectEngine.setStyle(
            SelectStyle(
                openDurationMs = 1L,
                closeDurationMs = 200L,
            ),
        )
        val select =
            SelectNode(
                model =
                    selectModel(id = "application.select.reopen.model") {
                        option("a", "Alpha")
                        option("b", "Beta")
                    },
                ownerDomain = ScreenDomainId.Application,
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
        val router = SurfaceDomInputRouter { root }
        val clickX = select.bounds.x + (select.bounds.width / 2).coerceAtLeast(1)
        val clickY = select.bounds.y + (select.bounds.height / 2).coerceAtLeast(1)

        assertTrue(router.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertTrue(DomainPortalServices.applicationSelectEngine.isOpenFor(ownerKey))
        Thread.sleep(3L)
        DomainPortalServices.applicationSelectEngine.onFrame(ctx, 300, 200, 1f)
        DomainPortalServices.closeSelect(ownerKey)
        assertTrue(DomainPortalServices.isSelectClosingFor(ownerKey))

        assertTrue(router.handleMouseDown(clickX, clickY, MouseButton.LEFT))

        assertTrue(DomainPortalServices.applicationSelectEngine.isOpenFor(ownerKey))
        assertFalse(DomainPortalServices.isSelectClosingFor(ownerKey))
    }

    @Test
    fun `application select portal pointer press preserves focused anchor`() {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 300, 160)
        val ownerKey = "application-select-focus-preserve-owner"
        DomainPortalServices.applicationSelectEngine.setStyle(
            SelectStyle(
                openDurationMs = 1L,
                closeDurationMs = 1L,
                maxPanelHeightPadding = 10,
            ),
        )
        val select =
            SelectNode(
                model =
                    selectModel(id = "application.select.focus.preserve.model") {
                        repeat(48) { index ->
                            option("id-$index", "Option $index")
                        }
                    },
                ownerDomain = ScreenDomainId.Application,
                key = ownerKey,
            ).apply {
                width = 120
                height = 20
                bounds = Rect(20, 20, 120, 20)
            }
        select.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 300, 160)
        tree.paint(ctx)
        val router = SurfaceDomInputRouter { root }
        val applicationPortalHost = ApplicationPortalHost()
        applicationPortalHost.onInputFrame(300, 160)
        val clickX = select.bounds.x + (select.bounds.width / 2).coerceAtLeast(1)
        val clickY = select.bounds.y + (select.bounds.height / 2).coerceAtLeast(1)

        assertTrue(router.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertTrue(FocusManager.isFocused(select))
        assertTrue(DomainPortalServices.applicationSelectEngine.isOpenFor(ownerKey))

        applicationPortalHost.syncPortalFrame(ctx, 300, 160, 1f, 0, 0)
        val track = requireNotNull(DomainPortalServices.applicationSelectEngine.debugScrollbarTrackRect(ownerKey))
        val downX = track.x + track.width / 2
        val downY = track.y + 2

        assertTrue(
            applicationPortalHost.handlePortalPointerAfterDom(
                mouseX = downX,
                mouseY = downY,
                dWheel = 0,
                button = MouseButton.LEFT,
                pressed = true,
            ),
        )

        assertTrue(FocusManager.isFocused(select))
        assertTrue(DomainPortalServices.applicationSelectEngine.isOpenFor(ownerKey))
        assertTrue(DomainPortalServices.applicationSelectEngine.isScrollbarDragging())
    }
}
