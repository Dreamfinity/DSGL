package org.dreamfinity.dsgl.mcForge1710.demo.examples.cookbook

import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.components.modal.*
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dsl.button
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.mcForge1710.demo.examples.containers.centeredFlexWrapper

class ModalStackWindow : DsglWindow() {
    override fun render() = ui {
        centeredFlexWrapper {
            modalStackRecipe()
        }
    }
}

private fun UiScope.modalStackRecipe() {
    var modals by useState(emptyList<ModalSpec>())

    fun removeModal(key: String) {
        modals = modals.filterNot { it.key == key }
    }

    modalHost(modals = modals, modalKey = "recipe.modal.host") {
        button("Open modal", {
            onMouseClick = {
                modals += ModalSpec(
                    key = "recipe.modal.basic",
                    onHide = { removeModal("recipe.modal.basic") }
                ) { scope ->
                    modalHeader(closeButton = true, onHide = scope.dismiss) {
                        modalTitle("Recipe modal")
                    }
                    modalBody {
                        text("Modal content")
                        button("Open another modal", { onMouseClick = {

                        } })
                    }
                    modalFooter {
                        button("Close", { onMouseClick = { scope.dismiss?.invoke() } })
                    }
                }
            }
        })
    }
}
