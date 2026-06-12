package org.dreamfinity.dsgl.mcForge1710.demo.examples.cookbook

import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dsl.button
import org.dreamfinity.dsgl.core.dsl.input
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.useRef
import org.dreamfinity.dsgl.mcForge1710.demo.examples.containers.centeredFlexWrapper

class RefUsageWindow : DsglWindow() {
    override fun render() =
        ui {
            centeredFlexWrapper {
                focusRecipe()
            }
        }
}

fun UiScope.focusRecipe() {
    val inputRef by useRef<ElementHandle>()

    input(InputType.Text(value = "", placeholder = "Focusable input"), ref = inputRef)
    button("Focus input", {
        onMouseClick = { inputRef.current?.requestFocus() }
    })
}
