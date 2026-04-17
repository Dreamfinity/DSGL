package org.dreamfinity.dsgl.core.components.modal

import org.dreamfinity.dsgl.core.dsl.UiScope

enum class BackdropMode {
    True,
    False,
    Static
}

enum class ModalSize {
    Sm,
    Lg
}

data class ModalSpec(
    val key: String,
    val backdrop: BackdropMode = BackdropMode.True,
    val keyboard: Boolean = true,
    val centered: Boolean = false,
    val size: ModalSize? = null,
    val trapFocus: Boolean = true,
    val restoreFocus: Boolean = true,
    val onHide: (() -> Unit)? = null,
    val content: UiScope.(ModalScope) -> Unit
)

data class ModalScope(
    val dismiss: (() -> Unit)?,
    val isTopMost: Boolean,
    val modalKey: String
)
