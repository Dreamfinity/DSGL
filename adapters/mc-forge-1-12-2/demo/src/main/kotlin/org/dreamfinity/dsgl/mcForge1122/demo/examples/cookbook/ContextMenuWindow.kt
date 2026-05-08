package org.dreamfinity.dsgl.mcForge1122.demo.examples.cookbook

import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.contextmenu.contextMenu
import org.dreamfinity.dsgl.core.dom.onContextMenu
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.mcForge1122.demo.examples.containers.centeredFlexWrapper

class ContextMenuWindow : DsglWindow() {
    override fun render() = ui {
        centeredFlexWrapper {
            contextMenuRecipe()
        }
    }
}

fun UiScope.contextMenuRecipe() {
    var lastAction by useState("none")

    div({
        key = "recipe.file.tile"
        style = { display = Display.Flex }
    }) {
        text("Right-click this tile")
    }.onContextMenu {
        openMenu(
            contextMenu(id = "recipe.file.menu") {
                item("Open") { onClick { lastAction = "open" } }
                item("Rename") { onClick { lastAction = "rename" } }
                separator()
                item("Delete") { onClick { lastAction = "delete" } }
            }
        )
    }

    text("lastAction=$lastAction")
}
