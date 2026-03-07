package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.components.modal.*
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.modalsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.modals"
        style = {
            width = contentWidth.px
            height = contentHeight.px
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
                onMouseClick = { window.pushModal(basicModal(window)) }
            })
            button("Open static", {
                onMouseClick = { window.pushModal(staticModal(window)) }
            })
            button("Open lg centered", {
                onMouseClick = { window.pushModal(largeCenteredModal(window)) }
            })
            button("Open flow step 1", {
                onMouseClick = { window.pushModal(flowStep1Modal(window)) }
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
                    window.modalBackgroundCounter += 1
                    window.appendInfo("Background counter incremented")
                }
            })
            button("Pop top", {
                onMouseClick = { window.popTopModal() }
            })
            button("Clear modals", {
                onMouseClick = {
                    window.demoModals = emptyList()
                    window.appendInfo("Modal stack cleared")
                }
            })
        }

        text({
            val stack = if (window.demoModals.isEmpty()) "[]" else window.demoModals.joinToString(
                prefix = "[",
                postfix = "]"
            ) { it.key }
            value = "Stack=$stack"
            style = { color = DEMO_MUTED }
        })
        text(
            "Background counter=${window.modalBackgroundCounter}",
            { style = { color = DEMO_MUTED } }
        )
    }
}

private fun basicModal(window: ShowcaseWindow): ModalSpec {
    return ModalSpec(
        key = "modal.basic",
        backdrop = BackdropMode.True,
        keyboard = true,
        onHide = { window.removeModal("modal.basic") }
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

private fun staticModal(window: ShowcaseWindow): ModalSpec {
    return ModalSpec(
        key = "modal.static",
        backdrop = BackdropMode.Static,
        keyboard = false,
        onHide = { window.removeModal("modal.static") }
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

private fun largeCenteredModal(window: ShowcaseWindow): ModalSpec {
    return ModalSpec(
        key = "modal.large",
        size = ModalSize.Lg,
        centered = true,
        onHide = { window.removeModal("modal.large") }
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

private fun flowStep1Modal(window: ShowcaseWindow): ModalSpec {
    return ModalSpec(
        key = "modal.flow.1",
        onHide = { window.removeModal("modal.flow.1") }
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
                    window.pushModal(flowStep2Modal(window))
                }
            })
        }
    }
}

private fun flowStep2Modal(window: ShowcaseWindow): ModalSpec {
    return ModalSpec(
        key = "modal.flow.2",
        centered = true,
        onHide = { window.removeModal("modal.flow.2") }
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


