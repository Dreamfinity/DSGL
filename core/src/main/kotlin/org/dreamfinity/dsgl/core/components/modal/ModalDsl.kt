package org.dreamfinity.dsgl.core.components.modal

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.components.modal.internal.ModalHostNode
import org.dreamfinity.dsgl.core.components.modal.internal.ModalRuntime
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.ref.RefTarget
import org.dreamfinity.dsgl.core.style.AlignItems
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.JustifyContent

fun UiScope.modalHost(
    modals: List<ModalSpec>,
    modalKey: String = "modal.host",
    content: UiScope.() -> Unit
) {
    ModalRuntime.onBuild(modalKey, modals)
    val hostNode = mount(ModalHostNode(modalKey))
    hostNode.onKeyDown = { event ->
        val topMost = modals.lastOrNull()
        if (topMost != null) {
            val topDialogKey = ModalRuntime.dialogKey(modalKey, topMost.key)
            val focusInsideTop = FocusManager.isFocusWithinSubtree(topDialogKey)
            if (event.keyCode == KeyCodes.ESCAPE) {
                if (topMost.keyboard) {
                    topMost.onHide?.invoke()
                }
                event.cancelled = true
            } else if (topMost.trapFocus && !focusInsideTop) {
                FocusManager.requestFocusFirstInSubtree(topDialogKey)
                event.cancelled = true
            } else if (!focusInsideTop) {
                event.cancelled = true
            }
        }
    }

    val hostScope = UiScope(hostNode)
    hostScope.div({ key = "$modalKey.content" }) {
        content()
    }

    modals.forEachIndexed { index, spec ->
        val isTopMost = index == modals.lastIndex
        val dialogKey = ModalRuntime.dialogKey(modalKey, spec.key)
        val backdropColor = when (spec.backdrop) {
            BackdropMode.True, BackdropMode.Static -> 0x88000000.toInt()
            BackdropMode.False -> 0x00000000
        }
        hostScope.div({
            key = "$modalKey.modal.${spec.key}.layer"
            backgroundColor = backdropColor
            onMouseDown = { event ->
                if (!isTopMost) {
                    event.cancelled = true
                } else {
                    val insideDialog = isTargetInsideDialog(event.target, dialogKey)
                    if (!insideDialog && spec.trapFocus) {
                        FocusManager.requestFocusFirstInSubtree(dialogKey)
                    }
                    event.cancelled = true
                }
            }
            onMouseClick = { event ->
                if (!isTopMost) {
                    event.cancelled = true
                } else {
                    val insideDialog = isTargetInsideDialog(event.target, dialogKey)
                    if (!insideDialog) {
                        if (spec.backdrop == BackdropMode.True) {
                            spec.onHide?.invoke()
                        }
                        event.cancelled = true
                    }
                }
            }
            onMouseWheel = { event ->
                val insideDialog = isTargetInsideDialog(event.target, dialogKey)
                if (!insideDialog) {
                    event.cancelled = true
                }
            }
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Column
                alignItems = AlignItems.Center
                justifyContent = if (spec.centered) JustifyContent.Center else JustifyContent.Start
                padding(if (spec.centered) 6 else 10)
            }
            asFlexColumn()
        }) {
            modalFrame(
                spec = spec,
                dialogKey = dialogKey,
                scope = ModalScope(
                    dismiss = spec.onHide,
                    isTopMost = isTopMost,
                    modalKey = spec.key
                )
            )
        }
    }

    hostScope.div({
        key = "$modalKey.modal.lifecycle"
        width = 0
        height = 0
        ref = RefTarget { handle ->
            if (handle != null) {
                ModalRuntime.onCommit(modalKey, modals)
            }
        }
        style = {
            display = Display.None
        }
    })
}

fun UiScope.modalFrame(
    spec: ModalSpec,
    dialogKey: String = "modal.dialog.${spec.key}",
    scope: ModalScope = ModalScope(
        dismiss = spec.onHide,
        isTopMost = true,
        modalKey = spec.key
    )
) {
    modalDialog(
        centered = spec.centered,
        size = spec.size,
        modalKey = dialogKey
    ) {
        spec.content(this, scope)
    }
}

