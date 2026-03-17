package org.dreamfinity.dsgl.core.overlay.system

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorEditorKind
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleProperty

class InspectorInputPathBaselineTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        KeyModifiers.sync(shift = false, control = false, meta = false)
        ColorPickerRuntime.engine.closeAll()
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `inspector live path baseline remains hybrid between dom and controller authority`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val inspectorNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector entry missing")
        val routeProbe = inspectorNode.javaClass.getDeclaredMethod("isDomOwnedInteractionTarget", DOMNode::class.java)
        routeProbe.isAccessible = true

        val inputTarget = TextInputNode(text = "v", key = "baseline-input")
        val dropdownTarget = ContainerNode(key = "dsgl-system-inspector-dropdown-baseline")
        val bodyTarget = ContainerNode(key = "dsgl-system-inspector-body")
        val plainTarget = ContainerNode(key = "baseline-plain")

        assertTrue(routeProbe.invoke(inspectorNode, inputTarget) as Boolean)
        assertTrue(routeProbe.invoke(inspectorNode, dropdownTarget) as Boolean)
        assertTrue(routeProbe.invoke(inspectorNode, bodyTarget) as Boolean)
        assertFalse(routeProbe.invoke(inspectorNode, plainTarget) as Boolean)

        val methodNames = InspectorController::class.java.methods.map { it.name }.toSet()
        assertTrue(methodNames.contains("handleMouseDown"))
        assertTrue(methodNames.contains("handleMouseUp"))
        assertTrue(methodNames.contains("handleMouseWheel"))
        assertTrue(methodNames.contains("handleKeyDown"))
        assertTrue(methodNames.contains("onCapturedPointerMove"))
    }

    @Test
    fun `inspector text editing baseline remains controller coordinated with dom rendered inputs`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(984, 144, MouseButton.LEFT))

        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 992, cursorY = 126, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val inspectorNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector entry missing")
        assertTrue(collectNodes(inspectorNode).any { it.styleType == "input" })

        val textRow = inspector.debugStyleEditorRows().firstOrNull { it.editorKind == InspectorEditorKind.StringInput }
            ?: error("expected string editor row")
        assertTrue(textRow.controlRect.width > 0)
        assertTrue(textRow.controlRect.height > 0)

        val methodNames = InspectorController::class.java.methods.map { it.name }.toSet()
        assertTrue(methodNames.contains("handleKeyDown"))
        assertTrue(InspectorController::class.java.declaredMethods.any { it.name.startsWith("debugActiveEditBuffer") })
    }

    @Test
    fun `inspector dropdown baseline remains live and consumed in current path`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(984, 144, MouseButton.LEFT))

        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 992, cursorY = 126, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        val inspectorNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector entry missing")
        val selectNode = collectNodes(inspectorNode).firstOrNull {
            it.key?.toString()?.startsWith("dsgl-system-inspector-editor-select-") == true
        } ?: error("expected select control node")

        val selectX = selectNode.bounds.x + 2
        val selectY = selectNode.bounds.y + (selectNode.bounds.height / 2).coerceAtLeast(1)
        assertTrue(host.handleMouseDown(selectX, selectY, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(selectX, selectY, MouseButton.LEFT))

        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = selectX, cursorY = selectY, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        val openedDropdown = inspector.debugStyleEditorDropdowns().firstOrNull() ?: error("expected opened dropdown")
        val option = openedDropdown.options.firstOrNull() ?: error("expected dropdown option")
        val optionX = option.rect.x + 2
        val optionY = option.rect.y + (option.rect.height / 2).coerceAtLeast(1)

        assertTrue(host.handleMouseDown(optionX, optionY, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(optionX, optionY, MouseButton.LEFT))
        host.syncFrame(root, inspectedLayoutRevision = 4L, cursorX = optionX, cursorY = optionY, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        assertTrue(inspector.debugStyleEditorDropdowns().isEmpty())
    }

    @Test
    fun `inspector drag and scroll baseline keeps current mixed authority behavior`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(984, 144, MouseButton.LEFT))

        val panelRect = inspector.debugPanelRect() ?: error("panel rect missing")
        val headerX = panelRect.x + 16
        val headerY = panelRect.y + 10
        assertTrue(host.handleMouseDown(headerX, headerY, MouseButton.LEFT))
        assertTrue(host.handleMouseMove(headerX - 42, headerY + 12))
        assertTrue(inspector.isPointerCaptured)
        assertTrue(host.handleMouseUp(headerX - 42, headerY + 12, MouseButton.LEFT))
        assertFalse(inspector.isPointerCaptured)

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)
        host.paint(ctx)

        val contentRect = inspector.debugContentRect()
        val wheelX = contentRect.x + 4
        val wheelY = contentRect.y + 12
        val beforeWheel = inspector.panelScrollOffsetY

        var consumedWheel = false
        repeat(4) { step ->
            consumedWheel = host.handleMouseWheel(wheelX, wheelY, -120) || consumedWheel
            host.syncFrame(root, inspectedLayoutRevision = 3L + step, cursorX = wheelX, cursorY = wheelY, inspectorPointerCaptured = false)
            host.render(ctx, 420, 280)
            host.paint(ctx)
        }
        assertTrue(consumedWheel)

        var afterWheel = inspector.panelScrollOffsetY
        repeat(12) { settle ->
            if (afterWheel > beforeWheel) return@repeat
            host.syncFrame(root, inspectedLayoutRevision = 30L + settle, cursorX = wheelX, cursorY = wheelY, inspectorPointerCaptured = false)
            host.render(ctx, 420, 280)
            host.paint(ctx)
            afterWheel = inspector.panelScrollOffsetY
        }
        assertTrue(afterWheel > beforeWheel)

        val thumb = inspector.debugScrollbarThumbRect()
        assertTrue(thumb.width > 0 && thumb.height > 0)
        val thumbX = thumb.x + thumb.width / 2
        val thumbY = thumb.y + thumb.height / 2
        assertTrue(host.handleMouseDown(thumbX, thumbY, MouseButton.LEFT))
        assertFalse(inspector.isPointerCaptured)
        assertTrue(host.handleMouseMove(thumbX, thumbY + 34))
        assertTrue(host.handleMouseUp(thumbX, thumbY + 34, MouseButton.LEFT))
        assertTrue(inspector.panelScrollOffsetY >= afterWheel)
    }

    @Test
    fun `inspector color edit baseline uses system owned picker path`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(984, 144, MouseButton.LEFT))

        val anchor = Rect(80, 80, 20, 18)
        assertTrue(inspector.debugOpenColorPickerForSelection(StyleProperty.BACKGROUND_COLOR, anchor))
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = anchor.x + 1, cursorY = anchor.y + 1, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        assertTrue(host.isSystemColorPickerOpen())
        assertEquals(OverlayOwnerScope.System, host.debugSystemColorPickerPopupOwnerScope())
        assertNotNull(host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup))
    }

    private fun inspectedRoot(): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1280, 720)
        val target = ContainerNode(key = "target").apply {
            bounds = Rect(980, 140, 120, 30)
        }
        target.applyParent(root)
        StyleEngine.setInspectorOverrideLiteral(target, StyleProperty.BACKGROUND_COLOR, "#FF112233").getOrThrow()
        return root
    }

    private fun inspectedRootWithManyChildren(): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1800, 1200)
        val selected = ContainerNode(key = "target").apply {
            bounds = Rect(980, 140, 260, 180)
        }
        selected.applyParent(root)
        repeat(60) { index ->
            ContainerNode(key = "child-$index").apply {
                bounds = Rect(980, 180 + index * 12, 180, 10)
            }.applyParent(selected)
        }
        StyleEngine.setInspectorOverrideLiteral(selected, StyleProperty.BACKGROUND_COLOR, "#FF112233").getOrThrow()
        return root
    }

    private fun collectNodes(root: DOMNode): List<DOMNode> {
        val out = ArrayList<DOMNode>()
        fun walk(node: DOMNode) {
            out += node
            node.children.forEach(::walk)
        }
        walk(root)
        return out
    }
}



