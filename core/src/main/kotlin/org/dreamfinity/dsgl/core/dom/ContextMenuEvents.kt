package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.contextmenu.ContextMenuHost
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuModel
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuRuntime
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuTriggerScope
import org.dreamfinity.dsgl.core.event.MouseButton

fun DOMNode.onContextMenu(
    host: ContextMenuHost = ContextMenuRuntime.host,
    handler: ContextMenuTriggerScope.() -> Unit
) {
    val previous = onMouseDown
    onMouseDown = { event ->
        previous?.invoke(event)
        if (!event.cancelled && event.mouseButton == MouseButton.RIGHT) {
            val anchor = event.target?.bounds ?: bounds
            handler(
                ContextMenuTriggerScope(
                    mouseX = event.mouseX,
                    mouseY = event.mouseY,
                    anchorRect = anchor,
                    host = host
                )
            )
            event.cancelled = true
        }
    }
}

fun DOMNode.onContextMenuModel(
    host: ContextMenuHost = ContextMenuRuntime.host,
    modelProvider: () -> ContextMenuModel
) {
    onContextMenu(host = host, handler = { openMenu(modelProvider()) })
}
