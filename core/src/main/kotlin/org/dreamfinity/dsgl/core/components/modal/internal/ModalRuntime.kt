package org.dreamfinity.dsgl.core.components.modal.internal

import org.dreamfinity.dsgl.core.components.modal.ModalSpec
import org.dreamfinity.dsgl.core.event.FocusManager
import java.util.concurrent.ConcurrentHashMap

internal object ModalRuntime {
    private data class ModalMeta(
        val restoreFocus: Boolean
    )

    private class HostState {
        var previousKeys: List<String> = emptyList()
        var previousMetaByKey: Map<String, ModalMeta> = emptyMap()
        val restoreFocusByModalKey: MutableMap<String, Any?> = linkedMapOf()
        var pendingRestoreFocusKey: Any? = null
        var pendingFocusDialogKey: String? = null
    }

    private val states: MutableMap<String, HostState> = ConcurrentHashMap()

    fun onBuild(hostKey: String, modals: List<ModalSpec>) {
        val state = states.getOrPut(hostKey) { HostState() }
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
        state.previousMetaByKey = modals.associate { spec ->
            spec.key to ModalMeta(restoreFocus = spec.restoreFocus)
        }
    }

    fun onCommit(hostKey: String, modals: List<ModalSpec>) {
        val state = states[hostKey] ?: return
        val topMost = modals.lastOrNull()

        if (topMost == null) {
            val restoreKey = state.pendingRestoreFocusKey
            state.pendingRestoreFocusKey = null
            if (restoreKey != null) {
                if (!FocusManager.requestFocusByKey(restoreKey)) {
                    FocusManager.clearFocus()
                }
            }
            if (state.previousKeys.isEmpty()) {
                states.remove(hostKey)
            }
            return
        }

        val topDialogKey = dialogKey(hostKey, topMost.key)

        val restoreKey = state.pendingRestoreFocusKey
        if (restoreKey != null) {
            state.pendingRestoreFocusKey = null
            FocusManager.requestFocusByKey(restoreKey)
        }

        val needsFocusOnTop = state.pendingFocusDialogKey == topDialogKey
        val focusOutsideTop = !FocusManager.isFocusWithinSubtree(topDialogKey)
        if ((needsFocusOnTop || (topMost.trapFocus && focusOutsideTop)) &&
            !FocusManager.requestFocusFirstInSubtree(topDialogKey)
        ) {
            FocusManager.requestFocusByKey(topDialogKey)
        }
        if (needsFocusOnTop) {
            state.pendingFocusDialogKey = null
        }
    }

    fun dialogKey(hostKey: String, modalKey: String): String = "$hostKey.modal.$modalKey.dialog"
}