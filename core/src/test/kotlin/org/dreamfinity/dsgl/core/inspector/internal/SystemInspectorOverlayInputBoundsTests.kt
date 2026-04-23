package org.dreamfinity.dsgl.core.inspector.internal

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorDropdownOptionSnapshot
import org.dreamfinity.dsgl.core.inspector.InspectorDropdownSnapshot
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemInspectorOverlayInputBoundsTests {
    @Test
    fun `input bounds include rendered dropdown popup outside panel`() {
        val controller =
            InspectorController().also {
                it.toggle()
                it.setPickMode(false)
            }
        val node = SystemInspectorOverlayNode(controller)
        val panelRect = controller.overlayPanelRect() ?: error("expected panel rect")

        val popupRect =
            Rect(
                x = panelRect.x + panelRect.width + 32,
                y = panelRect.y + 80,
                width = 180,
                height = 120,
            )
        controller.onNativeDomDropdownSnapshots(
            listOf(
                InspectorDropdownSnapshot(
                    popupRect = popupRect,
                    property = StyleProperty.ALIGN,
                    unitSelect = false,
                    options =
                        listOf(
                            InspectorDropdownOptionSnapshot(
                                rect = Rect(popupRect.x + 2, popupRect.y + 2, popupRect.width - 4, 24),
                                text = "start",
                                value = "start",
                                hovered = false,
                            ),
                        ),
                    footerText = null,
                ),
            ),
        )

        node.syncInputBounds(viewportWidth = 1400, viewportHeight = 800)
        val popupProbeX = popupRect.x + 12
        val popupProbeY = popupRect.y + 12
        assertTrue(node.bounds.contains(popupProbeX, popupProbeY))

        controller.onNativeDomDropdownSnapshots(emptyList())
        node.syncInputBounds(viewportWidth = 1400, viewportHeight = 800)
        assertFalse(node.bounds.contains(popupProbeX, popupProbeY))
    }
}
