package org.dreamfinity.dsgl.core.components.modal

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.components.modal.internal.ModalPortalSessionStore
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.dsl.ui
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.overlay.ApplicationOverlayHost
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModalPortalLayoutRegressionTests {
    private val trees: MutableList<DomTree> = ArrayList()
    private val overlays: MutableList<ApplicationOverlayHost> = ArrayList()
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
        EventBus.run {
            trees.forEach { tree ->
                tree.clearRefs()
                tree.root.clearListenersDeep()
            }
        }
        overlays.forEach { overlay -> overlay.clearRefs() }
        hostKeys.forEach(ModalPortalSessionStore::forgetPortal)
        trees.clear()
        overlays.clear()
        hostKeys.clear()
    }

    @Test
    fun `modal portal resolves layer style before first overlay layout`() {
        val hostKey = "tests.modal.portal.layout.first.frame"
        val tree = buildTree(hostKey, listOf(basicModal()))
        trees += tree
        tree.render(measureContext, 320, 180)

        val overlay = ApplicationOverlayHost()
        overlays += overlay
        overlay.render(measureContext, 320, 180)

        val layer = overlay.modalPortal.debugFindNodeByKey("$hostKey.modal.modal.basic.layer")
        val dialog = overlay.modalPortal.debugFindNodeByKey(ModalPortalSessionStore.dialogKey(hostKey, "modal.basic"))
        assertNotNull(layer)
        assertNotNull(dialog)

        assertEquals(10, layer.padding.top)
        assertEquals(10, layer.padding.right)
        assertEquals(10, layer.padding.bottom)
        assertEquals(10, layer.padding.left)
        assertEquals(16, dialog.bounds.y)
    }

    @Test
    fun `centered modal keeps stable bounds across passive overlay frames`() {
        val hostKey = "tests.modal.portal.layout.centered.stable"
        val tree =
            buildTree(
                hostKey,
                listOf(
                    ModalSpec(
                        key = "modal.centered",
                        centered = true,
                    ) { _ -> },
                ),
            )
        trees += tree
        tree.render(measureContext, 320, 180)

        val overlay = ApplicationOverlayHost()
        overlays += overlay
        overlay.render(measureContext, 320, 180)

        val dialogKey = ModalPortalSessionStore.dialogKey(hostKey, "modal.centered")
        val firstDialog = overlay.modalPortal.debugFindNodeByKey(dialogKey)
        assertNotNull(firstDialog)
        val firstBounds = firstDialog.bounds

        assertTrue(overlay.handleMouseMove(firstBounds.x + firstBounds.width / 2, firstBounds.y + firstBounds.height / 2))
        overlay.render(measureContext, 320, 180)

        val secondDialog = overlay.modalPortal.debugFindNodeByKey(dialogKey)
        assertNotNull(secondDialog)
        assertEquals(firstBounds, secondDialog.bounds)
    }

    private fun buildTree(hostKey: String, modals: List<ModalSpec>): DomTree {
        hostKeys += hostKey
        return ui {
            modalPortal(modals = modals, key = hostKey) {
                div({ key = "$hostKey.content" })
            }
        }
    }

    private fun basicModal(): ModalSpec =
        ModalSpec(
            key = "modal.basic",
        ) { _ -> }
}
