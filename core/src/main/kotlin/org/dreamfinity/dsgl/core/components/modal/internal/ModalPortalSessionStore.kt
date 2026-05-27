package org.dreamfinity.dsgl.core.components.modal.internal

import org.dreamfinity.dsgl.core.components.modal.ModalSpec
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget
import java.util.concurrent.ConcurrentHashMap

internal fun modalLifecycleKey(portalKey: String): String = "$portalKey.modal.lifecycle"

private data class ModalMeta(
    val restoreFocus: Boolean,
)

private class ModalPortalState {
    var previousKeys: List<String> = emptyList()
    var previousMetaByKey: Map<String, ModalMeta> = emptyMap()
    val restoreFocusByModalKey: MutableMap<String, Any?> = linkedMapOf()
    var pendingRestoreFocusKey: Any? = null
    var pendingFocusDialogKey: String? = null
    var currentModals: List<ModalSpec> = emptyList()
}

internal object ModalPortalSessionStore {
    data class PortalSnapshot(
        val portalKey: String,
        val root: ModalPortalRootNode,
        val topMostModal: ModalSpec?,
    )

    private val states: MutableMap<String, ModalPortalState> = ConcurrentHashMap()
    private val portalTemplates: MutableMap<String, ModalPortalRootNode> = ConcurrentHashMap()
    private val portalHostRefs: MutableMap<String, RefTarget<ElementHandle>> = ConcurrentHashMap()

    fun onBuild(portalKey: String, modals: List<ModalSpec>) {
        val state = states.getOrPut(portalKey) { ModalPortalState() }
        val currentKeys = modals.map { it.key }
        val previousKeys = state.previousKeys

        val openedKeys = currentKeys.filter { it !in previousKeys }
        openedKeys.forEach { openedKey ->
            state.restoreFocusByModalKey[openedKey] = FocusManager.focusedNode()?.key
        }

        val currentKeySet = currentKeys.toHashSet()
        val closedKeys = previousKeys.filter { it !in currentKeySet }.toHashSet()
        if (closedKeys.isNotEmpty()) {
            previousKeys.asReversed().forEach { closedKey ->
                if (closedKey !in closedKeys) return@forEach
                val previousMeta = state.previousMetaByKey[closedKey]
                val restoreKey = state.restoreFocusByModalKey.remove(closedKey)
                if (previousMeta?.restoreFocus == true && restoreKey != null) {
                    state.pendingRestoreFocusKey = restoreKey
                    return@forEach
                }
            }
            closedKeys.forEach { key ->
                state.restoreFocusByModalKey.remove(key)
            }
        }

        val previousTop = previousKeys.lastOrNull()
        val currentTop = currentKeys.lastOrNull()
        if (currentTop != null && currentTop != previousTop) {
            state.pendingFocusDialogKey = dialogKey(portalKey, currentTop)
        }
        if (currentTop == null) {
            state.pendingFocusDialogKey = null
        }

        state.previousKeys = currentKeys
        state.currentModals = modals
        state.previousMetaByKey =
            modals.associate { spec ->
                spec.key to ModalMeta(restoreFocus = spec.restoreFocus)
            }
    }

    fun onCommit(portalKey: String, modals: List<ModalSpec>, focusRoot: DOMNode? = null) {
        val state = states[portalKey] ?: return
        val topMost = modals.lastOrNull()

        if (topMost == null) {
            if (commitWithoutActiveModal(state, focusRoot)) {
                states.remove(portalKey)
            }
            return
        }

        commitWithActiveModal(portalKey, state, topMost, focusRoot)
    }

    fun registerPortalTemplate(portalKey: String, root: ModalPortalRootNode) {
        val previous = portalTemplates.put(portalKey, root)
        if (previous != null && previous !== root && previous.parent == null) {
            clearTemplateOwnedListeners(previous)
        }
    }

    fun portalHostRef(portalKey: String): RefTarget<ElementHandle> =
        portalHostRefs.getOrPut(portalKey) {
            RefTarget { handle ->
                if (handle == null) {
                    forgetPortal(portalKey)
                }
            }
        }

    fun commitPortal(portalKey: String, focusRoot: DOMNode) {
        val state = states[portalKey] ?: return
        onCommit(portalKey, state.currentModals, focusRoot)
    }

