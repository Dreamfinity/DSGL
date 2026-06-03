package org.dreamfinity.dsgl.core.components.modal

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.components.modal.internal.ModalPortalSessionStore
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dsl.button
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.dsl.ui
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.portal.ApplicationPortalHost
import org.dreamfinity.dsgl.core.portal.hasActiveModalPortal
import org.dreamfinity.dsgl.core.portal.isFloatingWindowDemoOpen
import org.dreamfinity.dsgl.core.portal.toggleFloatingWindowDemo
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModalPortalPointerRegressionTests {
    private val trees: MutableList<DomTree> = ArrayList()
    private val portalHosts: MutableList<ApplicationPortalHost> = ArrayList()
    private val hostKeys: MutableSet<String> = LinkedHashSet()
    private val measureContext =
        object : UiMeasureContext {
            override fun measureText(text: String): Int = text.length * 6

            override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * 6

            override val fontHeight: Int = 9

            override fun fontHeight(fontId: String?, fontSize: Int?): Int = 9

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
        EventBus.run {
            trees.forEach { tree ->
                tree.clearRefs()
                tree.root.clearListenersDeep()
            }
        }
        portalHosts.forEach { portalHost -> portalHost.clearRefs() }
        hostKeys.forEach(ModalPortalSessionStore::forgetPortal)
        trees.clear()
        portalHosts.clear()
        hostKeys.clear()
    }

    @Test
    fun `active modal prevents application floating window from opening`() {
        val hostKey = "tests.modal.portal.floating.blocked"
        val portalHost = ApplicationPortalHost()
        portalHosts += portalHost
        val tree = buildTree(hostKey, listOf(staticModal()))
        trees += tree

        tree.render(measureContext, 320, 180)
        portalHost.render(measureContext, 320, 180)
        assertTrue(portalHost.hasActiveModalPortal())

        portalHost.toggleFloatingWindowDemo(anchorX = 24, anchorY = 24)
        portalHost.render(measureContext, 320, 180)

        assertFalse(portalHost.isFloatingWindowDemoOpen())
        assertTrue(
            portalHost.floatingWindowPortal
                .debugNode()
                .parent == null,
        )
    }

    @Test
    fun `static modal backdrop pointer press does not activate modal layer`() {
        val hostKey = "tests.modal.portal.static.backdrop.active"
        val portalHost = renderStaticModalPortal(hostKey)
        val layer = portalHost.modalPortal.debugFindNodeByKey("$hostKey.modal.modal.static.layer")
        assertNotNull(layer)

        assertTrue(portalHost.handleMouseDown(4, 4, MouseButton.LEFT))
        assertFalse(layer.styleActive)
        assertTrue(portalHost.handleMouseUp(4, 4, MouseButton.LEFT))
        assertFalse(layer.styleActive)
    }

    @Test
    fun `static modal dialog pointer press does not activate modal layer`() {
        val hostKey = "tests.modal.portal.static.dialog.active"
        val portalHost = renderStaticModalPortal(hostKey)
        val layer = portalHost.modalPortal.debugFindNodeByKey("$hostKey.modal.modal.static.layer")
        val dialog = portalHost.modalPortal.debugFindNodeByKey(ModalPortalSessionStore.dialogKey(hostKey, "modal.static"))
        assertNotNull(layer)
        assertNotNull(dialog)
        val clickX = dialog.bounds.x + dialog.bounds.width / 2
        val clickY = dialog.bounds.y + dialog.bounds.height / 2

        assertTrue(portalHost.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertFalse(layer.styleActive)
        assertTrue(portalHost.handleMouseUp(clickX, clickY, MouseButton.LEFT))
        assertFalse(layer.styleActive)
    }

    @Test
    fun `static modal dialog control receives pointer through modal owned routing`() {
        val hostKey = "tests.modal.portal.static.dialog.button"
        var clicks = 0
        val tree =
            buildTree(
                hostKey = hostKey,
                modals =
                    listOf(
                        staticModal {
                            button("OK", {
                                key = "$hostKey.modal.button"
                                onMouseClick = { clicks += 1 }
                            })
                        },
                    ),
            )
        trees += tree
        tree.render(measureContext, 320, 180)
        val portalHost =
            ApplicationPortalHost().also { portalHost ->
                portalHosts += portalHost
                portalHost.render(measureContext, 320, 180)
            }
        val button = portalHost.modalPortal.debugFindNodeByKey("$hostKey.modal.button")
        assertNotNull(button)
        val clickX = button.bounds.x + button.bounds.width / 2
        val clickY = button.bounds.y + button.bounds.height / 2

        assertTrue(portalHost.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertTrue(portalHost.handleMouseUp(clickX, clickY, MouseButton.LEFT))

        assertTrue(clicks == 1)
    }

    @Test
    fun `static modal clears dialog hover when pointer moves to backdrop`() {
        val hostKey = "tests.modal.portal.static.dialog.hover.clear"
        val portalHost = renderStaticModalWithButton(hostKey)
        val button = portalHost.modalPortal.debugFindNodeByKey("$hostKey.modal.button")
        assertNotNull(button)
        val hoverX = button.bounds.x + button.bounds.width / 2
        val hoverY = button.bounds.y + button.bounds.height / 2

        assertTrue(portalHost.handleMouseMove(hoverX, hoverY))
        assertTrue(button.styleHovered)

        assertTrue(portalHost.handleMouseMove(4, 4))
        assertFalse(button.styleHovered)
        assertFalse(button.styleActive)
    }

    @Test
    fun `static modal backdrop move does not activate modal nodes`() {
        val hostKey = "tests.modal.portal.static.backdrop.move"
        val portalHost = renderStaticModalWithButton(hostKey)
        val layer = portalHost.modalPortal.debugFindNodeByKey("$hostKey.modal.modal.static.layer")
        val dialog = portalHost.modalPortal.debugFindNodeByKey(ModalPortalSessionStore.dialogKey(hostKey, "modal.static"))
        val button = portalHost.modalPortal.debugFindNodeByKey("$hostKey.modal.button")
        assertNotNull(layer)
        assertNotNull(dialog)
        assertNotNull(button)

        assertTrue(portalHost.handleMouseMove(4, 4))

        assertFalse(layer.styleActive)
        assertFalse(dialog.styleActive)
        assertFalse(button.styleActive)
        assertFalse(button.styleHovered)
    }

    @Test
    fun `active modal mouse move clears stale application portal hover`() {
        val hostKey = "tests.modal.portal.static.lower.hover.clear"
        val portalHost = ApplicationPortalHost()
        portalHosts += portalHost
        portalHost.rootNode.setViewportBounds(320, 180)
        val lowerButton =
            ButtonNode("Lower", key = "$hostKey.lower.button").apply {
                bounds = Rect(0, 0, 80, 24)
            }
        lowerButton.applyParent(portalHost.rootNode)

        assertTrue(portalHost.handleMouseMove(12, 12))
        assertTrue(lowerButton.styleHovered)

        val tree = buildTree(hostKey, listOf(staticModal()))
        trees += tree
        tree.render(measureContext, 320, 180)
        portalHost.render(measureContext, 320, 180)

        assertTrue(portalHost.handleMouseMove(4, 4))
        assertFalse(lowerButton.styleHovered)
    }

    private fun renderStaticModalPortal(hostKey: String): ApplicationPortalHost {
        val tree = buildTree(hostKey, listOf(staticModal()))
        trees += tree
        tree.render(measureContext, 320, 180)
        return ApplicationPortalHost().also { portalHost ->
            portalHosts += portalHost
            portalHost.render(measureContext, 320, 180)
        }
    }

    private fun renderStaticModalWithButton(hostKey: String): ApplicationPortalHost {
        val tree =
            buildTree(
                hostKey = hostKey,
                modals =
                    listOf(
                        staticModal {
                            button("OK", {
                                key = "$hostKey.modal.button"
                                onMouseClick = {}
                            })
                        },
                    ),
            )
        trees += tree
        tree.render(measureContext, 320, 180)
        return ApplicationPortalHost().also { portalHost ->
            portalHosts += portalHost
            portalHost.render(measureContext, 320, 180)
        }
    }

    private fun buildTree(hostKey: String, modals: List<ModalSpec>): DomTree {
        hostKeys += hostKey
        return ui {
            modalPortal(modals = modals, key = hostKey) {
                div({ key = "$hostKey.content" })
            }
        }
    }

    private fun staticModal(content: UiScope.() -> Unit = {}): ModalSpec =
        ModalSpec(
            key = "modal.static",
            backdrop = BackdropMode.Static,
            keyboard = false,
        ) { _ ->
            text("Static")
            content()
        }
}