fun UiScope.modalDialog(
    centered: Boolean = false,
    size: ModalSize? = null,
    modalKey: Any? = null,
    block: UiScope.() -> Unit
) {
    val presetWidth = when (size) {
        ModalSize.Sm -> 132
        ModalSize.Lg -> 232
        null -> 184
    }
    div({
        key = modalKey
        width = presetWidth
        padding = 0
        gap = 0
        backgroundColor = 0xFF2F3A46.toInt()
        style = {
            display = Display.Flex
            flexDirection = FlexDirection.Column
            if (!centered) {
                margin(6, 0, 0, 0)
            }
            border(1, 0xFF6E7D8C.toInt())
        }
        asFlexColumn()
    }) {
        block()
    }
}

fun UiScope.modalHeader(
    closeButton: Boolean = false,
    onHide: (() -> Unit)? = null,
    block: UiScope.() -> Unit = {}
) {
    div({
        key = "modal.header"
        padding = 4
        gap = 4
        backgroundColor = 0xFF334255.toInt()
        style = {
            display = Display.Flex
            flexDirection = FlexDirection.Row
            alignItems = AlignItems.Center
        }
        asFlexRow()
    }) {
        div({
            key = "modal.header.titleSlot"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
                flexGrow = 1f
            }
            asFlexRow()
        }) {
            block()
        }
        if (closeButton) {
            button("x", {
                width = 18
                onMouseClick = { onHide?.invoke() }
            })
        }
    }
}

fun UiScope.modalTitle(
    text: String,
    modalTitleKey: Any? = null
) {
    div({
        key = modalTitleKey
        style = {
            display = Display.Block
        }
    }) {
        text(text, {
            color = 0xFFEAF3FF.toInt()
        })
    }
}

fun UiScope.modalBody(
    modalBodyKey: Any? = null,
    block: UiScope.() -> Unit
) {
    div({
        key = modalBodyKey
        padding = 4
        gap = 3
        backgroundColor = 0xFF2F3A46.toInt()
        asFlexColumn()
    }) {
        block()
    }
}

fun UiScope.modalFooter(
    modalFooterKey: Any? = null,
    block: UiScope.() -> Unit
) {
    div({
        key = modalFooterKey
        padding = 4
        gap = 4
        backgroundColor = 0xFF334255.toInt()
        style = {
            display = Display.Flex
            flexDirection = FlexDirection.Row
            justifyContent = JustifyContent.End
            alignItems = AlignItems.Center
        }
        asFlexRow()
    }) {
        block()
    }
}

fun alertModal(
    modalKey: String,
    title: String,
    message: String,
    onClose: () -> Unit
): ModalSpec {
    return ModalSpec(
        key = modalKey,
        onHide = onClose
    ) { scope ->
        modalHeader(closeButton = true, onHide = scope.dismiss) {
            modalTitle(title)
        }
        modalBody {
            text(message)
        }
        modalFooter {
            button("OK", {
                onMouseClick = { scope.dismiss?.invoke() }
            })
        }
    }
}

fun confirmModal(
    modalKey: String,
    title: String,
    message: String,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    onConfirm: () -> Unit,
    onCancel: () -> Unit
): ModalSpec {
    return ModalSpec(
        key = modalKey,
        onHide = onCancel
    ) { scope ->
        modalHeader(closeButton = true, onHide = scope.dismiss) {
            modalTitle(title)
        }
        modalBody {
            text(message)
        }
        modalFooter {
            button(cancelText, {
                onMouseClick = { scope.dismiss?.invoke() }
            })
            button(confirmText, {
                onMouseClick = {
                    onConfirm()
                }
            })
        }
    }
}

fun promptModal(
    modalKey: String,
    title: String,
    value: String,
    onValueInput: (String) -> Unit,
    confirmText: String = "Apply",
    cancelText: String = "Cancel",
    onConfirm: () -> Unit,
    onCancel: () -> Unit
): ModalSpec {
    return ModalSpec(
        key = modalKey,
        onHide = onCancel
    ) { scope ->
        modalHeader(closeButton = true, onHide = scope.dismiss) {
            modalTitle(title)
        }
        modalBody {
            input(
                InputType.Text(value = value, placeholder = "Enter value"), {
                    this.key = "modal.prompt.input.$key"
                    width = 150
                    onInput = { event ->
                        onValueInput(event.value)
                    }
                }
            )
        }
        modalFooter {
            button(cancelText, {
                onMouseClick = { scope.dismiss?.invoke() }
            })
            button(confirmText, {
                onMouseClick = { onConfirm() }
            })
        }
    }
}

private fun isTargetInsideDialog(target: DOMNode?, dialogKey: String): Boolean {
    var node = target
    while (node != null) {
        if (node.key == dialogKey) return true
        node = node.parent
    }
    return false
}