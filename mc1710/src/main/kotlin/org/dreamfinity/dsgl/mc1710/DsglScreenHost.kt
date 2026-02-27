package org.dreamfinity.dsgl.mc1710

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.gui.GuiScreen
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.animation.StyleAnimationEngine
import org.dreamfinity.dsgl.core.dnd.DndRuntime
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.RangeInputNode
import org.dreamfinity.dsgl.core.dom.elements.SingleLineInputNode
import org.dreamfinity.dsgl.core.dom.elements.TextAreaNode
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.host.DsglWindowHost
import org.dreamfinity.dsgl.core.host.Viewport
import org.dreamfinity.dsgl.core.input.ClipboardAccess
import org.dreamfinity.dsgl.core.input.ClipboardBridge
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Minecraft 1.7.10 host that owns UI lifecycle and boilerplate.
 *
 * Subclass or instantiate with a [DsglWindow] and open it via
 * `Minecraft.getMinecraft().displayGuiScreen(...)`.
 */
@SideOnly(Side.CLIENT)
abstract class DsglScreenHost(
    private val windowFactory: () -> DsglWindow,
    var rendersCount: Long = 0
) : GuiScreen(), DsglWindowHost {
    constructor(window: DsglWindow) : this({ window })

    override lateinit var window: DsglWindow
    private lateinit var adapter: Mc1710UiAdapter
    private var domTree: DomTree? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0
    private var needsRender: Boolean = true
    private var needsLayout: Boolean = true
    private var lastMouseEvent: Long = 0
    private var eventButton: Int = -1
    private var lastMouseX: Int = 0
    private var lastMouseY: Int = 0
    private var lastMoveX: Int = Int.MIN_VALUE
    private var lastMoveY: Int = Int.MIN_VALUE
    private val pressedKeys: MutableSet<Int> = HashSet()
    private val hoverChain: MutableList<DOMNode> = mutableListOf()
    private var hoverTarget: DOMNode? = null
    private var dragCaptureTarget: DOMNode? = null
    private var dragCaptureKey: Any? = null
    private var dragCaptureClass: Class<out DOMNode>? = null
    private var dragCaptureFocusKey: Any? = null
    private var inspectorPointerCaptured: Boolean = false
    private var inspectorOwnedMouseButton: Int = -1
    private var layoutRevision: Long = 0L
    private val pendingCleanupRoots: MutableList<DOMNode> = ArrayList()
    private var activeTarget: DOMNode? = null
    private var lastFrameNanos: Long = 0L
    private val inspector: InspectorController = InspectorController()
    private val inspectorInputDebug: Boolean = false
    private val clipboardAccess: ClipboardAccess = object : ClipboardAccess {
        override fun readText(): String {
            return try {
                getClipboardString() ?: ""
            } catch (_: Exception) {
                ""
            }
        }

        override fun writeText(value: String) {
            try {
                setClipboardString(value)
            } catch (_: Exception) {
            }
        }
    }

    override fun initGui() {
        DsglFonts.ensureInitialized(mc.mcDataDir, javaClass.classLoader)
        adapter = Mc1710UiAdapter(mc)
        ClipboardBridge.install(clipboardAccess)
        inspector.deactivate()
        inspectorPointerCaptured = false
        inspectorOwnedMouseButton = -1
        layoutRevision = 0L
        StyleEngine.clearAllInspectorOverrides()
        StyleAnimationEngine.clear()
        StyleEngine.setStylesDirectory(File(mc.mcDataDir, "dsgl/styles"))
        StyleEngine.forceReloadStylesheets()
        window = windowFactory()
        window.attachHost(this)
        window.markOpened(Instant.now(), ZoneId.systemDefault())
        needsRender = true
        needsLayout = true
        window.onOpen()
        updateSize(force = true)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (!::adapter.isInitialized) return
        updateSize(force = false)
        window.onFrame(System.currentTimeMillis())
        rebuildIfNeeded()
        val tree = domTree ?: return
        val nowNanos = System.nanoTime()
        val dtSeconds = if (lastFrameNanos == 0L) {
            1.0 / 60.0
        } else {
            ((nowNanos - lastFrameNanos).toDouble() / 1_000_000_000.0).coerceIn(0.0, 0.25)
        }
        lastFrameNanos = nowNanos
        window.tick(dtSeconds.toFloat(), partialTicks)
        StyleAnimationEngine.tickAndApply(tree.root, dtSeconds, partialTicks)
        var stylesAlreadyApplied = false
        if (needsLayout) {
            tree.render(adapter, lastWidth, lastHeight)
            layoutRevision++
            inspector.onLayoutCommitted(tree.root, layoutRevision)
            needsLayout = false
            stylesAlreadyApplied = true
        }
        inspector.onLayoutCommitted(tree.root, layoutRevision)
        inspector.onCursorMoved(mouseX, mouseY)
        if (inspectorPointerCaptured) {
            inspector.onCapturedPointerMove(mouseX, mouseY, lastWidth, lastHeight)
        }
        val inspectorBlocks = inspectorPointerCaptured || inspector.shouldConsumePointer(mouseX, mouseY)
        val commands = tree.paint(adapter, applyStyles = !stylesAlreadyApplied)
        if (!inspectorBlocks) {
            DndRuntime.engine.onMouseMove(tree.root, mouseX, mouseY)
        }
        DndRuntime.engine.onFrame(tree.root, dtSeconds)
        val prevX = if (lastMoveX == Int.MIN_VALUE) mouseX else lastMoveX
        val prevY = if (lastMoveY == Int.MIN_VALUE) mouseY else lastMoveY
        val dx = mouseX - prevX
        val dy = mouseY - prevY
        if (inspectorBlocks) {
            clearHoverChainStates()
            hoverTarget = null
        } else {
            updateHover(tree.root, hoverChain, mouseX, mouseY, dx, dy)
            hoverTarget = hoverChain.lastOrNull()
            if (dragCaptureTarget != null && hasFocusChangedSinceCapture()) {
                releaseDragCapture()
            }
            if (dx != 0 || dy != 0) {
                val moveEvent = MouseMoveEvent(mouseX, mouseY, prevX, prevY)
                moveEvent.target = dragCaptureTarget ?: hoverTarget
                EventBus.post(moveEvent)
            }
        }
        lastMoveX = mouseX
        lastMoveY = mouseY
        val composedCommands = ArrayList<RenderCommand>(commands.size + 48)
        composedCommands.addAll(commands)
        DndRuntime.engine.appendPlaceholderCommands(composedCommands)
        DndRuntime.engine.appendOverlayCommands(tree.root, adapter, lastWidth, lastHeight, composedCommands)
        inspector.appendOverlayCommands(lastWidth, lastHeight, composedCommands)
        adapter.paint(composedCommands)
        flushPendingCleanup()
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        window.onKeyTyped(typedChar, keyCode)
    }

    override fun onGuiClosed() {
        ClipboardBridge.install(null)
        FocusManager.clearFocus()
        DndRuntime.engine.cancelActiveDrag()
        clearActiveTarget()
        flushPendingCleanup()
        clearHoverChainStates()
        inspector.deactivate()
        inspectorPointerCaptured = false
        inspectorOwnedMouseButton = -1
        layoutRevision = 0L
        StyleEngine.clearAllInspectorOverrides()
        StyleAnimationEngine.clear()
        domTree?.clearRefs()
        domTree?.root?.let { root ->
            EventBus.run { root.clearListenersDeep() }
        }
        hoverChain.clear()
        hoverTarget = null
        releaseDragCapture()
        lastFrameNanos = 0L
        window.onClose()
        super.onGuiClosed()
    }

    override fun doesGuiPauseGame(): Boolean = false

    override fun requestRebuild(reason: String?) {
        needsRender = true
    }

    override fun requestRedraw() {
    }

    override fun getViewport(): Viewport {
        val scale = adapter.scaledResolution().scaleFactor.toFloat()
        return Viewport(lastWidth, lastHeight, scale)
    }

    private fun updateSize(force: Boolean) {
        val scaled = adapter.scaledResolution()
        val width = scaled.scaledWidth
        val height = scaled.scaledHeight
        if (force || width != lastWidth || height != lastHeight) {
            lastWidth = width
            lastHeight = height
            needsLayout = true
            needsRender = true
            window.onResize(width, height)
        }
    }

    private fun rebuildIfNeeded() {
        if (needsRender || domTree == null) {
            rendersCount++
            window.beginRenderBuild()
            val nextTree = window.render()
            val currentTree = domTree
            if (currentTree == null) {
                domTree = nextTree
            } else {
                val reconcile = currentTree.reconcileWith(nextTree)
                if (reconcile.detachedRoots.isNotEmpty()) {
                    pendingCleanupRoots.addAll(reconcile.detachedRoots)
                }
                domTree = currentTree
            }
            needsRender = false
            needsLayout = true
            domTree?.root?.let { root ->
                FocusManager.retainFocus(root)
                restoreDragCapture(root)
                DndRuntime.engine.rebindAfterReconcile(root)
            }
        }
    }

    override fun handleKeyboardInput() {
        KeyModifiers.sync(
            shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT),
            control = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL),
            meta = Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA)
        )
        val keyCode = Keyboard.getEventKey()
        val keyChar = Keyboard.getEventCharacter()
        val inspectorMouseX = if (lastMoveX == Int.MIN_VALUE) lastMouseX else lastMoveX
        val inspectorMouseY = if (lastMoveY == Int.MIN_VALUE) lastMouseY else lastMoveY
        if (Keyboard.getEventKeyState()) {
            if (keyCode == Keyboard.KEY_F8) {
                inspector.toggle()
                inspectorPointerCaptured = false
                if (inspector.active) {
                    DndRuntime.engine.cancelActiveDrag()
                    releaseDragCapture()
                    clearActiveTarget()
                    clearHoverChainStates()
                }
                mc.dispatchKeypresses()
                return
            }
            if (keyCode == Keyboard.KEY_F9 && inspector.active) {
                inspector.toggleMode()
                mc.dispatchKeypresses()
                return
            }
            if (keyCode == Keyboard.KEY_ESCAPE && inspector.cancelPickMode()) {
                logInspectorInput("escape cancelled inspector pick mode")
                mc.dispatchKeypresses()
                return
            }
            val keyboardBlocked = inspector.active && (
                    inspector.shouldConsumeKeyboard(inspectorMouseX, inspectorMouseY) ||
                            inspector.mode == org.dreamfinity.dsgl.core.inspector.InspectorMode.Locked
                    )
            if (keyboardBlocked) {
                logInspectorInput("keyboard down consumed keyCode=$keyCode")
                mc.dispatchKeypresses()
                return
            }
            if (keyCode == Keyboard.KEY_F6) {
                StyleEngine.forceReloadStylesheets()
                requestRebuild("style reload")
            }
            if (pressedKeys.add(keyCode)) {
                val downEvent = KeyboardKeyDownEvent(keyChar, keyCode)
                EventBus.post(downEvent)
                if (!downEvent.cancelled) {
                    window.onKeyTyped(keyChar, keyCode)
                    if (keyCode == Keyboard.KEY_ESCAPE) {
                        mc.displayGuiScreen(null)
                    }
                }
            }
        } else {
            val keyboardBlocked = inspector.active && (
                    inspector.shouldConsumeKeyboard(inspectorMouseX, inspectorMouseY) ||
                            inspector.mode == org.dreamfinity.dsgl.core.inspector.InspectorMode.Locked
                    )
            if (keyboardBlocked) {
                pressedKeys.remove(keyCode)
                logInspectorInput("keyboard up consumed keyCode=$keyCode")
                mc.dispatchKeypresses()
                return
            }
            if (pressedKeys.remove(keyCode)) {
                EventBus.post(KeyboardKeyUpEvent(keyChar, keyCode))
            }
        }

        mc.dispatchKeypresses()
    }

    override fun handleMouseInput() {
        updateSize(force = false)
        rebuildIfNeeded()
        val tree = domTree ?: return
        if (needsLayout) {
            tree.render(adapter, lastWidth, lastHeight)
            layoutRevision++
            inspector.onLayoutCommitted(tree.root, layoutRevision)
            needsLayout = false
        }

        val mouseX = Mouse.getEventX() * width / mc.displayWidth
        val mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1
        val dWheel = Mouse.getDWheel()
        val mouseButton = Mouse.getEventButton()
        inspector.onCursorMoved(mouseX, mouseY)

        if (dWheel != 0 && inspector.handleMouseWheel(mouseX, mouseY, dWheel)) {
            inspector.markPointerHandled("wheel in inspector")
            eventButton = -1
            clearActiveTarget()
            releaseDragCapture()
            lastMouseX = mouseX
            lastMouseY = mouseY
            logInspectorInput("wheel consumed by inspector delta=$dWheel")
            return
        }

        if (inspectorPointerCaptured) {
            if (!Mouse.getEventButtonState() && mouseButton != -1) {
                mapButton(mouseButton)?.let { mappedButton ->
                    inspector.handleMouseUp(mouseX, mouseY, mappedButton)
                }
                inspector.markPointerHandled("captured release")
                inspectorPointerCaptured = false
                inspectorOwnedMouseButton = -1
            }
            eventButton = -1
            clearActiveTarget()
            releaseDragCapture()
            lastMouseX = mouseX
            lastMouseY = mouseY
            logInspectorInput("pointer captured event consumed button=$mouseButton")
            return
        }

        if (Mouse.getEventButtonState() && mouseButton != -1) {
            mapButton(mouseButton)?.let { mappedButton ->
                if (inspector.handleMouseDown(mouseX, mouseY, mappedButton)) {
                    inspectorPointerCaptured = inspector.isDraggingPanel
                    inspectorOwnedMouseButton = mouseButton
                    inspector.markPointerHandled("down in inspector")
                    eventButton = -1
                    clearActiveTarget()
                    releaseDragCapture()
                    lastMouseX = mouseX
                    lastMouseY = mouseY
                    logInspectorInput("mouse down consumed by inspector button=$mouseButton")
                    return
                }
            }
        }

        if (!Mouse.getEventButtonState() && mouseButton != -1 && inspectorOwnedMouseButton == mouseButton) {
            mapButton(mouseButton)?.let { mappedButton ->
                inspector.handleMouseUp(mouseX, mouseY, mappedButton)
            }
            inspector.markPointerHandled("owned press release")
            inspectorPointerCaptured = false
            inspectorOwnedMouseButton = -1
            eventButton = -1
            clearActiveTarget()
            releaseDragCapture()
            lastMouseX = mouseX
            lastMouseY = mouseY
            logInspectorInput("mouse up consumed by inspector ownership button=$mouseButton")
            return
        }

        if (mouseButton != -1 && !Mouse.getEventButtonState()) {
            mapButton(mouseButton)?.let { mappedButton ->
                inspector.handleMouseUp(mouseX, mouseY, mappedButton)
            }
        }

        val inspectorConsumesPointer = inspector.shouldConsumePointer(mouseX, mouseY)
        if (inspectorConsumesPointer) {
            if (mouseButton != -1 && Mouse.getEventButtonState()) {
                // If inspector consumed the press via bounds gating, keep ownership until release.
                inspectorOwnedMouseButton = mouseButton
            }
            if (mouseButton != -1 && !Mouse.getEventButtonState()) {
                inspectorPointerCaptured = false
                inspectorOwnedMouseButton = -1
            }
            inspector.markPointerHandled(
                when {
                    dWheel != 0 -> "wheel in inspector"
                    mouseButton != -1 && Mouse.getEventButtonState() -> "down in inspector bounds"
                    mouseButton != -1 && !Mouse.getEventButtonState() -> "up in inspector bounds"
                    else -> "move in inspector bounds"
                }
            )
            eventButton = -1
            clearActiveTarget()
            releaseDragCapture()
            lastMouseX = mouseX
            lastMouseY = mouseY
            logInspectorInput("pointer event consumed by inspector bounds button=$mouseButton wheel=$dWheel")
            return
        }

        refreshHoverTarget(mouseX, mouseY)

        if (mouseButton > 2) return

        if (Mouse.getEventButtonState()) {
            eventButton = mouseButton
            lastMouseEvent = net.minecraft.client.Minecraft.getSystemTime()
            mapButton(mouseButton)?.let { mappedButton ->
                val event = MouseDownEvent(mouseX, mouseY, mappedButton)
                event.target = hoverTarget
                EventBus.post(event)
                DndRuntime.engine.onMouseDown(tree.root, event.target ?: hoverTarget, event)
                if (mappedButton == MouseButton.LEFT) {
                    setActiveTarget(event.target ?: hoverTarget)
                    val captureTarget = resolveDragCaptureTarget(event.target ?: hoverTarget, mouseX, mouseY)
                    if (captureTarget != null) {
                        setDragCapture(captureTarget)
                    } else if (dragCaptureTarget != null) {
                        releaseDragCapture()
                    }
                }
            }
        } else if (mouseButton != -1) {
            val releaseTarget = dragCaptureTarget ?: hoverTarget
            val hadDragCapture = dragCaptureTarget != null
            eventButton = -1
            mapButton(mouseButton)?.let { mappedButton ->
                val upEvent = MouseUpEvent(mouseX, mouseY, mappedButton)
                upEvent.target = releaseTarget
                EventBus.post(upEvent)
                val dndConsumed = DndRuntime.engine.onMouseUp(tree.root, upEvent)
                if (!hadDragCapture && !dndConsumed) {
                    val clickEvent = MouseClickEvent(mouseX, mouseY, mappedButton)
                    clickEvent.target = hoverTarget
                    EventBus.post(clickEvent)
                }
            }
            clearActiveTarget()
            releaseDragCapture()
        } else if (eventButton != -1 && lastMouseEvent > 0L) {
            mapButton(eventButton)?.let { mappedButton ->
                val dx = mouseX - lastMouseX
                val dy = mouseY - lastMouseY
                if (dx != 0 || dy != 0) {
                    DndRuntime.engine.onMouseMove(tree.root, mouseX, mouseY)
                    val dragEvent = MouseDragEvent(
                        lastMouseX,
                        lastMouseY,
                        dx,
                        dy,
                        mappedButton
                    )
                    if (!DndRuntime.engine.isDragging) {
                        dragEvent.target = dragCaptureTarget ?: hoverTarget
                        EventBus.post(dragEvent)
                    }
                }
            }
        }

        if (dWheel != 0) {
            val wheelEvent = MouseWheelEvent(mouseX, mouseY, dWheel)
            wheelEvent.target = resolveWheelTarget()
            EventBus.post(wheelEvent)
        }

        lastMouseX = mouseX
        lastMouseY = mouseY
    }

    private fun mapButton(button: Int): MouseButton? {
        return when (button) {
            0 -> MouseButton.LEFT
            1 -> MouseButton.RIGHT
            2 -> MouseButton.MIDDLE
            else -> null
        }
    }

    private fun flushPendingCleanup() {
        if (pendingCleanupRoots.isEmpty()) return
        val it = pendingCleanupRoots.iterator()
        while (it.hasNext()) {
            val root = it.next()
            EventBus.run { root.clearListenersDeep() }
            it.remove()
        }
    }

    private fun setDragCapture(target: DOMNode) {
        dragCaptureTarget = target
        dragCaptureKey = target.key
        dragCaptureClass = target.javaClass
        dragCaptureFocusKey = FocusManager.focusedNode()?.key
    }

    private fun releaseDragCapture() {
        RangeInputNode.clearActiveDrag()
        SingleLineInputNode.clearActiveDrag()
        TextAreaNode.clearActiveDrag()
        dragCaptureTarget = null
        dragCaptureKey = null
        dragCaptureClass = null
        dragCaptureFocusKey = null
    }

    private fun setActiveTarget(target: DOMNode?) {
        if (target?.styleDisabled == true) return
        if (activeTarget === target) return
        activeTarget?.setActiveState(false)
        activeTarget = target
        activeTarget?.setActiveState(true)
    }

    private fun clearActiveTarget() {
        activeTarget?.setActiveState(false)
        activeTarget = null
    }

    private fun resolveDragCaptureTarget(start: DOMNode?, mouseX: Int, mouseY: Int): DOMNode? {
        var current = start
        while (current != null) {
            when (current) {
                is RangeInputNode -> return current
                is SingleLineInputNode -> if (current.shouldCaptureTextSelectionDrag(mouseX, mouseY)) return current
                is TextAreaNode -> if (current.shouldCaptureAnyDrag(mouseX, mouseY)) return current
            }
            if (current.shouldCapturePointerDrag(mouseX, mouseY)) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun restoreDragCapture(root: DOMNode) {
        if (dragCaptureTarget == null) return
        val key = dragCaptureKey
        val cls = dragCaptureClass
        if (key == null || cls == null) {
            releaseDragCapture()
            return
        }

        val restored = findByKeyAndClass(root, key, cls)
        if (restored != null) {
            dragCaptureTarget = restored
        } else {
            releaseDragCapture()
        }
    }

    private fun findByKeyAndClass(
        node: DOMNode,
        key: Any,
        cls: Class<out DOMNode>
    ): DOMNode? {
        if (node.key == key && node.javaClass == cls) return node
        for (child in node.children) {
            val found = findByKeyAndClass(child, key, cls)
            if (found != null) {
                return found
            }
        }
        return null
    }

    private fun hasFocusChangedSinceCapture(): Boolean {
        if (dragCaptureFocusKey == null) return false
        val currentFocusKey = FocusManager.focusedNode()?.key
        return currentFocusKey != dragCaptureFocusKey
    }

    private fun refreshHoverTarget(mouseX: Int, mouseY: Int) {
        val tree = domTree ?: return
        if (needsLayout) {
            tree.render(adapter, lastWidth, lastHeight)
            layoutRevision++
            inspector.onLayoutCommitted(tree.root, layoutRevision)
            needsLayout = false
        }
        val chain = collectHoverChain(tree.root, mouseX, mouseY)
        hoverTarget = chain.lastOrNull()
    }

    private fun resolveWheelTarget(): DOMNode? {
        val focused = FocusManager.focusedNode()
        if (focused is TextAreaNode) {
            val hovered = hoverTarget
            if (!isSameOrAncestor(focused, hovered)) {
                return focused
            }
        }
        return hoverTarget
    }

    private fun isSameOrAncestor(candidate: DOMNode, node: DOMNode?): Boolean {
        var current = node
        while (current != null) {
            if (current === candidate) return true
            current = current.parent
        }
        return false
    }

    private fun clearHoverChainStates() {
        hoverChain.forEach { node ->
            node.setHoveredState(false)
        }
        hoverChain.clear()
    }

    private fun logInspectorInput(message: String) {
        if (!inspectorInputDebug) return
        println("[DSGL-InspectorInput] $message")
    }
}