    fun portalSnapshots(): List<PortalSnapshot> =
        portalTemplates
            .entries
            .sortedBy { it.key }
            .map { (portalKey, root) ->
                PortalSnapshot(
                    portalKey = portalKey,
                    root = root,
                    topMostModal = states[portalKey]?.currentModals?.lastOrNull(),
                )
            }

    fun shouldKeepPortalActive(portalKey: String): Boolean {
        val template = portalTemplates[portalKey] ?: return false
        val state = states[portalKey]
        return template.children.any { it.key != modalLifecycleKey(portalKey) } ||
            state?.previousKeys?.isNotEmpty() == true ||
            state?.pendingRestoreFocusKey != null ||
            state?.pendingFocusDialogKey != null
    }

    fun forgetPortal(portalKey: String) {
        portalTemplates.remove(portalKey)
        portalHostRefs.remove(portalKey)
        states.remove(portalKey)
    }

    fun dialogKey(portalKey: String, modalKey: String): String = "$portalKey.modal.$modalKey.dialog"
}

private fun clearTemplateOwnedListeners(root: DOMNode) {
    EventBus.run {
        clearTemplateOwnedListeners(
            root = root,
            node = root,
        )
    }
}

private fun clearTemplateOwnedListeners(root: DOMNode, node: DOMNode) {
    if (!isOwnedByTemplateRoot(root, node)) return
    EventBus.run { node.clearOwnListeners() }
    node.children.forEach { child -> clearTemplateOwnedListeners(root, child) }
}

private fun isOwnedByTemplateRoot(root: DOMNode, node: DOMNode): Boolean {
    if (node === root) return true
    var current = node.parent
    while (current != null) {
        if (current === root) return true
        current = current.parent
    }
    return false
}

private fun commitWithoutActiveModal(state: ModalPortalState, focusRoot: DOMNode?): Boolean {
    val restoreKey = state.pendingRestoreFocusKey
    if (restoreKey != null) {
        val restored = requestFocusByKey(restoreKey, focusRoot)
        if (restored || focusRoot != null) {
            state.pendingRestoreFocusKey = null
        }
        if (!restored && focusRoot != null) {
            FocusManager.clearFocus()
        }
    }
    return state.previousKeys.isEmpty()
}

private fun commitWithActiveModal(
    portalKey: String,
    state: ModalPortalState,
    topMost: ModalSpec,
    focusRoot: DOMNode?,
) {
    restorePendingFocus(state, focusRoot)

    val topDialogKey = ModalPortalSessionStore.dialogKey(portalKey, topMost.key)
    val needsFocusOnTop = state.pendingFocusDialogKey == topDialogKey
    val focusOutsideTop = !FocusManager.isFocusWithinSubtree(topDialogKey)
    val shouldFocusTop = needsFocusOnTop || (topMost.trapFocus && focusOutsideTop)
    val focusedTop = !shouldFocusTop || focusTopDialog(topDialogKey, focusRoot)
    if (needsFocusOnTop && focusedTop) {
        state.pendingFocusDialogKey = null
    }
}

private fun restorePendingFocus(state: ModalPortalState, focusRoot: DOMNode?) {
    val restoreKey = state.pendingRestoreFocusKey ?: return
    val restored = requestFocusByKey(restoreKey, focusRoot)
    if (restored || focusRoot != null) {
        state.pendingRestoreFocusKey = null
    }
}

private fun focusTopDialog(topDialogKey: String, focusRoot: DOMNode?): Boolean =
    requestFocusFirstInSubtree(topDialogKey, focusRoot) || requestFocusByKey(topDialogKey, focusRoot)

private fun requestFocusByKey(key: Any?, focusRoot: DOMNode?): Boolean =
    if (focusRoot != null) {
        FocusManager.requestFocusByKey(focusRoot, key) || FocusManager.requestFocusByKey(key)
    } else {
        FocusManager.requestFocusByKey(key)
    }

private fun requestFocusFirstInSubtree(rootKey: Any?, focusRoot: DOMNode?): Boolean =
    if (focusRoot != null) {
        FocusManager.requestFocusFirstInSubtree(focusRoot, rootKey) ||
            FocusManager.requestFocusFirstInSubtree(rootKey)
    } else {
        FocusManager.requestFocusFirstInSubtree(rootKey)
    }
