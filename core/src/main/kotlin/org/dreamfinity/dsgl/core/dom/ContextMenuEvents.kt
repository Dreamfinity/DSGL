package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.contextmenu.ContextMenuModel
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuPortalService
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuTriggerScope
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.overlay.DomainPortalServices

fun DOMNode.onContextMenu(
    portalService: ContextMenuPortalService = DomainPortalServices.applicationContextMenuEngine,
    handler: ContextMenuTriggerScope.() -> Unit,
) {
    val previous = onMouseDown
    onMouseDown = { event ->
        previous?.invoke(event)
        if (!event.cancelled && event.mouseButton == MouseButton.RIGHT) {
            val sourceNode = event.target ?: this
            val sourceStyle = sourceNode.appliedComputedStyleSnapshot()
            val anchor = sourceNode.bounds
            handler(
                ContextMenuTriggerScope(
                    mouseX = event.mouseX,
                    mouseY = event.mouseY,
                    anchorRect = anchor,
                    inheritedFontId = sourceStyle?.fontId ?: sourceNode.fontId,
                    inheritedFontSize = sourceStyle?.fontSize ?: sourceNode.fontSize,
                    portalService = portalService,
                ),
            )
            event.cancelled = true
        }
    }
}

fun DOMNode.onContextMenuModel(
    portalService: ContextMenuPortalService = DomainPortalServices.applicationContextMenuEngine,
    modelProvider: () -> ContextMenuModel,
) {
    onContextMenu(portalService = portalService, handler = { openMenu(modelProvider()) })
}
