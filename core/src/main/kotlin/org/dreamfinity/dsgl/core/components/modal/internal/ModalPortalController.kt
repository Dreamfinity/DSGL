package org.dreamfinity.dsgl.core.components.modal.internal

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.components.modal.BackdropMode
import org.dreamfinity.dsgl.core.components.modal.ModalSpec
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.overlay.PortalBackdropPolicy
import org.dreamfinity.dsgl.core.overlay.PortalDismissPolicy
import org.dreamfinity.dsgl.core.overlay.PortalEntry
import org.dreamfinity.dsgl.core.overlay.PortalEntryBounds
import org.dreamfinity.dsgl.core.overlay.PortalEntryId
import org.dreamfinity.dsgl.core.overlay.PortalEntryOrder
import org.dreamfinity.dsgl.core.overlay.PortalEntryPlacement
import org.dreamfinity.dsgl.core.overlay.PortalEntryState
import org.dreamfinity.dsgl.core.overlay.PortalFocusPolicy
import org.dreamfinity.dsgl.core.overlay.PortalHost
import org.dreamfinity.dsgl.core.overlay.PortalInputPolicy
import org.dreamfinity.dsgl.core.overlay.PortalInsidePointerPolicy
import org.dreamfinity.dsgl.core.overlay.PortalPointerContainmentPolicy
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.overlay.evaluateOutsidePointerDown

internal class ModalPortalController {
    private val portalHost: PortalHost =
        PortalHost(ScreenDomainSurfaces.ApplicationPortal)
    private val entriesByPortalKey: LinkedHashMap<String, ModalPortalEntry> = LinkedHashMap()
    private var pendingPolicyPointerSequence: PendingPolicyPointerSequence? = null

    fun sync(rootNode: DOMNode, viewportWidth: Int, viewportHeight: Int) {
        val snapshots = ModalPortalSessionStore.portalSnapshots()
        val activePortalKeys = snapshots.mapTo(LinkedHashSet()) { it.portalKey }
        snapshots.forEach { snapshot ->
            val entry =
                entriesByPortalKey.getOrPut(snapshot.portalKey) {
                    ModalPortalEntry(snapshot.portalKey, snapshot.root).also(portalHost::register)
                }
            entry.reconcile(snapshot.root)
            entry.syncTopMost(snapshot.topMostModal)
            entry.syncActive(viewportWidth, viewportHeight)
        }
        entriesByPortalKey
            .keys
            .filter { it !in activePortalKeys }
            .forEach { portalKey ->
                val entry = entriesByPortalKey.remove(portalKey) ?: return@forEach
                portalHost.unregister(entry.state.id)
                entry.detach()
            }
        reconcileMountedRoots(rootNode)
    }

    fun close() {
        entriesByPortalKey.values.forEach { entry ->
            portalHost.unregister(entry.state.id)
            entry.detach()
        }
        entriesByPortalKey.clear()
        pendingPolicyPointerSequence = null
    }

    fun commitActivePortals() {
        portalHost
            .entriesInPaintOrder()
            .filterIsInstance<ModalPortalEntry>()
            .forEach { entry ->
                entry.syncProtectedDialogBounds()
                ModalPortalSessionStore.commitPortal(entry.portalKey, entry.root)
            }
    }

    fun hasActivePortal(): Boolean = entriesByPortalKey.values.any { entry -> entry.state.active }

    fun handlePointerPolicy(
        mouseX: Int,
        mouseY: Int,
        button: MouseButton,
        pressed: Boolean,
    ): Boolean {
        if (!pressed) {
            val pending = pendingPolicyPointerSequence ?: return false
            if (pending.button != button) return false
            pending.dismissEntry?.let { entry -> entry.state.dismiss(entry) }
            pendingPolicyPointerSequence = null
            return true
        }
        val result = portalHost.evaluateOutsidePointerDown(mouseX, mouseY) ?: return false
        if (result.consumed) {
            pendingPolicyPointerSequence =
                PendingPolicyPointerSequence(
                    button = button,
                    dismissEntry = result.entry.takeIf { result.shouldClose },
                )
        }
        return result.consumed
    }

    internal fun debugActivePortalEntryIds(): List<String> = portalHost.entriesInPaintOrder().map { it.state.id.value }

    internal fun debugEvaluatePointerDownPolicy(mouseX: Int, mouseY: Int) =
        portalHost.evaluateOutsidePointerDown(mouseX, mouseY)

    internal fun debugFindNodeByKey(key: Any?): DOMNode? = debugFindNode { node -> node.key == key }

