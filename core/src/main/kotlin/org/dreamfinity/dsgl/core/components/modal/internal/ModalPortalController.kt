package org.dreamfinity.dsgl.core.components.modal.internal

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.MouseButton
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
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurfaces

internal class ModalPortalController {
    private val portalHost: PortalHost =
        PortalHost(ScreenDomainSurfaces.ApplicationPortal)
    private val entriesByPortalKey: LinkedHashMap<String, ModalPortalEntry> = LinkedHashMap()

    fun sync(rootNode: DOMNode, viewportWidth: Int, viewportHeight: Int) {
        val snapshots = ModalPortalSessionStore.portalSnapshots()
        val activePortalKeys = snapshots.mapTo(LinkedHashSet()) { it.portalKey }
        snapshots.forEach { snapshot ->
            val entry =
                entriesByPortalKey.getOrPut(snapshot.portalKey) {
                    ModalPortalEntry(snapshot.portalKey, snapshot.root).also(portalHost::register)
                }
            entry.reconcile(snapshot.root)
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
    }

    fun commitActivePortals() {
        portalHost
            .entriesInPaintOrder()
            .mapNotNull { it as? ModalPortalEntry }
            .forEach { entry -> ModalPortalSessionStore.commitPortal(entry.portalKey, entry.root) }
    }

    fun hasActivePortal(): Boolean = entriesByPortalKey.values.any { entry -> entry.state.active }

    internal fun debugActivePortalEntryIds(): List<String> = portalHost.entriesInPaintOrder().map { it.state.id.value }

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
}

private class ModalPortalEntry(
    val portalKey: String,
    templateRoot: ModalPortalRootNode,
) : PortalEntry {
    private var tree: DomTree = DomTree(templateRoot)

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

    fun detach() {
        root.parent
            ?.children
            ?.remove(root)
        root.parent = null
    }

    override fun close() {
        ModalPortalSessionStore.forgetPortal(portalKey)
        state.deactivate()
    }

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = false

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false
}
