package org.dreamfinity.dsgl.core.components.modal

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.components.modal.internal.ModalPortalSessionStore
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dsl.button
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.dsl.input
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.dsl.ui
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.host.DsglWindowHost
import org.dreamfinity.dsgl.core.host.Viewport
import org.dreamfinity.dsgl.core.overlay.ApplicationOverlayHost
import org.dreamfinity.dsgl.core.overlay.PortalPointerRegion
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModalPortalKeyboardRegressionTests {
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
        FocusManager.clearFocus()
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
    fun `escape is not cancelled after static modal closes`() {
        val hostKey = "tests.modal.portal.keyboard.regression"
        val current = buildTree(hostKey, emptyList())
        trees += current

        val withStatic = buildTree(hostKey, listOf(staticModal()))
        trees += withStatic
        current.reconcileWith(withStatic)

        val closed = buildTree(hostKey, emptyList())
        trees += closed
        current.reconcileWith(closed)

        FocusManager.clearFocus()
        val event = KeyboardKeyDownEvent('\u0000', KeyCodes.ESCAPE)
        EventBus.post(event)

        assertFalse(event.cancelled)
    }

    @Test
    fun `modal portal fills root viewport bounds`() {
        val tree = buildTree("tests.modal.portal.layout.viewport", emptyList())
        trees += tree

        tree.render(measureContext, 1920, 1080)

        val host =
            tree.root.children
                .firstOrNull()
        assertNotNull(host)
        assertEquals(0, host.bounds.x)
        assertEquals(0, host.bounds.y)
        assertEquals(1920, host.bounds.width)
        assertEquals(1080, host.bounds.height)

        val content = host.children.firstOrNull()
        assertNotNull(content)
        assertEquals(1920, content.bounds.width)
        assertEquals(1080, content.bounds.height)
    }

    @Test
    fun `modal layers mount through application overlay portal`() {
        val hostKey = "tests.modal.portal.portal"
        val tree = buildTree(hostKey, listOf(basicModal()))
        trees += tree
        tree.render(measureContext, 320, 180)

        val modalPortal =
            tree.root.children
                .firstOrNull()
        assertNotNull(modalPortal)
        assertEquals(listOf("$hostKey.content"), modalPortal.children.map { it.key })

        val overlay = ApplicationOverlayHost()
        overlays += overlay
        overlay.render(measureContext, 320, 180)

        assertEquals(listOf("application.modal.$hostKey"), overlay.modalPortal.debugActivePortalEntryIds())
    }

    @Test
    fun `modal portal blocks application root click through`() {
        val hostKey = "tests.modal.portal.portal.input"
        val tree = buildTree(hostKey, listOf(basicModal()))
        trees += tree
        tree.render(measureContext, 320, 180)

        val overlay = ApplicationOverlayHost()
        overlays += overlay
        overlay.render(measureContext, 320, 180)

        assertTrue(overlay.handleMouseDown(4, 4, MouseButton.LEFT))
    }

    @Test
    fun `modal portal generic policy classifies dialog as inside and backdrop as outside`() {
        val hostKey = "tests.modal.portal.portal.policy"
        val tree = buildTree(hostKey, listOf(dismissibleBodyModal {}))
        trees += tree
        tree.render(measureContext, 320, 180)

        val overlay = ApplicationOverlayHost()
        overlays += overlay
        overlay.render(measureContext, 320, 180)

        val dialog = overlay.modalPortal.debugFindNodeByKey(ModalPortalSessionStore.dialogKey(hostKey, "modal.dismissible"))
        assertNotNull(dialog)
        val inside =
            overlay.modalPortal.debugEvaluatePointerDownPolicy(
                mouseX = dialog.bounds.x + dialog.bounds.width / 2,
                mouseY = dialog.bounds.y + dialog.bounds.height / 2,
            )
        val outside = overlay.modalPortal.debugEvaluatePointerDownPolicy(mouseX = 2, mouseY = 2)

        assertNotNull(inside)
        assertEquals(PortalPointerRegion.InsideEntry, inside.region)
        assertTrue(inside.consumed)
        assertFalse(inside.shouldClose)
        assertNotNull(outside)
        assertEquals(PortalPointerRegion.OutsideEntry, outside.region)
        assertTrue(outside.consumed)
        assertTrue(outside.shouldClose)
    }

    @Test
    fun `modal portal policy blocks application root fallthrough for non interactive dialog body`() {
        val hostKey = "tests.modal.portal.portal.policy.inside"
        val tree = buildTree(hostKey, listOf(dismissibleBodyModal {}))
        trees += tree
        tree.render(measureContext, 320, 180)

        val overlay = ApplicationOverlayHost()
        overlays += overlay
        overlay.render(measureContext, 320, 180)

        val dialog = overlay.modalPortal.debugFindNodeByKey(ModalPortalSessionStore.dialogKey(hostKey, "modal.dismissible"))
        assertNotNull(dialog)
        var applicationRootReceived = false
        val consumedBy =
            dispatchApplicationPortalPointer(
                overlay = overlay,
                mouseX = dialog.bounds.x + dialog.bounds.width / 2,
                mouseY = dialog.bounds.y + dialog.bounds.height / 2,
                pressed = true,
            ) {
                applicationRootReceived = true
                true
            }

        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumedBy)
        assertFalse(applicationRootReceived)
    }

    @Test
    fun `static modal backdrop consumes without dismissing or falling through`() {
        val hostKey = "tests.modal.portal.portal.policy.static"
        var hideCount = 0
        val tree =
            buildTree(
                hostKey,
                listOf(
                    ModalSpec(
                        key = "modal.static.backdrop",
                        backdrop = BackdropMode.Static,
                        onHide = { hideCount += 1 },
                    ) { _ -> },
                ),
            )
        trees += tree
        tree.render(measureContext, 320, 180)

        val overlay = ApplicationOverlayHost()
        overlays += overlay
        overlay.render(measureContext, 320, 180)

        var applicationRootReceived = false
        val consumedBy =
            dispatchApplicationPortalPointer(
                overlay = overlay,
                mouseX = 2,
                mouseY = 2,
                pressed = true,
            ) {
                applicationRootReceived = true
                true
            }

        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumedBy)
        assertFalse(applicationRootReceived)
        assertEquals(0, hideCount)
    }

    @Test
    fun `modal backdrop dismiss consumes full pointer sequence and does not click through`() {
        val hostKey = "tests.modal.portal.portal.policy.full.click"
        var modals: List<ModalSpec> = emptyList()
        modals = listOf(dismissibleBodyModal { modals = emptyList() })
        var tree = buildTree(hostKey, modals)
        trees += tree
        val overlay = ApplicationOverlayHost()
        overlays += overlay
        renderTreeAndOverlay(tree, overlay)

        var applicationRootReceivedDown = false
        val consumedDown =
            dispatchApplicationPortalPointer(
                overlay = overlay,
                mouseX = 2,
                mouseY = 2,
                pressed = true,
            ) {
                applicationRootReceivedDown = true
                true
            }

        assertEquals(listOf("modal.dismissible"), modals.map { it.key })
        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumedDown)
        assertFalse(applicationRootReceivedDown)

        var applicationRootReceivedUp = false
        val consumedUp =
            dispatchApplicationPortalPointer(
                overlay = overlay,
                mouseX = 2,
                mouseY = 2,
                pressed = false,
            ) {
                applicationRootReceivedUp = true
                true
            }

        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumedUp)
        assertFalse(applicationRootReceivedUp)
        assertEquals(emptyList(), modals)

        tree = reconcileTree(tree, buildTree(hostKey, modals))
        renderTreeAndOverlay(tree, overlay)
    }

    @Test
    fun `modal portal does not dismiss non static modal when clicking inside dialog body`() {
        val hostKey = "tests.modal.portal.portal.inside.dismiss"
        var hideCount = 0
        val tree = buildTree(hostKey, listOf(dismissibleBodyModal { hideCount += 1 }))
        trees += tree
        tree.render(measureContext, 320, 180)

        val overlay = ApplicationOverlayHost()
        overlays += overlay
        overlay.render(measureContext, 320, 180)

        val dialog = overlay.modalPortal.debugFindNodeByKey(ModalPortalSessionStore.dialogKey(hostKey, "modal.dismissible"))
        assertNotNull(dialog)
        val clickX = dialog.bounds.x + dialog.bounds.width / 2
        val clickY = dialog.bounds.y + dialog.bounds.height / 2

        assertTrue(overlay.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertTrue(overlay.handleMouseUp(clickX, clickY, MouseButton.LEFT))
        assertEquals(0, hideCount)
    }

    @Test
    fun `modal portal dismisses non static modal when clicking backdrop`() {
        val hostKey = "tests.modal.portal.portal.backdrop.dismiss"
        var hideCount = 0
        val tree = buildTree(hostKey, listOf(dismissibleBodyModal { hideCount += 1 }))
        trees += tree
        tree.render(measureContext, 320, 180)

        val overlay = ApplicationOverlayHost()
        overlays += overlay
        overlay.render(measureContext, 320, 180)

        assertTrue(overlay.handleMouseDown(2, 2, MouseButton.LEFT))
        assertTrue(overlay.handleMouseUp(2, 2, MouseButton.LEFT))
        assertEquals(1, hideCount)
    }

    @Test
    fun `modal portal keeps topmost focus request on overlay commit`() {
        val hostKey = "tests.modal.portal.portal.focus"
        val current = buildTreeWithContentInput(hostKey, emptyList())
        trees += current
        current.render(measureContext, 320, 180)
        FocusManager.requestFocus(requireNodeByKey(current.root, "$hostKey.content.input"))

        val withModal = buildTreeWithContentInput(hostKey, listOf(inputModal()))
        trees += withModal
        current.reconcileWith(withModal)
        current.render(measureContext, 320, 180)

        val overlay = ApplicationOverlayHost()
        overlays += overlay
        overlay.render(measureContext, 320, 180)

        assertEquals("modal.input", FocusManager.focusedNode()?.key)
    }

    @Test
    fun `modal portal keeps underlying modal pointer interactive after top modal closes`() {
        val hostKey = "tests.modal.portal.portal.stack.pointer"
        var stepOneClicks = 0
        val stepOne = clickableModal("step.one", "step.one.button") { stepOneClicks += 1 }
        val stepTwo = clickableModal("step.two", "step.two.button") {}
        val current = buildTree(hostKey, listOf(stepOne))
        trees += current
        current.render(measureContext, 320, 180)

        val overlay = ApplicationOverlayHost()
        overlays += overlay
        overlay.render(measureContext, 320, 180)

        val stacked = buildTree(hostKey, listOf(stepOne, stepTwo))
        trees += stacked
        current.reconcileWith(stacked)
        current.render(measureContext, 320, 180)
        overlay.render(measureContext, 320, 180)

        val popped = buildTree(hostKey, listOf(stepOne))
        trees += popped
        current.reconcileWith(popped)
        current.render(measureContext, 320, 180)
        overlay.render(measureContext, 320, 180)

        val stepOneButton = overlay.modalPortal.debugFindNodeByKey("step.one.button")
        assertNotNull(stepOneButton)
        val clickX = stepOneButton.bounds.x + stepOneButton.bounds.width / 2
        val clickY = stepOneButton.bounds.y + stepOneButton.bounds.height / 2

        assertTrue(overlay.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertTrue(overlay.handleMouseUp(clickX, clickY, MouseButton.LEFT))
        assertEquals(1, stepOneClicks)
    }

    @Test
    fun `modal portal restores showcase flow modal pointer interaction after closing step two`() {
        val hostKey = "tests.modal.portal.portal.showcase.flow"
        var modals: List<ModalSpec> = emptyList()

        fun removeModal(key: String) {
            modals = modals.filterNot { modal -> modal.key == key }
        }

        fun pushModal(modal: ModalSpec) {
            modals += modal
        }
        pushModal(showcaseFlowStepOne(::pushModal, ::removeModal))

        var tree = buildTree(hostKey, modals)
        trees += tree
        val overlay = ApplicationOverlayHost()
        overlays += overlay
        renderTreeAndOverlay(tree, overlay)

        clickOverlayButton(overlay, "Next")
        assertEquals(listOf("modal.flow.1", "modal.flow.2"), modals.map { it.key })
        tree = reconcileTree(tree, buildTree(hostKey, modals))
        renderTreeAndOverlay(tree, overlay)

        clickOverlayButton(overlay, "Back to Step 1")
        assertEquals(listOf("modal.flow.1"), modals.map { it.key })
        tree = reconcileTree(tree, buildTree(hostKey, modals))
        renderTreeAndOverlay(tree, overlay)

        clickOverlayButton(overlay, "Next")
        assertEquals(listOf("modal.flow.1", "modal.flow.2"), modals.map { it.key })
    }

    @Test
    fun `modal portal restores showcase flow pointer interaction after closing step two header button`() {
        val hostKey = "tests.modal.portal.portal.showcase.flow.header"
        var modals: List<ModalSpec> = emptyList()

        fun removeModal(key: String) {
            modals = modals.filterNot { modal -> modal.key == key }
        }

        fun pushModal(modal: ModalSpec) {
            modals += modal
        }
        pushModal(showcaseFlowStepOne(::pushModal, ::removeModal))

        var tree = buildTree(hostKey, modals)
        trees += tree
        val overlay = ApplicationOverlayHost()
        overlays += overlay
        renderTreeAndOverlay(tree, overlay)

        clickOverlayButton(overlay, "Next")
        tree = reconcileTree(tree, buildTree(hostKey, modals))
        renderTreeAndOverlay(tree, overlay)

        clickOverlayButtonInDialog(
            overlay = overlay,
            text = "x",
            dialogKey = ModalPortalSessionStore.dialogKey(hostKey, "modal.flow.2"),
        )
        assertEquals(listOf("modal.flow.1"), modals.map { it.key })
        tree = reconcileTree(tree, buildTree(hostKey, modals))
        renderTreeAndOverlay(tree, overlay)

        clickOverlayButton(overlay, "Next")
        assertEquals(listOf("modal.flow.1", "modal.flow.2"), modals.map { it.key })
    }

    @Test
    fun `modal portal restores hook state showcase flow pointer interaction after closing step two`() {
        val window = ShowcaseFlowWindow()
        val host = RecordingHost(window)
        window.attachHost(host)
        var tree = renderWithHookSession(window)
        trees += tree
        val overlay = ApplicationOverlayHost()
        overlays += overlay
        renderTreeAndOverlay(tree, overlay)

        clickTreeNode(tree, "open.flow")
        assertTrue(host.rebuildRequests > 0)
        tree = reconcileTree(tree, renderWithHookSession(window))
        renderTreeAndOverlay(tree, overlay)

        clickOverlayButton(overlay, "Next")
        tree = reconcileTree(tree, renderWithHookSession(window))
        renderTreeAndOverlay(tree, overlay)

        clickOverlayButton(overlay, "Back to Step 1")
        tree = reconcileTree(tree, renderWithHookSession(window))
        renderTreeAndOverlay(tree, overlay)

        clickOverlayButton(overlay, "Next")
        tree = reconcileTree(tree, renderWithHookSession(window))
        renderTreeAndOverlay(tree, overlay)

        assertEquals(listOf("modal.flow.1", "modal.flow.2"), window.lastRenderedModalKeys)
    }

    private fun buildTree(hostKey: String, modals: List<ModalSpec>): DomTree {
        hostKeys += hostKey
        return ui {
            modalPortal(modals = modals, key = hostKey) {
                div({ key = "$hostKey.content" })
            }
        }
    }

    private fun buildTreeWithContentInput(hostKey: String, modals: List<ModalSpec>): DomTree {
        hostKeys += hostKey
        return ui {
            modalPortal(modals = modals, key = hostKey) {
                input(InputType.Text(value = ""), {
                    key = "$hostKey.content.input"
                })
            }
        }
    }

    private fun staticModal(): ModalSpec =
        ModalSpec(
            key = "modal.static",
            backdrop = BackdropMode.Static,
            keyboard = false,
        ) { _ -> }

    private fun basicModal(): ModalSpec =
        ModalSpec(
            key = "modal.basic",
        ) { _ -> }

    private fun inputModal(): ModalSpec =
        ModalSpec(
            key = "modal.input",
        ) { _ ->
            input(InputType.Text(value = ""), {
                key = "modal.input"
            })
        }

    private fun dismissibleBodyModal(onHide: () -> Unit): ModalSpec =
        ModalSpec(
            key = "modal.dismissible",
            centered = true,
            onHide = onHide,
        ) { _ ->
            modalBody {
                text("Clicking this non-interactive body area must not dismiss the modal.")
            }
        }

    private fun clickableModal(modalKey: String, buttonKey: String, onClick: () -> Unit): ModalSpec =
        ModalSpec(key = modalKey) { _ ->
            modalBody {
                button("Click", {
                    key = buttonKey
                    onMouseClick = { onClick() }
                })
            }
        }

    private fun showcaseFlowStepOne(onPushModal: (ModalSpec) -> Unit, onRemoveModal: (String) -> Unit): ModalSpec =
        ModalSpec(
            key = "modal.flow.1",
            onHide = { onRemoveModal("modal.flow.1") },
        ) { scope ->
            modalHeader(closeButton = true, onHide = scope.dismiss) {
                modalTitle("Flow Step 1")
            }
            modalBody {
                text("Step 1 remains visible but inert when Step 2 is pushed.")
            }
            modalFooter {
                button("Close", {
                    onMouseClick = { scope.dismiss?.invoke() }
                })
                button("Next", {
                    onMouseClick = {
                        onPushModal(showcaseFlowStepTwo(onRemoveModal))
                    }
                })
            }
        }

    private fun showcaseFlowStepTwo(onRemoveModal: (String) -> Unit): ModalSpec =
        ModalSpec(
            key = "modal.flow.2",
            centered = true,
            onHide = { onRemoveModal("modal.flow.2") },
        ) { scope ->
            modalHeader(closeButton = true, onHide = scope.dismiss) {
                modalTitle("Flow Step 2")
            }
            modalBody {
                text("Topmost modal only. Closing returns interaction to Step 1.")
            }
            modalFooter {
                button("Back to Step 1", {
                    onMouseClick = { scope.dismiss?.invoke() }
                })
            }
        }

    private fun renderTreeAndOverlay(tree: DomTree, overlay: ApplicationOverlayHost) {
        tree.render(measureContext, 320, 180)
        overlay.render(measureContext, 320, 180)
    }

    private fun reconcileTree(current: DomTree, next: DomTree): DomTree {
        trees += next
        current.reconcileWith(next)
        return current
    }

    private fun clickOverlayButton(overlay: ApplicationOverlayHost, text: String) {
        val button =
            overlay.modalPortal.debugFindNode { node ->
                node is ButtonNode && node.text == text
            }
        assertNotNull(button)
        val clickX = button.bounds.x + button.bounds.width / 2
        val clickY = button.bounds.y + button.bounds.height / 2
        assertTrue(overlay.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertTrue(overlay.handleMouseUp(clickX, clickY, MouseButton.LEFT))
    }

    private fun clickOverlayButtonInDialog(overlay: ApplicationOverlayHost, text: String, dialogKey: String) {
        val button =
            overlay.modalPortal.debugFindNode { node ->
                node is ButtonNode && node.text == text && hasAncestorWithKey(node, dialogKey)
            }
        assertNotNull(button)
        val clickX = button.bounds.x + button.bounds.width / 2
        val clickY = button.bounds.y + button.bounds.height / 2
        assertTrue(overlay.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertTrue(overlay.handleMouseUp(clickX, clickY, MouseButton.LEFT))
    }

    private fun hasAncestorWithKey(node: DOMNode, key: Any?): Boolean {
        var current: DOMNode? = node
        while (current != null) {
            if (current.key == key) return true
            current = current.parent
        }
        return false
    }

    private fun renderWithHookSession(window: DsglWindow): DomTree {
        window.beginRenderBuild()
        return try {
            window.render()
        } finally {
            window.endRenderBuild()
            window.commitRenderBuild()
        }
    }

    private inner class ShowcaseFlowWindow : DsglWindow() {
        var lastRenderedModalKeys: List<String> = emptyList()

        override fun render(): DomTree =
            ui {
                var modals by useState(emptyList<ModalSpec>())
                lastRenderedModalKeys = modals.map { it.key }

                fun removeModal(key: String) {
                    modals = modals.filterNot { modal -> modal.key == key }
                }

                fun pushModal(modal: ModalSpec) {
                    modals += modal
                }
                modalPortal(modals = modals, key = "tests.modal.portal.portal.hook.showcase") {
                    button("Open flow step 1", {
                        key = "open.flow"
                        onMouseClick = { pushModal(showcaseFlowStepOne(::pushModal, ::removeModal)) }
                    })
                }
            }
    }

    private class RecordingHost(
        override val window: DsglWindow,
    ) : DsglWindowHost {
        var rebuildRequests: Int = 0

        override fun requestRebuild(reason: String?) {
            rebuildRequests += 1
        }

        override fun requestRedraw() = Unit

        override fun getViewport(): Viewport = Viewport(width = 320, height = 180)
    }

    private fun clickTreeNode(tree: DomTree, key: Any) {
        val node = requireNodeByKey(tree.root, key)
        val clickX = node.bounds.x + node.bounds.width / 2
        val clickY = node.bounds.y + node.bounds.height / 2
        EventBus.post(
            MouseClickEvent(clickX, clickY, MouseButton.LEFT).also { event ->
                event.target = node
            },
        )
    }

    private fun dispatchApplicationPortalPointer(
        overlay: ApplicationOverlayHost,
        mouseX: Int,
        mouseY: Int,
        pressed: Boolean,
        applicationRootHandler: () -> Boolean,
    ) = ScreenDomainSurfaces.firstInputConsumer(
        canConsume = { surface ->
            when (surface) {
                ScreenDomainSurfaces.ApplicationPortal ->
                    if (pressed) {
                        overlay.handleMouseDown(mouseX, mouseY, MouseButton.LEFT)
                    } else {
                        overlay.handleMouseUp(mouseX, mouseY, MouseButton.LEFT)
                    }

                ScreenDomainSurfaces.ApplicationRoot -> applicationRootHandler()
                else -> false
            }
        },
    )

    private fun requireNodeByKey(root: DOMNode, key: Any): DOMNode {
        if (root.key == key) return root
        root.children.forEach { child ->
            val found = runCatching { requireNodeByKey(child, key) }.getOrNull()
            if (found != null) return found
        }
        error("Missing node key=$key")
    }
}