    internal fun debugFindNode(predicate: (DOMNode) -> Boolean): DOMNode? =
        portalHost
            .entriesInPaintOrder()
            .mapNotNull { (it as? ModalPortalEntry)?.root }
            .firstNotNullOfOrNull { root -> findNode(root, predicate) }

    private fun reconcileMountedRoots(rootNode: DOMNode) {
        val activeRoots =
            portalHost
                .entriesInPaintOrder()
                .mapNotNull { (it as? ModalPortalEntry)?.root }
        entriesByPortalKey.values.forEach { entry ->
            if (entry.root !in activeRoots) {
                entry.detach()
            }
        }
        activeRoots.forEach { root ->
            if (root.parent !== rootNode) {
                root.parent
                    ?.children
                    ?.remove(root)
                root.parent = rootNode
            }
        }
        rootNode.children.removeAll(activeRoots.toSet())
        rootNode.children.addAll(activeRoots)
    }
}

private data class PendingPolicyPointerSequence(
    val button: MouseButton,
    val dismissEntry: PortalEntry?,
)

private class ModalPortalEntry(
    val portalKey: String,
    templateRoot: ModalPortalRootNode,
) : PortalEntry {
    private var tree: DomTree = DomTree(templateRoot)
    private var topMostModal: ModalSpec? = null

    val root: ModalPortalRootNode
        get() = tree.root as ModalPortalRootNode

    override val state: PortalEntryState =
        PortalEntryState(
            id = PortalEntryId("application.modal.$portalKey"),
            ownerToken = portalKey,
            surface = ScreenDomainSurfaces.ApplicationPortal,
            order = PortalEntryOrder(zIndex = -100),
            dismissPolicy = PortalDismissPolicy.None,
            inputPolicy = PortalInputPolicy.DomOnly,
            focusPolicy = PortalFocusPolicy.TrapFocus,
            backdropPolicy = PortalBackdropPolicy.ConsumeOutsidePointerDown,
            insidePointerPolicy = PortalInsidePointerPolicy.ConsumePointerDown,
            pointerContainmentPolicy = PortalPointerContainmentPolicy.ProtectedBoundsOnly,
        )
    override val node: DOMNode
        get() = root

    fun reconcile(templateRoot: ModalPortalRootNode) {
        val previousRoot = root
        val parent = root.parent
        val result = tree.reconcileWith(DomTree(templateRoot))
        tree.root = result.root
        EventBus.run {
            result.detachedRoots.forEach { detached -> detached.clearListenersDeep() }
        }
        if (previousRoot !== root) {
            previousRoot.parent
                ?.children
                ?.remove(previousRoot)
            previousRoot.parent = null
        }
        root.parent = parent
    }

    fun syncTopMost(spec: ModalSpec?) {
        topMostModal = spec
        state.dismissAction = {
            val topMost = topMostModal
            if (topMost?.backdrop == BackdropMode.True) {
                topMost.onHide?.invoke()
            }
        }
        state.dismissPolicy =
            if (spec?.backdrop == BackdropMode.True) {
                PortalDismissPolicy.OutsidePointerDown
            } else {
                PortalDismissPolicy.None
            }
        state.backdropPolicy =
            if (spec != null) {
                PortalBackdropPolicy.ConsumeOutsidePointerDown
            } else {
                PortalBackdropPolicy.None
            }
    }

    fun syncActive(viewportWidth: Int, viewportHeight: Int) {
        if (!ModalPortalSessionStore.shouldKeepPortalActive(portalKey)) {
            state.deactivate()
            return
        }
        state.activate(
            PortalEntryPlacement(
                anchorBounds = null,
                bounds =
                    PortalEntryBounds(
                        viewportBounds = Rect(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1)),
                        entryBounds = Rect(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1)),
                    ),
            ),
        )
    }

    fun syncProtectedDialogBounds() {
        val topMost =
            topMostModal ?: run {
                state.updateProtectedBounds(emptyList())
                return
            }
        val dialog = findNode(root) { node -> node.key == ModalPortalSessionStore.dialogKey(portalKey, topMost.key) }
        state.updateProtectedBounds(listOfNotNull(dialog?.bounds))
    }

    fun detach() {
        root.parent
            ?.children
            ?.remove(root)
        root.parent = null
    }

    override fun clearRefs() {
        tree.clearRefs()
        EventBus.run { root.clearListenersDeep() }
    }

    override fun close() {
        ModalPortalSessionStore.forgetPortal(portalKey)
        state.deactivate()
    }

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = false

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false
}

private fun findNode(root: DOMNode, predicate: (DOMNode) -> Boolean): DOMNode? {
    if (predicate(root)) return root
    root.children.forEach { child ->
        val found = findNode(child, predicate)
        if (found != null) {
            return found
        }
    }
    return null
}
