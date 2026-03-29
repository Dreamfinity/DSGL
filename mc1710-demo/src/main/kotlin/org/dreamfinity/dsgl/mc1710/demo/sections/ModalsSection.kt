package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.components.modal.*
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.useState
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.modalsSection(
    modals: List<ModalSpec>,
    onPushModal: (ModalSpec) -> Unit,
    onRemoveModal: (String) -> Unit,
    onPopTopModal: () -> Unit,
    onClearModals: () -> Unit,
    onInfo: (String) -> Unit
) {
    var modalBackgroundCounter by useState(0)

    div({
        key = "section.modals"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Declarative modal stack (state-driven list order).")
        text("Last modal in list is topmost. Background button proves input blocking.", {
            style = { color = DEMO_MUTED }
        })

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Open basic", {
                onMouseClick = { onPushModal(basicModal(onRemoveModal)) }
            })
            button("Open static", {
                onMouseClick = { onPushModal(staticModal(onRemoveModal)) }
            })
            button("Open lg centered", {
                onMouseClick = { onPushModal(largeCenteredModal(onRemoveModal)) }
            })
            button("Open flow step 1", {
                onMouseClick = { onPushModal(flowStep1Modal(onPushModal, onRemoveModal)) }
            })
        }

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Background +1", {
                onMouseClick = {
                    modalBackgroundCounter += 1
                    onInfo("Background counter incremented")
                }
            })
            button("Pop top", {
                onMouseClick = { onPopTopModal() }
            })
            button("Clear modals", {
                onMouseClick = {
                    onClearModals()
                    onInfo("Modal stack cleared")
                }
            })
        }

        text({
            val stack = if (modals.isEmpty()) "[]" else modals.joinToString(
                prefix = "[",
                postfix = "]"
            ) { it.key }
            value = "Stack=$stack"
            style = { color = DEMO_MUTED }
        })
        text(
            "Background counter=$modalBackgroundCounter",
            { style = { color = DEMO_MUTED } }
        )
    }
}

private fun basicModal(onRemoveModal: (String) -> Unit): ModalSpec {
    return ModalSpec(
        key = "modal.basic",
        backdrop = BackdropMode.True,
        keyboard = true,
        onHide = { onRemoveModal("modal.basic") }
    ) { scope ->
        modalHeader(closeButton = true, onHide = scope.dismiss) {
            modalTitle("Basic Modal")
        }
        modalBody {
            text("This modal closes by backdrop click, ESC, close button, or footer button.")
        }
        modalFooter {
            button("Close", {
                onMouseClick = { scope.dismiss?.invoke() }
            })
        }
    }
}

private fun staticModal(onRemoveModal: (String) -> Unit): ModalSpec {
    return ModalSpec(
        key = "modal.static",
        backdrop = BackdropMode.Static,
        keyboard = false,
        onHide = { onRemoveModal("modal.static") }
    ) { scope ->
        modalHeader(closeButton = true, onHide = scope.dismiss) {
            modalTitle("Static Backdrop")
        }
        modalBody {
            text("Backdrop clicks and ESC do not dismiss this modal.")
            text("Use close button or footer action.", { style = { color = DEMO_MUTED } })
        }
        modalFooter {
            button("Close", {
                onMouseClick = { scope.dismiss?.invoke() }
            })
        }
    }
}

private fun largeCenteredModal(onRemoveModal: (String) -> Unit): ModalSpec {
    return ModalSpec(
        key = "modal.large",
        size = ModalSize.Lg,
        centered = true,
        onHide = { onRemoveModal("modal.large") }
    ) { scope ->
        modalHeader(closeButton = true, onHide = scope.dismiss) {
            modalTitle("Large Centered")
        }
        modalBody {
            text("Preset size: Lg; centered=true")
            text("ModalHost keeps background inert while open.", { style = { color = DEMO_MUTED } })
        }
        modalFooter {
            button("Done", {
                onMouseClick = { scope.dismiss?.invoke() }
            })
        }
    }
}

private fun flowStep1Modal(
    onPushModal: (ModalSpec) -> Unit,
    onRemoveModal: (String) -> Unit
): ModalSpec {
    return ModalSpec(
        key = "modal.flow.1",
        onHide = { onRemoveModal("modal.flow.1") }
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
                    onPushModal(flowStep2Modal(onRemoveModal))
                }
            })
        }
    }
}

private fun flowStep2Modal(onRemoveModal: (String) -> Unit): ModalSpec {
    return ModalSpec(
        key = "modal.flow.2",
        centered = true,
        onHide = { onRemoveModal("modal.flow.2") }
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
}
