package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.components.modal.*
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.renderModalsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div(
        ComponentProps(
            key = "section.modals",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("Declarative modal stack (state-driven list order)."))
        text(TextProps("Last modal in list is topmost. Background button proves input blocking.").apply {
            color = DEMO_MUTED
        })

        div(ComponentProps(gap = 4).asFlexRow()) {
            button(
                ButtonProps("Open basic").apply {
                    onMouseClick = { window.pushModal(buildBasicModal(window)) }
                }
            )
            button(
                ButtonProps("Open static").apply {
                    onMouseClick = { window.pushModal(buildStaticModal(window)) }
                }
            )
            button(
                ButtonProps("Open lg centered").apply {
                    onMouseClick = { window.pushModal(buildLargeCenteredModal(window)) }
                }
            )
            button(
                ButtonProps("Open flow step 1").apply {
                    onMouseClick = { window.pushModal(buildFlowStep1Modal(window)) }
                }
            )
        }

        div(ComponentProps(gap = 4).asFlexRow()) {
            button(
                ButtonProps("Background +1").apply {
                    onMouseClick = {
                        window.modalBackgroundCounter += 1
                        window.appendInfo("Background counter incremented")
                    }
                }
            )
            button(
                ButtonProps("Pop top").apply {
                    onMouseClick = { window.popTopModal() }
                }
            )
            button(
                ButtonProps("Clear modals").apply {
                    onMouseClick = {
                        window.demoModals = emptyList()
                        window.appendInfo("Modal stack cleared")
                    }
                }
            )
        }

        text(
            TextProps {
                val stack = if (window.demoModals.isEmpty()) "[]" else window.demoModals.joinToString(
                    prefix = "[",
                    postfix = "]"
                ) { it.key }
                "Stack=$stack"
            }.apply { color = DEMO_MUTED }
        )
        text(
            TextProps {
                "Background counter=${window.modalBackgroundCounter}"
            }.apply { color = DEMO_MUTED }
        )
    }
}

private fun buildBasicModal(window: ShowcaseWindow): ModalSpec {
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
            text(TextProps("This modal closes by backdrop click, ESC, close button, or footer button."))
        }
        modalFooter {
            button(
                ButtonProps("Close").apply {
                    onMouseClick = { scope.dismiss?.invoke() }
                }
            )
        }
    }
}

private fun buildStaticModal(window: ShowcaseWindow): ModalSpec {
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
            text(TextProps("Backdrop clicks and ESC do not dismiss this modal."))
            text(TextProps("Use close button or footer action.").apply { color = DEMO_MUTED })
        }
        modalFooter {
            button(
                ButtonProps("Close").apply {
                    onMouseClick = { scope.dismiss?.invoke() }
                }
            )
        }
    }
}

private fun buildLargeCenteredModal(window: ShowcaseWindow): ModalSpec {
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
            text(TextProps("Preset size: Lg; centered=true"))
            text(TextProps("ModalHost keeps background inert while open.").apply { color = DEMO_MUTED })
        }
        modalFooter {
            button(
                ButtonProps("Done").apply {
                    onMouseClick = { scope.dismiss?.invoke() }
                }
            )
        }
    }
}

private fun buildFlowStep1Modal(window: ShowcaseWindow): ModalSpec {
    return ModalSpec(
        key = "modal.flow.1",
        onHide = { window.removeModal("modal.flow.1") }
    ) { scope ->
        modalHeader(closeButton = true, onHide = scope.dismiss) {
            modalTitle("Flow Step 1")
        }
        modalBody {
            text(TextProps("Step 1 remains visible but inert when Step 2 is pushed."))
        }
        modalFooter {
            button(
                ButtonProps("Close").apply {
                    onMouseClick = { scope.dismiss?.invoke() }
                }
            )
            button(
                ButtonProps("Next").apply {
                    onMouseClick = {
                        window.pushModal(buildFlowStep2Modal(window))
                    }
                }
            )
        }
    }
}

private fun buildFlowStep2Modal(window: ShowcaseWindow): ModalSpec {
    return ModalSpec(
        key = "modal.flow.2",
        centered = true,
        onHide = { window.removeModal("modal.flow.2") }
    ) { scope ->
        modalHeader(closeButton = true, onHide = scope.dismiss) {
            modalTitle("Flow Step 2")
        }
        modalBody {
            text(TextProps("Topmost modal only. Closing returns interaction to Step 1."))
        }
        modalFooter {
            button(
                ButtonProps("Back to Step 1").apply {
                    onMouseClick = { scope.dismiss?.invoke() }
                }
            )
        }
    }
}
