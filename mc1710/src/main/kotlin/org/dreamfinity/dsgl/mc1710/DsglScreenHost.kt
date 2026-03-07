package org.dreamfinity.dsgl.mc1710

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.gui.GuiScreen
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.animation.StyleAnimationEngine
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuRuntime
import org.dreamfinity.dsgl.core.dnd.DndRuntime
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.RangeInputNode
import org.dreamfinity.dsgl.core.dom.elements.SingleLineInputNode
import org.dreamfinity.dsgl.core.dom.elements.TextAreaNode
import org.dreamfinity.dsgl.core.dom.layout.AffineTransform2D
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.host.DsglWindowHost
import org.dreamfinity.dsgl.core.host.Viewport
import org.dreamfinity.dsgl.core.host.rawMouseToDsglX
import org.dreamfinity.dsgl.core.host.rawMouseToDsglY
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
    companion object {
        @Volatile
        private var stylesPreloadedOnce: Boolean = false
    }

    constructor(window: DsglWindow) : this({ window })

    override lateinit var window: DsglWindow
    private lateinit var adapter: Mc1710UiAdapter
    private var domTree: DomTree? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0
    private var lastViewport: Viewport = Viewport(width = 0, height = 0)
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
    private val composedCommandsBuffer: MutableList<RenderCommand> = ArrayList(512)
    private val stagingCommandsBuffer: MutableList<RenderCommand> = ArrayList(512)
    private var activeTarget: DOMNode? = null
    private var lastFrameNanos: Long = 0L
    private val inspector: InspectorController = InspectorController()
    private val inspectorInputDebug: Boolean = false
    private val perfDebug: Boolean = java.lang.Boolean.getBoolean("dsgl.perf.debug")
    private val phaseTraceDebug: Boolean = java.lang.Boolean.getBoolean("dsgl.rebuild.trace")
    private var lastPerfLogMs: Long = 0L
    private var frameIndex: Long = 0L
    private var blankFrameGuardSkips: Long = 0L
    private val pipelineErrorLogTimes: MutableMap<String, Long> = linkedMapOf()
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
        if (!stylesPreloadedOnce) {
            StyleEngine.forceReloadStylesheets()
            stylesPreloadedOnce = true
        }
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
        frameIndex += 1
        tracePhase("draw.start")
        updateSize(force = false)
        val dsglMouseX = lastViewport.rawMouseToDsglX(Mouse.getX())
        val dsglMouseY = lastViewport.rawMouseToDsglY(Mouse.getY())
        window.onFrame(System.currentTimeMillis())
        val rebuiltThisFrame = rebuildIfNeeded()
        val tree = domTree ?: return
        val nowNanos = System.nanoTime()
        val dtSeconds = if (lastFrameNanos == 0L) {
            1.0 / 60.0
        } else {
            ((nowNanos - lastFrameNanos).toDouble() / 1_000_000_000.0).coerceIn(0.0, 0.25)
        }
        lastFrameNanos = nowNanos
        window.tick(dtSeconds.toFloat(), partialTicks)
        val animationVisualsChanged = StyleAnimationEngine.tickAndApply(tree.root, dtSeconds, partialTicks)
        if (animationVisualsChanged) {
            tree.markVisualDirty()
        }
        var stylesAlreadyApplied = false
        var layoutCommittedThisFrame = false
        if (needsLayout) {
            tracePhase("layout.start")
            if (tryCommitLayout(tree, "drawScreen")) {
                needsLayout = false
                stylesAlreadyApplied = true
                layoutCommittedThisFrame = true
                tracePhase("layout.end")
            } else {
                tracePhase("layout.fail")
                adapter.paint(composedCommandsBuffer)
                flushPendingCleanup()
                super.drawScreen(mouseX, mouseY, partialTicks)
                return
            }
        }
        inspector.onLayoutCommitted(tree.root, layoutRevision)
        inspector.onCursorMoved(dsglMouseX, dsglMouseY)
        if (inspectorPointerCaptured) {
            inspector.onCapturedPointerMove(dsglMouseX, dsglMouseY, lastWidth, lastHeight)
        }
        val inspectorBlocks = inspectorPointerCaptured || inspector.shouldConsumePointer(dsglMouseX, dsglMouseY)
        tracePhase("commands.start")
        if (!stylesAlreadyApplied) {
            tracePhase("style.start")
        }
        val commands = try {
            tree.paint(adapter, applyStyles = !stylesAlreadyApplied)
        } catch (error: Throwable) {
            logPipelineError(
                key = "draw.paint",
                message = "[DSGL] Paint pipeline failed; rendering previous committed frame: ${error.message}"
            )
            adapter.paint(composedCommandsBuffer)
            flushPendingCleanup()
            super.drawScreen(mouseX, mouseY, partialTicks)
            return
        }
        if (!stylesAlreadyApplied) {
            tracePhase("style.end")
        }
        ContextMenuRuntime.engine.onFrame(adapter, lastWidth, lastHeight, 1f)
        val contextMenuBlocks = !inspectorBlocks && ContextMenuRuntime.engine.isOpen()
        if (!inspectorBlocks && !contextMenuBlocks) {
            DndRuntime.engine.onMouseMove(tree.root, dsglMouseX, dsglMouseY)
        }
        DndRuntime.engine.onFrame(tree.root, dtSeconds)
        val prevX = if (lastMoveX == Int.MIN_VALUE) dsglMouseX else lastMoveX
        val prevY = if (lastMoveY == Int.MIN_VALUE) dsglMouseY else lastMoveY
        val dx = dsglMouseX - prevX
        val dy = dsglMouseY - prevY
        if (inspectorBlocks || contextMenuBlocks) {
            clearHoverChainStates()
            hoverTarget = null
        } else {
            updateHoverLocal(tree.root, hoverChain, dsglMouseX, dsglMouseY, dx, dy)
            hoverTarget = hoverChain.lastOrNull()
            if (dragCaptureTarget != null && hasFocusChangedSinceCapture()) {
                releaseDragCapture()
            }
            if (dx != 0 || dy != 0) {
                val moveEvent = MouseMoveEvent(dsglMouseX, dsglMouseY, prevX, prevY)
                moveEvent.target = dragCaptureTarget ?: hoverTarget
                EventBus.post(moveEvent)
            }
        }
        lastMoveX = dsglMouseX
        lastMoveY = dsglMouseY
        stagingCommandsBuffer.clear()
        stagingCommandsBuffer.addAll(commands)
        DndRuntime.engine.appendPlaceholderCommands(stagingCommandsBuffer)
        DndRuntime.engine.appendOverlayCommands(tree.root, adapter, lastWidth, lastHeight, stagingCommandsBuffer)
        ContextMenuRuntime.engine.appendOverlayCommands(adapter, lastWidth, lastHeight, stagingCommandsBuffer)
        inspector.appendOverlayCommands(lastWidth, lastHeight, stagingCommandsBuffer)
        val keepPrevious = shouldKeepPreviousFrameCommands(
            tree = tree,
            rebuiltThisFrame = rebuiltThisFrame,
            layoutCommittedThisFrame = layoutCommittedThisFrame,
            candidate = stagingCommandsBuffer
        )
        if (!keepPrevious) {
            composedCommandsBuffer.clear()
            composedCommandsBuffer.addAll(stagingCommandsBuffer)
        } else {
            blankFrameGuardSkips += 1
            tracePhase("commands.guard-preserved")
        }
        tracePhase("commands.end")
        adapter.paint(composedCommandsBuffer)
        tracePhase("draw.end")
        maybeLogPerf(tree)
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
        ContextMenuRuntime.engine.closeAll()
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
        return lastViewport
    }

    private fun updateSize(force: Boolean) {
        val viewport = adapter.viewport()
        val width = viewport.width
        val height = viewport.height
        lastViewport = viewport
        if (force || width != lastWidth || height != lastHeight) {
            ContextMenuRuntime.engine.closeAll()
            lastWidth = width
            lastHeight = height
            needsLayout = true
            needsRender = true
            window.onResize(width, height)
        }
    }

    private fun rebuildIfNeeded(): Boolean {
        if (!needsRender && domTree != null) return false
        return try {
            tracePhase("rebuild.start")
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
            // Reconcile may involve selector-state mutations on template nodes.
            // Force a full style pass on the active retained tree to avoid one-frame unstyled flashes.
            StyleEngine.markSelectorStateChanged()
            needsRender = false
            needsLayout = true
            domTree?.root?.let { root ->
                FocusManager.retainFocus(root)
                restoreDragCapture(root)
                DndRuntime.engine.rebindAfterReconcile(root)
            }
            tracePhase("rebuild.end")
            true
        } catch (error: Throwable) {
            logPipelineError(
                key = "rebuild",
                message = "[DSGL] Rebuild failed; keeping previous committed frame/tree: ${error.message}"
            )
            false
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
            if (ContextMenuRuntime.engine.handleKeyDown(keyCode)) {
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
                if (downEvent.cancelled) {
                    pressedKeys.remove(keyCode)
                } else {
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
            if (tryCommitLayout(tree, "handleMouseInput")) {
                needsLayout = false
            } else {
                return
            }
        }

        val mouseX = lastViewport.rawMouseToDsglX(Mouse.getEventX())
        val mouseY = lastViewport.rawMouseToDsglY(Mouse.getEventY())
        val dWheel = Mouse.getDWheel()
        val mouseButton = Mouse.getEventButton()
        inspector.onCursorMoved(mouseX, mouseY)
        ContextMenuRuntime.engine.onFrame(
            measureContext = adapter,
            viewportWidth = lastWidth,
            viewportHeight = lastHeight,
            viewportScale = 1f
        )

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

        if (dWheel != 0 && ContextMenuRuntime.engine.handleMouseWheel(mouseX, mouseY, dWheel)) {
            eventButton = -1
            clearActiveTarget()
            releaseDragCapture()
            lastMouseX = mouseX
            lastMouseY = mouseY
            return
        }
        if (mouseButton != -1) {
            val mappedButton = mapButton(mouseButton)
            if (mappedButton != null) {
                val consumed = if (Mouse.getEventButtonState()) {
                    ContextMenuRuntime.engine.handleMouseDown(mouseX, mouseY, mappedButton)
                } else {
                    ContextMenuRuntime.engine.handleMouseUp(mouseX, mouseY, mappedButton)
                }
                if (consumed) {
                    eventButton = -1
                    clearActiveTarget()
                    releaseDragCapture()
                    lastMouseX = mouseX
                    lastMouseY = mouseY
                    return
                }
            }
        } else if (ContextMenuRuntime.engine.handleMouseMove(mouseX, mouseY)) {
            eventButton = -1
            clearActiveTarget()
            releaseDragCapture()
            lastMouseX = mouseX
            lastMouseY = mouseY
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
            if (tryCommitLayout(tree, "refreshHoverTarget")) {
                needsLayout = false
            } else {
                return
            }
        }
        val chain = collectHoverChainLocal(tree.root, mouseX, mouseY)
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

    private fun collectHoverChainLocal(root: DOMNode, mouseX: Int, mouseY: Int): List<DOMNode> {
        val out = ArrayList<DOMNode>(8)
        collectHoverChainLocal(root, mouseX, mouseY, AffineTransform2D.IDENTITY, out)
        return out
    }

    private fun collectHoverChainLocal(
        root: DOMNode,
        mouseX: Int,
        mouseY: Int,
        parentTransform: AffineTransform2D,
        out: MutableList<DOMNode>
    ): Boolean {
        if (root.styleDisabled) return false
        if (!root.isHitTestVisible()) return false
        val worldTransform = parentTransform.times(root.localTransformMatrix())
        val inverse = worldTransform.inverseOrNull() ?: return false
        val local = inverse.transform(mouseX.toFloat(), mouseY.toFloat())
        if (!root.bounds.contains(local.first, local.second)) return false
        out.add(root)
        for (i in root.children.size - 1 downTo 0) {
            val child = root.children[i]
            if (collectHoverChainLocal(child, mouseX, mouseY, worldTransform, out)) return true
        }
        return true
    }

    private fun updateHoverLocal(
        root: DOMNode,
        prevHoverChain: MutableList<DOMNode>,
        mouseX: Int,
        mouseY: Int,
        mouseDX: Int,
        mouseDY: Int
    ) {
        val currHoverChain = ArrayList<DOMNode>(prevHoverChain.size + 4)
        collectHoverChainLocal(root, mouseX, mouseY, AffineTransform2D.IDENTITY, currHoverChain)
        val minSize = minOf(prevHoverChain.size, currHoverChain.size)
        var commonPrefixLen = 0
        while (
            commonPrefixLen < minSize &&
            isSameHoverNodeLocal(prevHoverChain[commonPrefixLen], currHoverChain[commonPrefixLen])
        ) {
            commonPrefixLen++
        }
        for (i in prevHoverChain.size - 1 downTo commonPrefixLen) {
            prevHoverChain[i].setHoveredState(false)
            postMouseLeaveEventLocal(prevHoverChain[i], mouseX, mouseY)
        }
        for (i in commonPrefixLen until currHoverChain.size) {
            currHoverChain[i].setHoveredState(true)
            postMouseEnterEventLocal(currHoverChain[i], mouseX, mouseY)
        }
        for (i in 0 until commonPrefixLen) {
            currHoverChain[i].setHoveredState(true)
        }
        if (mouseDX != 0 || mouseDY != 0) {
            for (i in 0 until currHoverChain.size) {
                postMouseOverEventLocal(currHoverChain[i], mouseX, mouseY)
            }
        }
        prevHoverChain.clear()
        prevHoverChain.addAll(currHoverChain)
    }

    private fun isSameHoverNodeLocal(prev: DOMNode, curr: DOMNode): Boolean {
        if (prev === curr) return true
        val prevKey = prev.key
        val currKey = curr.key
        if (prevKey != null || currKey != null) {
            return prevKey != null &&
                    currKey != null &&
                    prevKey == currKey &&
                    prev.javaClass == curr.javaClass
        }
        if (prev.parent == null && curr.parent == null) {
            return prev.javaClass == curr.javaClass
        }
        return false
    }

    private fun postMouseEnterEventLocal(target: DOMNode, mouseX: Int, mouseY: Int) {
        val event = MouseEnterEvent(mouseX, mouseY)
        event.target = target
        EventBus.post(event)
        target.onmouseenter?.invoke(event)
    }

    private fun postMouseLeaveEventLocal(target: DOMNode, mouseX: Int, mouseY: Int) {
        val event = MouseLeaveEvent(mouseX, mouseY)
        event.target = target
        EventBus.post(event)
        target.onmouseleave?.invoke(event)
    }

    private fun postMouseOverEventLocal(target: DOMNode, mouseX: Int, mouseY: Int) {
        val event = MouseOverEvent(mouseX, mouseY)
        event.target = target
        EventBus.post(event)
        target.onmouseover?.invoke(event)
    }

    private fun logInspectorInput(message: String) {
        if (!inspectorInputDebug) return
        println("[DSGL-InspectorInput] $message")
    }

    private fun tryCommitLayout(tree: DomTree, phase: String): Boolean {
        return try {
            tree.render(adapter, lastWidth, lastHeight)
            val rootBounds = tree.root.bounds
            if (lastWidth > 0 && lastHeight > 0 && (rootBounds.width <= 0 || rootBounds.height <= 0)) {
                logPipelineError(
                    key = "layout.$phase.invalidBounds",
                    message = "[DSGL] Layout commit produced invalid root bounds ${rootBounds.width}x${rootBounds.height} in $phase."
                )
                return false
            }
            layoutRevision++
            inspector.onLayoutCommitted(tree.root, layoutRevision)
            true
        } catch (error: Throwable) {
            logPipelineError(
                key = "layout.$phase",
                message = "[DSGL] Layout commit failed in $phase; keeping previous frame: ${error.message}"
            )
            false
        }
    }

    private fun shouldKeepPreviousFrameCommands(
        tree: DomTree,
        rebuiltThisFrame: Boolean,
        layoutCommittedThisFrame: Boolean,
        candidate: List<RenderCommand>
    ): Boolean {
        val shape = validateCommandShape(candidate)
        if (!shape.valid) {
            logPipelineError(
                key = "shape.guard",
                message = "[DSGL] Guarded invalid command shape (clip=${shape.clipDepth}, transform=${shape.transformDepth}, opacity=${shape.opacityDepth}); keeping previous frame."
            )
            return composedCommandsBuffer.isNotEmpty()
        }
        if (candidate.isNotEmpty()) return false
        if (composedCommandsBuffer.isEmpty()) return false
        if (!rebuiltThisFrame && !layoutCommittedThisFrame) return false
        if (!hasRenderableNodes(tree.root)) return false
        logPipelineError(
            key = "blank.guard",
            message = "[DSGL] Guarded against blank rebuild frame; keeping previous commands."
        )
        return true
    }

    private data class CommandShape(
        val valid: Boolean,
        val clipDepth: Int,
        val transformDepth: Int,
        val opacityDepth: Int
    )

    private fun validateCommandShape(commands: List<RenderCommand>): CommandShape {
        var clipDepth = 0
        var transformDepth = 0
        var opacityDepth = 0
        for (command in commands) {
            when (command) {
                is RenderCommand.PushClip -> clipDepth += 1
                is RenderCommand.PopClip -> {
                    clipDepth -= 1
                    if (clipDepth < 0) return CommandShape(false, clipDepth, transformDepth, opacityDepth)
                }

                is RenderCommand.PushTransform -> transformDepth += 1
                is RenderCommand.PopTransform -> {
                    transformDepth -= 1
                    if (transformDepth < 0) return CommandShape(false, clipDepth, transformDepth, opacityDepth)
                }

                is RenderCommand.PushOpacity -> opacityDepth += 1
                is RenderCommand.PopOpacity -> {
                    opacityDepth -= 1
                    if (opacityDepth < 0) return CommandShape(false, clipDepth, transformDepth, opacityDepth)
                }

                else -> Unit
            }
        }
        return CommandShape(
            valid = clipDepth == 0 && transformDepth == 0 && opacityDepth == 0,
            clipDepth = clipDepth,
            transformDepth = transformDepth,
            opacityDepth = opacityDepth
        )
    }

    private fun hasRenderableNodes(node: DOMNode): Boolean {
        if (node.display != org.dreamfinity.dsgl.core.style.Display.None && node.children.isNotEmpty()) {
            return true
        }
        node.children.forEach { child ->
            if (hasRenderableNodes(child)) return true
        }
        return false
    }

    private fun tracePhase(phase: String) {
        if (!phaseTraceDebug) return
        println("[DSGL-RebuildTrace] frame=$frameIndex phase=$phase needsRender=$needsRender needsLayout=$needsLayout")
    }

    private fun logPipelineError(key: String, message: String) {
        val now = System.currentTimeMillis()
        val previous = pipelineErrorLogTimes[key] ?: 0L
        if (now - previous < 2_000L) return
        pipelineErrorLogTimes[key] = now
        println(message)
    }

    private fun maybeLogPerf(tree: DomTree) {
        if (!perfDebug) return
        val now = System.currentTimeMillis()
        if (now - lastPerfLogMs < 2_000L) return
        lastPerfLogMs = now
        val paintStats = tree.paintStats()
        val styleStats = StyleEngine.lastStyleApplyReport()
        println(
            "[DSGL-PERF] frames=${paintStats.frames} commandRebuilds=${paintStats.commandRebuilds} " +
                    "chunkVisited=${paintStats.chunkNodesVisitedLastFrame} chunkRebuilt=${paintStats.chunkNodesRebuiltLastFrame} " +
                    "styled=${styleStats.visitedNodes} styleCacheHit=${styleStats.cacheHits} " +
                    "styleRecomputed=${styleStats.recomputedNodes} blankGuardSkips=$blankFrameGuardSkips"
        )
    }
}
