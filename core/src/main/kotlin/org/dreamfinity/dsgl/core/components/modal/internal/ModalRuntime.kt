package org.dreamfinity.dsgl.core.components.modal.internal

import org.dreamfinity.dsgl.core.components.modal.ModalSpec
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget
import java.util.concurrent.ConcurrentHashMap

internal fun modalLifecycleKey(hostKey: String): String = "$hostKey.modal.lifecycle"

private data class ModalMeta(
    val restoreFocus: Boolean,
)

private class ModalHostState {
    var previousKeys: List<String> = emptyList()
    var previousMetaByKey: Map<String, ModalMeta> = emptyMap()
    val restoreFocusByModalKey: MutableMap<String, Any?> = linkedMapOf()
    var pendingRestoreFocusKey: Any? = null
    var pendingFocusDialogKey: String? = null
    var currentModals: List<ModalSpec> = emptyList()
}

internal object ModalRuntime {
    data class PortalSnapshot(
        val hostKey: String,
        val root: ModalPortalRootNode,
    )

    private val states: MutableMap<String, ModalHostState> = ConcurrentHashMap()
    private val portalTemplates: MutableMap<String, ModalPortalRootNode> = ConcurrentHashMap()
    private val portalHostRefs: MutableMap<String, RefTarget<ElementHandle>> = ConcurrentHashMap()

    fun onBuild(hostKey: String, modals: List<ModalSpec>) {
        val state = states.getOrPut(hostKey) { ModalHostState() }
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
            state.pendingFocusDialogKey = dialogKey(hostKey, currentTop)
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

    fun onCommit(hostKey: String, modals: List<ModalSpec>, focusRoot: DOMNode? = null) {
        val state = states[hostKey] ?: return
        val topMost = modals.lastOrNull()

        if (topMost == null) {
            if (commitWithoutActiveModal(state, focusRoot)) {
                states.remove(hostKey)
            }
            return
        }

        commitWithActiveModal(hostKey, state, topMost, focusRoot)
    }

    fun registerPortalTemplate(hostKey: String, root: ModalPortalRootNode) {
        val previous = portalTemplates.put(hostKey, root)
        if (previous != null && previous !== root && previous.parent == null) {
            clearTemplateOwnedListeners(previous)
        }
    }

    fun portalHostRef(hostKey: String): RefTarget<ElementHandle> =
        portalHostRefs.getOrPut(hostKey) {
            RefTarget { handle ->
                if (handle == null) {
                    forgetPortal(hostKey)
                }
            }
        }

    fun commitPortal(hostKey: String, focusRoot: DOMNode) {
        val state = states[hostKey] ?: return
        onCommit(hostKey, state.currentModals, focusRoot)
    }

    fun portalSnapshots(): List<PortalSnapshot> =
        portalTemplates
            .entries
            .sortedBy { it.key }
            .map { (hostKey, root) ->
                PortalSnapshot(
                    hostKey = hostKey,
                    root = root,
                )
            }

    fun shouldKeepPortalActive(hostKey: String): Boolean {
        val template = portalTemplates[hostKey] ?: return false
        val state = states[hostKey]
        return template.children.any { it.key != modalLifecycleKey(hostKey) } ||
            state?.previousKeys?.isNotEmpty() == true ||
            state?.pendingRestoreFocusKey != null ||
            state?.pendingFocusDialogKey != null
    }

    fun forgetPortal(hostKey: String) {
        portalTemplates.remove(hostKey)
        portalHostRefs.remove(hostKey)
        states.remove(hostKey)
    }

    fun dialogKey(hostKey: String, modalKey: String): String = "$hostKey.modal.$modalKey.dialog"
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

private fun commitWithoutActiveModal(state: ModalHostState, focusRoot: DOMNode?): Boolean {
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
    hostKey: String,
    state: ModalHostState,
    topMost: ModalSpec,
    focusRoot: DOMNode?,
) {
    restorePendingFocus(state, focusRoot)

    val topDialogKey = ModalRuntime.dialogKey(hostKey, topMost.key)
    val needsFocusOnTop = state.pendingFocusDialogKey == topDialogKey
    val focusOutsideTop = !FocusManager.isFocusWithinSubtree(topDialogKey)
    val shouldFocusTop = needsFocusOnTop || (topMost.trapFocus && focusOutsideTop)
    val focusedTop = !shouldFocusTop || focusTopDialog(topDialogKey, focusRoot)
    if (needsFocusOnTop && focusedTop) {
        state.pendingFocusDialogKey = null
    }
}

private fun restorePendingFocus(state: ModalHostState, focusRoot: DOMNode?) {
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
