package org.dreamfinity.dsgl.mcForge1710

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.HotReloadBridge
import org.dreamfinity.dsgl.core.animation.StyleAnimationEngine
import org.dreamfinity.dsgl.core.colorpicker.*
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuRuntime
import org.dreamfinity.dsgl.core.debug.OverlayDebugControlHost
import org.dreamfinity.dsgl.core.debug.OverlayLayerDebugState
import org.dreamfinity.dsgl.core.dnd.DndRuntime
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.ColorPickerInlineNode
import org.dreamfinity.dsgl.core.dom.elements.RangeInputNode
import org.dreamfinity.dsgl.core.dom.elements.SingleLineInputNode
import org.dreamfinity.dsgl.core.dom.elements.TextAreaNode
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.hooks.HookHotReloadRemountException
import org.dreamfinity.dsgl.core.hooks.HookRenderSessionMode
import org.dreamfinity.dsgl.core.host.DsglWindowHost
import org.dreamfinity.dsgl.core.host.Viewport
import org.dreamfinity.dsgl.core.host.rawMouseToDsglX
import org.dreamfinity.dsgl.core.host.rawMouseToDsglY
import org.dreamfinity.dsgl.core.input.ClipboardAccess
import org.dreamfinity.dsgl.core.input.ClipboardBridge
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorMode
import org.dreamfinity.dsgl.core.overlay.ApplicationOverlayHost
import org.dreamfinity.dsgl.core.overlay.OverlayLayerContracts
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.overlay.UiLayerId
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayHost
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectRuntime
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.ArrayList
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashMap

/**
 * Minecraft 1.7.10 host that owns UI lifecycle and boilerplate.
 *
 * Subclass or instantiate with a [DsglWindow] and open it via
 * `Minecraft.getMinecraft().displayGuiScreen(...)`.
 */
@SideOnly(Side.CLIENT)
abstract class DsglScreenHost(
    private val windowFactory: () -> DsglWindow,
    var rendersCount: Long = 0,
) : GuiScreen(),
    DsglWindowHost {
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
    private var layoutRevision: Long = 0L
    private val pendingCleanupRoots: MutableSet<DOMNode> =
        Collections.newSetFromMap(IdentityHashMap<DOMNode, Boolean>())
    private val composedCommandsBuffer: MutableList<RenderCommand> = ArrayList(512)
    private val stagingCommandsBuffer: MutableList<RenderCommand> = ArrayList(512)
    private val applicationOverlayCommandsBuffer: MutableList<RenderCommand> = ArrayList(256)
    private val systemOverlayCommandsBuffer: MutableList<RenderCommand> = ArrayList(256)
    private var activeTarget: DOMNode? = null
    private var lastFrameNanos: Long = 0L
    private val inspector: InspectorController = InspectorController()
    private val applicationOverlayHost: ApplicationOverlayHost = ApplicationOverlayHost()
    private val systemOverlayHost: SystemOverlayHost = SystemOverlayHost(inspector)
    private val debugOverlayHost: OverlayDebugControlHost = OverlayDebugControlHost()
    private val colorSamplerOwnershipRouter: ActiveColorSamplerOwnershipRouter = ActiveColorSamplerOwnershipRouter()
    private var activeColorSamplerOwner: ActiveColorSamplerOwner = ActiveColorSamplerOwner.None
    private var activeInlineColorSamplerNode: ColorPickerInlineNode? = null
    private val inspectorInputDebug: Boolean = false
    private val perfDebug: Boolean =
        java.lang.Boolean
            .getBoolean("dsgl.perf.debug")
    private val phaseTraceDebug: Boolean =
        java.lang.Boolean
            .getBoolean("dsgl.rebuild.trace")
    private var lastPerfLogMs: Long = 0L
    private var frameIndex: Long = 0L
    private var blankFrameGuardSkips: Long = 0L
    private val pipelineErrorLogTimes: MutableMap<String, Long> = linkedMapOf()
    private val clipboardAccess: ClipboardAccess =
        object : ClipboardAccess {
            override fun readText(): String =
                try {
                    getClipboardString() ?: ""
                } catch (_: Exception) {
                    ""
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
        ScreenColorSamplerBridge.install(
            object : ScreenColorSampler {
                override fun sampleColorAt(x: Int, y: Int): Int? = adapter.sampleScreenColor(x, y)

                override fun sampleArea(
                    x: Int,
                    y: Int,
                    width: Int,
                    height: Int,
                    outArgb: IntArray,
                ): Boolean = adapter.sampleScreenArea(x, y, width, height, outArgb)
            },
        )
        inspector.deactivate()
        inspectorPointerCaptured = false
        colorSamplerOwnershipRouter.reset()
        activeColorSamplerOwner = ActiveColorSamplerOwner.None
        activeInlineColorSamplerNode = null
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
        val frameCursor = prepareFrameCursor()
        val rebuiltThisFrame = rebuildIfNeeded()
        val tree = domTree ?: return
        val dtSeconds = tickFrameAndAnimations(tree, partialTicks)
        val layoutPhase =
            commitLayoutPhaseOrFallback(
                tree = tree,
                mouseX = mouseX,
                mouseY = mouseY,
                partialTicks = partialTicks,
            ) ?: return
        val overlayState =
            syncInspectorAndResolveOverlayState(
                tree = tree,
                dsglMouseX = frameCursor.mouseX,
                dsglMouseY = frameCursor.mouseY,
            )
        val commands =
            paintApplicationRootOrFallback(
                tree = tree,
                stylesAlreadyApplied = layoutPhase.stylesAlreadyApplied,
                mouseX = mouseX,
                mouseY = mouseY,
                partialTicks = partialTicks,
            ) ?: return
        syncFeatureRuntimeFrame(
            tree = tree,
            dsglMouseX = frameCursor.mouseX,
            dsglMouseY = frameCursor.mouseY,
        )
        val applicationOverlayCommands = collectApplicationOverlayCommands(overlayState.appOverlayRenderEnabled)
        val systemOverlayCommands =
            syncSystemOverlayAndCollectCommands(
                tree = tree,
                dsglMouseX = frameCursor.mouseX,
                dsglMouseY = frameCursor.mouseY,
                systemOverlayRenderEnabled = overlayState.systemOverlayRenderEnabled,
            )
        stageSystemOverlayCommands(
            systemOverlayCommands = systemOverlayCommands,
            systemOverlayRenderEnabled = overlayState.systemOverlayRenderEnabled,
        )
        val debugOverlayCommands = collectDebugOverlayCommands()
        updateFrameInteractionState(
            tree = tree,
            dtSeconds = dtSeconds,
            dsglMouseX = frameCursor.mouseX,
            dsglMouseY = frameCursor.mouseY,
            appOverlayInputEnabled = overlayState.appOverlayInputEnabled,
            systemOverlayInputEnabled = overlayState.systemOverlayInputEnabled,
            inspectorBlocks = overlayState.inspectorBlocks,
        )
        stageApplicationOverlayCommands(
            tree = tree,
            applicationOverlayCommands = applicationOverlayCommands,
            appOverlayRenderEnabled = overlayState.appOverlayRenderEnabled,
        )
        composeAndPresentFrame(
            tree = tree,
            commands = commands,
            debugOverlayCommands = debugOverlayCommands,
            rebuiltThisFrame = rebuiltThisFrame,
            layoutCommittedThisFrame = layoutPhase.layoutCommittedThisFrame,
        )
        finishDrawScreenFrame(
            tree = tree,
            mouseX = mouseX,
            mouseY = mouseY,
            partialTicks = partialTicks,
        )
    }

    private data class FrameCursorPosition(
        val mouseX: Int,
        val mouseY: Int,
    )

    private data class LayoutPhaseResult(
        val stylesAlreadyApplied: Boolean,
        val layoutCommittedThisFrame: Boolean,
    )

    private data class OverlayLayerFrameState(
        val appOverlayRenderEnabled: Boolean,
        val systemOverlayRenderEnabled: Boolean,
        val appOverlayInputEnabled: Boolean,
        val systemOverlayInputEnabled: Boolean,
        val inspectorBlocks: Boolean,
    )

    private fun prepareFrameCursor(): FrameCursorPosition {
        updateSize(force = false)
        val dsglMouseX = lastViewport.rawMouseToDsglX(Mouse.getX())
        val dsglMouseY = lastViewport.rawMouseToDsglY(Mouse.getY())
        window.onFrame(System.currentTimeMillis())
        return FrameCursorPosition(
            mouseX = dsglMouseX,
            mouseY = dsglMouseY,
        )
    }

    private fun tickFrameAndAnimations(tree: DomTree, partialTicks: Float): Double {
        val nowNanos = System.nanoTime()
        val dtSeconds =
            if (lastFrameNanos == 0L) {
                1.0 / 60.0
            } else {
                ((nowNanos - lastFrameNanos).toDouble() / 1_000_000_000.0).coerceIn(0.0, 0.25)
            }
        lastFrameNanos = nowNanos
        OverlayLayerDebugState.updateFrameTiming(dtSeconds)
        window.tick(dtSeconds.toFloat(), partialTicks)
        val animationVisualsChanged = StyleAnimationEngine.tickAndApply(tree.root, dtSeconds, partialTicks)
        if (animationVisualsChanged) {
            tree.markVisualDirty()
        }
        return dtSeconds
    }

    private fun commitLayoutPhaseOrFallback(
        tree: DomTree,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ): LayoutPhaseResult? {
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
                captureColorPickerEyedropperSamples()
                return null
            }
        }
        return LayoutPhaseResult(
            stylesAlreadyApplied = stylesAlreadyApplied,
            layoutCommittedThisFrame = layoutCommittedThisFrame,
        )
    }

    private fun syncInspectorAndResolveOverlayState(
        tree: DomTree,
        dsglMouseX: Int,
        dsglMouseY: Int,
    ): OverlayLayerFrameState {
        inspector.onLayoutCommitted(tree.root, layoutRevision)
        inspector.onCursorMoved(dsglMouseX, dsglMouseY)
        inspectorPointerCaptured = inspector.isPointerCaptured
        if (inspectorPointerCaptured) {
            inspector.onCapturedPointerMove(dsglMouseX, dsglMouseY, lastWidth, lastHeight)
        }
        val appOverlayRenderEnabled = OverlayLayerDebugState.isRenderEnabled(UiLayerId.ApplicationOverlay)
        val systemOverlayRenderEnabled = OverlayLayerDebugState.isRenderEnabled(UiLayerId.SystemOverlay)
        val appOverlayInputEnabled = OverlayLayerDebugState.isInputEnabled(UiLayerId.ApplicationOverlay)
        val systemOverlayInputEnabled = OverlayLayerDebugState.isInputEnabled(UiLayerId.SystemOverlay)
        val inspectorBlocks =
            systemOverlayInputEnabled &&
                (
                    inspectorPointerCaptured || inspector.shouldConsumePointer(dsglMouseX, dsglMouseY)
                )
        return OverlayLayerFrameState(
            appOverlayRenderEnabled = appOverlayRenderEnabled,
            systemOverlayRenderEnabled = systemOverlayRenderEnabled,
            appOverlayInputEnabled = appOverlayInputEnabled,
            systemOverlayInputEnabled = systemOverlayInputEnabled,
            inspectorBlocks = inspectorBlocks,
        )
    }

    private fun paintApplicationRootOrFallback(
        tree: DomTree,
        stylesAlreadyApplied: Boolean,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ): List<RenderCommand>? {
        tracePhase("commands.start")
        if (!stylesAlreadyApplied) {
            tracePhase("style.start")
        }
        val commands =
            try {
                tree.paint(adapter, applyStyles = !stylesAlreadyApplied)
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logPipelineError(
                    key = "draw.paint",
                    message = "[DSGL] Paint pipeline failed; rendering previous committed frame: ${error.message}",
                )
                adapter.paint(composedCommandsBuffer)
                flushPendingCleanup()
                super.drawScreen(mouseX, mouseY, partialTicks)
                captureColorPickerEyedropperSamples()
                return null
            }
        if (!stylesAlreadyApplied) {
            tracePhase("style.end")
        }
        return commands
    }

    private fun syncFeatureRuntimeFrame(tree: DomTree, dsglMouseX: Int, dsglMouseY: Int) {
        ContextMenuRuntime.engine.onFrame(adapter, lastWidth, lastHeight, 1f)
        SelectRuntime.applicationEngine.onFrame(adapter, lastWidth, lastHeight, 1f)
        SelectRuntime.systemEngine.onFrame(adapter, lastWidth, lastHeight, 1f)
        ColorPickerRuntime.engine.onFrame(lastWidth, lastHeight)
        ColorPickerRuntime.engine.onCursorPosition(dsglMouseX, dsglMouseY)
        refreshActiveColorSamplerOwner(tree.root)
    }

    private fun collectApplicationOverlayCommands(appOverlayRenderEnabled: Boolean): List<RenderCommand> {
        if (!appOverlayRenderEnabled) {
            return emptyList()
        }
        return try {
            applicationOverlayHost.render(adapter, lastWidth, lastHeight)
            applicationOverlayHost.paint(adapter)
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Throwable,
        ) {
            logPipelineError(
                key = "draw.applicationOverlay",
                message = "[DSGL] Application overlay paint failed; skipping app overlay frame: ${error.message}",
            )
            emptyList()
        }
    }

    private fun syncSystemOverlayAndCollectCommands(
        tree: DomTree,
        dsglMouseX: Int,
        dsglMouseY: Int,
        systemOverlayRenderEnabled: Boolean,
    ): List<RenderCommand> {
        systemOverlayHost.syncFrame(
            inspectedRoot = tree.root,
            inspectedLayoutRevision = layoutRevision,
            cursorX = dsglMouseX,
            cursorY = dsglMouseY,
            inspectorPointerCaptured = inspectorPointerCaptured,
        )
        if (!systemOverlayRenderEnabled) {
            return emptyList()
        }
        return try {
            systemOverlayHost.render(adapter, lastWidth, lastHeight)
            systemOverlayHost.paint(adapter)
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Throwable,
        ) {
            logPipelineError(
                key = "draw.systemOverlay",
                message = "[DSGL] System overlay paint failed; skipping system overlay frame: ${error.message}",
            )
            emptyList()
        }
    }

    private fun stageSystemOverlayCommands(
        systemOverlayCommands: List<RenderCommand>,
        systemOverlayRenderEnabled: Boolean,
    ) {
        systemOverlayCommandsBuffer.clear()
        systemOverlayCommandsBuffer.addAll(systemOverlayCommands)
        if (systemOverlayRenderEnabled) {
            SelectRuntime.systemEngine.appendOverlayCommands(
                adapter,
                lastWidth,
                lastHeight,
                systemOverlayCommandsBuffer,
            )
        }
    }

    private fun collectDebugOverlayCommands(): List<RenderCommand> =
        runCatching {
            debugOverlayHost.render(adapter, lastWidth, lastHeight)
            debugOverlayHost.paint(adapter)
        }.getOrElse {
            emptyList()
        }

    private fun updateFrameInteractionState(
        tree: DomTree,
        dtSeconds: Double,
        dsglMouseX: Int,
        dsglMouseY: Int,
        appOverlayInputEnabled: Boolean,
        systemOverlayInputEnabled: Boolean,
        inspectorBlocks: Boolean,
    ) {
        val contextMenuBlocks = appOverlayInputEnabled && !inspectorBlocks && ContextMenuRuntime.engine.isOpen()
        val selectBlocks = appOverlayInputEnabled && !inspectorBlocks && SelectRuntime.applicationEngine.isOpen()
        val systemSelectBlocks = systemOverlayInputEnabled && SelectRuntime.systemEngine.isOpen()
        val inlineSamplerOwnsSession = activeColorSamplerOwner is ActiveColorSamplerOwner.Inline
        val colorPickerBlocks =
            !inspectorBlocks &&
                (
                    (systemOverlayInputEnabled && systemOverlayHost.isSystemColorPickerOpen()) ||
                        (appOverlayInputEnabled && ColorPickerRuntime.engine.isOpen() && !inlineSamplerOwnsSession)
                )
        if (!inspectorBlocks && !contextMenuBlocks && !selectBlocks && !systemSelectBlocks && !colorPickerBlocks) {
            DndRuntime.engine.onMouseMove(tree.root, dsglMouseX, dsglMouseY)
        }
        DndRuntime.engine.onFrame(tree.root, dtSeconds)
        val prevX = if (lastMoveX == Int.MIN_VALUE) dsglMouseX else lastMoveX
        val prevY = if (lastMoveY == Int.MIN_VALUE) dsglMouseY else lastMoveY
        val dx = dsglMouseX - prevX
        val dy = dsglMouseY - prevY
        if (inspectorBlocks || contextMenuBlocks || selectBlocks || systemSelectBlocks || colorPickerBlocks) {
            clearHoverChainStates()
            hoverTarget = null
        } else {
            updateHover(tree.root, hoverChain, dsglMouseX, dsglMouseY, dx, dy)
            hoverTarget = hoverChain.lastOrNull()
            if (dragCaptureTarget != null && hasFocusChangedSinceCapture()) {
                releaseDragCapture()
            }
            if (dx != 0 || dy != 0) {
                val moveEvent = MouseMoveEvent(dsglMouseX, dsglMouseY, prevX, prevY)
                moveEvent.target = resolveForcedPointerTarget() ?: dragCaptureTarget ?: hoverTarget
                EventBus.post(moveEvent)
            }
        }
        lastMoveX = dsglMouseX
        lastMoveY = dsglMouseY
    }

    private fun stageApplicationOverlayCommands(
        tree: DomTree,
        applicationOverlayCommands: List<RenderCommand>,
        appOverlayRenderEnabled: Boolean,
    ) {
        applicationOverlayCommandsBuffer.clear()
        if (appOverlayRenderEnabled) {
            applicationOverlayCommandsBuffer.addAll(applicationOverlayCommands)
            DndRuntime.engine.appendPlaceholderCommands(applicationOverlayCommandsBuffer)
            DndRuntime.engine.appendOverlayCommands(
                tree.root,
                adapter,
                lastWidth,
                lastHeight,
                applicationOverlayCommandsBuffer,
            )
            SelectRuntime.applicationEngine.appendOverlayCommands(
                adapter,
                lastWidth,
                lastHeight,
                applicationOverlayCommandsBuffer,
            )
            ContextMenuRuntime.engine.appendOverlayCommands(
                adapter,
                lastWidth,
                lastHeight,
                applicationOverlayCommandsBuffer,
            )
            ColorPickerRuntime.engine.appendOverlayCommands(applicationOverlayCommandsBuffer)
            appendInlineColorPickerOverlayCommands(applicationOverlayCommandsBuffer)
        }
    }

    private fun composeAndPresentFrame(
        tree: DomTree,
        commands: List<RenderCommand>,
        debugOverlayCommands: List<RenderCommand>,
        rebuiltThisFrame: Boolean,
        layoutCommittedThisFrame: Boolean,
    ) {
        OverlayLayerContracts.composePaintCommands(
            applicationRoot = commands,
            applicationOverlay = applicationOverlayCommandsBuffer,
            systemOverlay = systemOverlayCommandsBuffer,
            debug = debugOverlayCommands,
            out = stagingCommandsBuffer,
            shouldRenderLayer = OverlayLayerDebugState::isRenderEnabled,
        )
        val keepPrevious =
            shouldKeepPreviousFrameCommands(
                tree = tree,
                rebuiltThisFrame = rebuiltThisFrame,
                layoutCommittedThisFrame = layoutCommittedThisFrame,
                candidate = stagingCommandsBuffer,
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
    }

    private fun finishDrawScreenFrame(
        tree: DomTree,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ) {
        tracePhase("draw.end")
        maybeLogPerf(tree)
        flushPendingCleanup()
        super.drawScreen(mouseX, mouseY, partialTicks)
        captureColorPickerEyedropperSamples()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        window.onKeyTyped(typedChar, keyCode)
    }

    override fun onGuiClosed() {
        ClipboardBridge.install(null)
        ScreenColorSamplerBridge.install(null)
        FocusManager.clearFocus()
        DndRuntime.engine.cancelActiveDrag()
        ColorPickerRuntime.engine.closeAll()
        SelectRuntime.host.closeAll()
        ContextMenuRuntime.engine.closeAll()
        clearActiveTarget()
        flushPendingCleanup()
        clearHoverChainStates()
        inspector.deactivate()
        inspectorPointerCaptured = false
        colorSamplerOwnershipRouter.reset()
        activeColorSamplerOwner = ActiveColorSamplerOwner.None
        activeInlineColorSamplerNode = null
        layoutRevision = 0L
        StyleEngine.clearAllInspectorOverrides()
        StyleAnimationEngine.clear()
        domTree?.clearRefs()
        applicationOverlayHost.clearRefs()
        systemOverlayHost.clearRefs()
        debugOverlayHost.clearRefs()
        domTree?.root?.let { root ->
            EventBus.run { root.clearListenersDeep() }
        }
        hoverChain.clear()
        hoverTarget = null
        releaseDragCapture()
        lastFrameNanos = 0L
        window.disposeHookRuntime()
        window.onClose()
        super.onGuiClosed()
    }

    override fun doesGuiPauseGame(): Boolean = false

    override fun requestRebuild(reason: String?) {
        needsRender = true
    }

    @Suppress("EmptyFunctionBlock")
    override fun requestRedraw() {
    }

    override fun getViewport(): Viewport = lastViewport

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
        val hotSwapped = HotReloadBridge.consumeHotSwap()
        if (!hotSwapped && !needsRender && domTree != null) {
            return false
        }

        if (hotSwapped) {
            println("Hot swapped - re-building the DOM")
        }

        return try {
            tracePhase("rebuild.start")
            rendersCount++
            val nextTree = renderWithHookSession(hotSwapped)
            val currentTree = domTree
            if (currentTree == null) {
                domTree = nextTree
            } else {
                val reconcile = currentTree.reconcileWith(nextTree)
                if (reconcile.detachedRoots.isNotEmpty()) {
                    pendingCleanupRoots.addAll(reconcile.detachedRoots)
                    flushPendingCleanup()
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
            window.commitRenderBuild()
            tracePhase("rebuild.end")
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Throwable,
        ) {
            window.discardRenderBuild()
            logPipelineError(
                key = "rebuild",
                message = "[DSGL] Rebuild failed; keeping previous committed frame/tree: ${error.message}",
            )
            false
        }
    }

    private fun renderWithHookSession(hotSwapped: Boolean): DomTree {
        val mode = if (hotSwapped) HookRenderSessionMode.HotReload else HookRenderSessionMode.Normal
        val maxAttempts = if (hotSwapped) 8 else 1
        var attempt = 0
        var lastRemountRequest: HookHotReloadRemountException? = null

        while (attempt < maxAttempts) {
            attempt += 1
            window.beginRenderBuild(mode)
            var remountRequested = false
            try {
                return window.render()
            } catch (remount: HookHotReloadRemountException) {
                if (!hotSwapped) {
                    throw remount
                }
                remountRequested = true
                lastRemountRequest = remount
                println(remount.message)
            } finally {
                window.endRenderBuild()
                if (remountRequested) {
                    window.discardRenderBuild()
                }
            }
        }

        error(
            "Hot-reload hook remount recovery exceeded $maxAttempts attempts: ${lastRemountRequest?.message}",
        )
    }

    override fun handleKeyboardInput() {
        updateSize(force = false)
        KeyModifiers.sync(
            shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT),
            control = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL),
            meta = Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA),
        )
        systemOverlayHost.onInputFrame(lastWidth, lastHeight)
        ColorPickerRuntime.engine.onFrame(lastWidth, lastHeight)
        val keyCode = Keyboard.getEventKey()
        val keyChar = Keyboard.getEventCharacter()
        val inspectorMouseX = if (lastMoveX == Int.MIN_VALUE) lastMouseX else lastMoveX
        val inspectorMouseY = if (lastMoveY == Int.MIN_VALUE) lastMouseY else lastMoveY
        if (Keyboard.getEventKeyState()) {
            if (handleKeyboardKeyDown(
                    keyCode = keyCode,
                    keyChar = keyChar,
                    inspectorMouseX = inspectorMouseX,
                    inspectorMouseY = inspectorMouseY,
                )
            ) {
                return
            }
        } else {
            if (handleKeyboardKeyUp(keyCode, keyChar, inspectorMouseX, inspectorMouseY)) return
        }

        mc.dispatchKeypresses()
    }

    private fun handleKeyboardKeyDown(
        keyCode: Int,
        keyChar: Char,
        inspectorMouseX: Int,
        inspectorMouseY: Int,
    ): Boolean {
        if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && keyCode == Keyboard.KEY_F12) {
            inspector.toggle()
            inspectorPointerCaptured = false
            if (inspector.active) {
                DndRuntime.engine.cancelActiveDrag()
                releaseDragCapture()
                clearActiveTarget()
                clearHoverChainStates()
            }
            mc.dispatchKeypresses()
            return true
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && keyCode == Keyboard.KEY_F12 && inspector.active) {
            inspector.toggleMode()
            mc.dispatchKeypresses()
            return true
        }
        if (keyCode == Keyboard.KEY_F10) {
            val demoAnchorX = if (lastMoveX == Int.MIN_VALUE) inspectorMouseX else lastMoveX
            val demoAnchorY = if (lastMoveY == Int.MIN_VALUE) inspectorMouseY else lastMoveY
            systemOverlayHost.togglePanelDemo(demoAnchorX, demoAnchorY)
            mc.dispatchKeypresses()
            return true
        }
        if (keyCode == Keyboard.KEY_ESCAPE && inspector.cancelPickMode()) {
            logInspectorInput("escape cancelled inspector pick mode")
            mc.dispatchKeypresses()
            return true
        }
        if (consumeOverlayKeyDown(
                keyCode = keyCode,
                keyChar = keyChar,
                inspectorMouseX = inspectorMouseX,
                inspectorMouseY = inspectorMouseY,
            )
        ) {
            mc.dispatchKeypresses()
            return true
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
        return false
    }

    private fun handleKeyboardKeyUp(
        keyCode: Int,
        keyChar: Char,
        inspectorMouseX: Int,
        inspectorMouseY: Int,
    ): Boolean {
        val keyboardBlocked =
            inspector.active &&
                (
                    inspector.shouldConsumeKeyboard(inspectorMouseX, inspectorMouseY) ||
                        inspector.mode == InspectorMode.Locked
                )
        if (keyboardBlocked) {
            pressedKeys.remove(keyCode)
            logInspectorInput("keyboard up consumed keyCode=$keyCode")
            mc.dispatchKeypresses()
            return true
        }
        if (pressedKeys.remove(keyCode)) {
            EventBus.post(KeyboardKeyUpEvent(keyChar, keyCode))
        }
        return false
    }

    override fun handleMouseInput() {
        updateSize(force = false)
        rebuildIfNeeded()
        val tree = prepareMouseInputTree() ?: return
        val inputEvent = readMouseInputEvent()
        syncMouseInputFrame(tree, inputEvent)
        if (consumeOverlayPointerPhase(inputEvent)) return
        refreshHoverTarget(inputEvent.mouseX, inputEvent.mouseY)
        if (inputEvent.mouseButton > 2) return
        dispatchApplicationRootPointerPhase(tree, inputEvent)
        dispatchApplicationRootWheelPhase(inputEvent)
        finishMouseInputEvent(inputEvent)
    }

    private data class MouseInputEvent(
        val mouseX: Int,
        val mouseY: Int,
        val dWheel: Int,
        val mouseButton: Int,
    )

    private fun prepareMouseInputTree(): DomTree? {
        val tree = domTree ?: return null
        if (needsLayout) {
            if (tryCommitLayout(tree, "handleMouseInput")) {
                needsLayout = false
            } else {
                return null
            }
        }
        return tree
    }

    private fun readMouseInputEvent(): MouseInputEvent =
        MouseInputEvent(
            mouseX = lastViewport.rawMouseToDsglX(Mouse.getEventX()),
            mouseY = lastViewport.rawMouseToDsglY(Mouse.getEventY()),
            dWheel = Mouse.getDWheel(),
            mouseButton = Mouse.getEventButton(),
        )

    private fun syncMouseInputFrame(tree: DomTree, inputEvent: MouseInputEvent) {
        inspector.onCursorMoved(inputEvent.mouseX, inputEvent.mouseY)
        ContextMenuRuntime.engine.onFrame(
            measureContext = adapter,
            viewportWidth = lastWidth,
            viewportHeight = lastHeight,
            viewportScale = 1f,
        )
        SelectRuntime.applicationEngine.onFrame(
            measureContext = adapter,
            viewportWidth = lastWidth,
            viewportHeight = lastHeight,
            viewportScale = 1f,
        )
        SelectRuntime.systemEngine.onFrame(
            measureContext = adapter,
            viewportWidth = lastWidth,
            viewportHeight = lastHeight,
            viewportScale = 1f,
        )
        systemOverlayHost.onInputFrame(lastWidth, lastHeight)
        inspectorPointerCaptured = inspector.isPointerCaptured
        systemOverlayHost.syncFrame(
            inspectedRoot = tree.root,
            inspectedLayoutRevision = layoutRevision,
            cursorX = inputEvent.mouseX,
            cursorY = inputEvent.mouseY,
            inspectorPointerCaptured = inspectorPointerCaptured,
        )
        ColorPickerRuntime.engine.onFrame(lastWidth, lastHeight)
        refreshActiveColorSamplerOwner(tree.root)
    }

    private fun consumeOverlayPointerPhase(inputEvent: MouseInputEvent): Boolean {
        val appPressMove = inputEvent.mouseButton == -1 && eventButton != -1
        if (!appPressMove &&
            consumeOverlayPointerEvent(
                mouseX = inputEvent.mouseX,
                mouseY = inputEvent.mouseY,
                dWheel = inputEvent.dWheel,
                mouseButton = inputEvent.mouseButton,
            )
        ) {
            consumeOverlayPointerState(inputEvent.mouseX, inputEvent.mouseY)
            return true
        }
        return false
    }

    private fun dispatchApplicationRootPointerPhase(tree: DomTree, inputEvent: MouseInputEvent) {
        if (Mouse.getEventButtonState()) {
            dispatchApplicationRootPointerDown(tree, inputEvent)
        } else if (inputEvent.mouseButton != -1 && eventButton == inputEvent.mouseButton) {
            dispatchApplicationRootPointerUp(tree, inputEvent)
        } else if (eventButton != -1 && lastMouseEvent > 0L) {
            dispatchApplicationRootPointerDrag(tree, inputEvent)
        }
    }

    private fun dispatchApplicationRootPointerDown(tree: DomTree, inputEvent: MouseInputEvent) {
        eventButton = inputEvent.mouseButton
        lastMouseEvent = Minecraft.getSystemTime()
        mapButton(inputEvent.mouseButton)?.let { mappedButton ->
            val event = MouseDownEvent(inputEvent.mouseX, inputEvent.mouseY, mappedButton)
            event.target = resolvePointerDownTarget()
            EventBus.post(event)
            DndRuntime.engine.onMouseDown(tree.root, event.target ?: hoverTarget, event)
            if (mappedButton == MouseButton.LEFT) {
                setActiveTarget(event.target ?: hoverTarget)
                val captureTarget =
                    resolveDragCaptureTarget(event.target ?: hoverTarget, inputEvent.mouseX, inputEvent.mouseY)
                if (captureTarget != null) {
                    setDragCapture(captureTarget)
                    captureTarget.beginPointerCapture(inputEvent.mouseX, inputEvent.mouseY, mappedButton)
                } else if (dragCaptureTarget != null) {
                    releaseDragCapture()
                }
            }
        }
    }

    private fun dispatchApplicationRootPointerUp(tree: DomTree, inputEvent: MouseInputEvent) {
        val releaseTarget = resolvePointerUpTarget()
        val hadDragCapture = dragCaptureTarget != null
        eventButton = -1
        mapButton(inputEvent.mouseButton)?.let { mappedButton ->
            val upEvent = MouseUpEvent(inputEvent.mouseX, inputEvent.mouseY, mappedButton)
            upEvent.target = releaseTarget
            EventBus.post(upEvent)
            dragCaptureTarget?.endPointerCapture(inputEvent.mouseX, inputEvent.mouseY, mappedButton)
            val dndConsumed = DndRuntime.engine.onMouseUp(tree.root, upEvent)
            if (!hadDragCapture && !dndConsumed) {
                val clickEvent = MouseClickEvent(inputEvent.mouseX, inputEvent.mouseY, mappedButton)
                clickEvent.target = resolveClickTarget()
                EventBus.post(clickEvent)
            }
        }
        clearActiveTarget()
        releaseDragCapture()
    }

    private fun dispatchApplicationRootPointerDrag(tree: DomTree, inputEvent: MouseInputEvent) {
        mapButton(eventButton)?.let { mappedButton ->
            val dx = inputEvent.mouseX - lastMouseX
            val dy = inputEvent.mouseY - lastMouseY
            if (dx != 0 || dy != 0) {
                DndRuntime.engine.onMouseMove(tree.root, inputEvent.mouseX, inputEvent.mouseY)
                val dragEvent =
                    MouseDragEvent(
                        lastMouseX,
                        lastMouseY,
                        dx,
                        dy,
                        mappedButton,
                    )
                if (!DndRuntime.engine.isDragging) {
                    dragEvent.target = dragCaptureTarget ?: hoverTarget
                    EventBus.post(dragEvent)
                }
                dragCaptureTarget?.continuePointerCapture(
                    mouseX = inputEvent.mouseX,
                    mouseY = inputEvent.mouseY,
                    mouseDX = dx,
                    mouseDY = dy,
                    button = mappedButton,
                )
            }
        }
    }

    private fun dispatchApplicationRootWheelPhase(inputEvent: MouseInputEvent) {
        if (inputEvent.dWheel != 0) {
            val wheelTarget = resolveWheelTarget()
            if (wheelTarget != null) {
                val wheelEvent = MouseWheelEvent(inputEvent.mouseX, inputEvent.mouseY, inputEvent.dWheel)
                wheelEvent.target = wheelTarget
                EventBus.post(wheelEvent)
                if (!wheelEvent.cancelled) {
                    bubbleGenericWheel(wheelTarget, inputEvent.mouseX, inputEvent.mouseY, inputEvent.dWheel)
                }
            }
        }
    }

    private fun finishMouseInputEvent(inputEvent: MouseInputEvent) {
        lastMouseX = inputEvent.mouseX
        lastMouseY = inputEvent.mouseY
    }

    private fun consumeOverlayKeyDown(
        keyCode: Int,
        keyChar: Char,
        inspectorMouseX: Int,
        inspectorMouseY: Int,
    ): Boolean {
        val consumedBy =
            OverlayLayerContracts.firstInputConsumer(
                canConsume = { layer ->
                    when (layer) {
                        UiLayerId.Debug -> debugOverlayHost.handleKeyDown(keyCode, keyChar)
                        UiLayerId.SystemOverlay ->
                            consumeSystemOverlayKeyDown(
                                keyCode = keyCode,
                                keyChar = keyChar,
                                inspectorMouseX = inspectorMouseX,
                                inspectorMouseY = inspectorMouseY,
                            )

                        UiLayerId.ApplicationOverlay -> consumeApplicationOverlayKeyDown(keyCode, keyChar)
                        UiLayerId.ApplicationRoot -> false
                    }
                },
                isLayerInputEnabled = OverlayLayerDebugState::isInputEnabled,
            )
        return consumedBy != null
    }

    private fun consumeSystemOverlayKeyDown(
        keyCode: Int,
        keyChar: Char,
        inspectorMouseX: Int,
        inspectorMouseY: Int,
    ): Boolean {
        if (SelectRuntime.systemEngine.handleKeyDown(keyCode, keyChar)) {
            return true
        }
        if (systemOverlayHost.handleKeyDown(keyCode, keyChar)) {
            return true
        }
        val keyboardBlocked =
            inspector.active &&
                (
                    inspector.shouldConsumeKeyboard(inspectorMouseX, inspectorMouseY) ||
                        inspector.mode == InspectorMode.Locked
                )
        if (keyboardBlocked) {
            logInspectorInput("keyboard down consumed keyCode=$keyCode")
            return true
        }
        return false
    }

    private fun consumeApplicationOverlayKeyDown(keyCode: Int, keyChar: Char): Boolean {
        if (ColorPickerRuntime.engine.handleKeyDown(keyCode, keyChar)) {
            return true
        }
        if (applicationOverlayHost.handleKeyDown(keyCode, keyChar)) {
            return true
        }
        if (SelectRuntime.applicationEngine.handleKeyDown(keyCode, keyChar)) {
            return true
        }
        if (ContextMenuRuntime.engine.handleKeyDown(keyCode)) {
            return true
        }
        return false
    }

    private fun consumeOverlayPointerEvent(
        mouseX: Int,
        mouseY: Int,
        dWheel: Int,
        mouseButton: Int,
    ): Boolean {
        val mappedButton = mapButton(mouseButton)
        val buttonPressed = Mouse.getEventButtonState()
        val consumedBy =
            OverlayLayerContracts.firstInputConsumer(
                canConsume = { layer ->
                    when (layer) {
                        UiLayerId.Debug ->
                            consumeDebugPointerEvent(
                                mouseX = mouseX,
                                mouseY = mouseY,
                                dWheel = dWheel,
                                mappedButton = mappedButton,
                                mouseButton = mouseButton,
                                buttonPressed = buttonPressed,
                            )

                        UiLayerId.SystemOverlay ->
                            consumeSystemOverlayPointerEvent(
                                mouseX = mouseX,
                                mouseY = mouseY,
                                dWheel = dWheel,
                                mouseButton = mouseButton,
                                mappedButton = mappedButton,
                                buttonPressed = buttonPressed,
                            )

                        UiLayerId.ApplicationOverlay ->
                            consumeApplicationOverlayPointerEvent(
                                mouseX = mouseX,
                                mouseY = mouseY,
                                dWheel = dWheel,
                                mouseButton = mouseButton,
                                mappedButton = mappedButton,
                                buttonPressed = buttonPressed,
                            )

                        UiLayerId.ApplicationRoot -> false
                    }
                },
                isLayerInputEnabled = OverlayLayerDebugState::isInputEnabled,
            )
        return consumedBy != null
    }

    private fun consumeDebugPointerEvent(
        mouseX: Int,
        mouseY: Int,
        dWheel: Int,
        mappedButton: MouseButton?,
        mouseButton: Int,
        buttonPressed: Boolean,
    ): Boolean {
        if (dWheel != 0 && debugOverlayHost.handleMouseWheel(mouseX, mouseY, dWheel)) {
            return true
        }
        if (mouseButton != -1 && mappedButton != null) {
            return if (buttonPressed) {
                debugOverlayHost.handleMouseDown(mouseX, mouseY, mappedButton)
            } else {
                debugOverlayHost.handleMouseUp(mouseX, mouseY, mappedButton)
            }
        }
        if (mouseButton == -1 && debugOverlayHost.handleMouseMove(mouseX, mouseY)) {
            return true
        }
        return false
    }

    private fun consumeSystemOverlayPointerEvent(
        mouseX: Int,
        mouseY: Int,
        dWheel: Int,
        mouseButton: Int,
        mappedButton: MouseButton?,
        buttonPressed: Boolean,
    ): Boolean {
        if (dWheel != 0 && SelectRuntime.systemEngine.handleMouseWheel(mouseX, mouseY, dWheel)) {
            return true
        }
        if (dWheel != 0 && systemOverlayHost.handleMouseWheel(mouseX, mouseY, dWheel)) {
            return true
        }
        if (mouseButton != -1 && mappedButton != null) {
            val consumedBySystemSelect =
                if (buttonPressed) {
                    SelectRuntime.systemEngine.handleMouseDown(mouseX, mouseY, mappedButton)
                } else {
                    SelectRuntime.systemEngine.handleMouseUp(mouseX, mouseY, mappedButton)
                }
            if (consumedBySystemSelect) {
                return true
            }
            val consumedBySystemOverlay =
                if (buttonPressed) {
                    systemOverlayHost.handleMouseDown(mouseX, mouseY, mappedButton)
                } else {
                    systemOverlayHost.handleMouseUp(mouseX, mouseY, mappedButton)
                }
            if (consumedBySystemOverlay) {
                return true
            }
        } else if (mouseButton == -1 && SelectRuntime.systemEngine.handleMouseMove(mouseX, mouseY)) {
            return true
        } else if (mouseButton == -1 && systemOverlayHost.handleMouseMove(mouseX, mouseY)) {
            return true
        }

        val inspectorConsumesPointer = inspector.shouldConsumePointer(mouseX, mouseY)
        if (!inspectorConsumesPointer) return false
        if (!buttonPressed && mouseButton != -1) {
            inspectorPointerCaptured = false
        }
        logInspectorInput("pointer event consumed by inspector bounds button=$mouseButton wheel=$dWheel")
        return true
    }

    private fun consumeApplicationOverlayPointerEvent(
        mouseX: Int,
        mouseY: Int,
        dWheel: Int,
        mouseButton: Int,
        mappedButton: MouseButton?,
        buttonPressed: Boolean,
    ): Boolean {
        val inlineSamplerOwnsSession = activeColorSamplerOwner is ActiveColorSamplerOwner.Inline
        if (!inlineSamplerOwnsSession) {
            if (dWheel != 0 && ColorPickerRuntime.engine.handleMouseWheel(mouseX, mouseY, dWheel)) {
                return true
            }
            if (mouseButton != -1 && mappedButton != null) {
                val consumedByColorPicker =
                    if (buttonPressed) {
                        ColorPickerRuntime.engine.handleMouseDown(mouseX, mouseY, mappedButton)
                    } else {
                        ColorPickerRuntime.engine.handleMouseUp(mouseX, mouseY, mappedButton)
                    }
                if (consumedByColorPicker) {
                    return true
                }
            } else if (mouseButton == -1 && ColorPickerRuntime.engine.handleMouseMove(mouseX, mouseY)) {
                return true
            }
        }

        if (dWheel != 0 && applicationOverlayHost.handleMouseWheel(mouseX, mouseY, dWheel)) {
            return true
        }
        if (mouseButton != -1 && mappedButton != null) {
            val consumedByAppOverlay =
                if (buttonPressed) {
                    applicationOverlayHost.handleMouseDown(mouseX, mouseY, mappedButton)
                } else {
                    applicationOverlayHost.handleMouseUp(mouseX, mouseY, mappedButton)
                }
            if (consumedByAppOverlay) {
                return true
            }
        } else if (mouseButton == -1 && applicationOverlayHost.handleMouseMove(mouseX, mouseY)) {
            return true
        }

        if (dWheel != 0 && ContextMenuRuntime.engine.handleMouseWheel(mouseX, mouseY, dWheel)) {
            return true
        }
        if (dWheel != 0 && SelectRuntime.applicationEngine.handleMouseWheel(mouseX, mouseY, dWheel)) {
            return true
        }
        if (mouseButton != -1 && mappedButton != null) {
            val consumedByContextMenu =
                if (buttonPressed) {
                    ContextMenuRuntime.engine.handleMouseDown(mouseX, mouseY, mappedButton)
                } else {
                    ContextMenuRuntime.engine.handleMouseUp(mouseX, mouseY, mappedButton)
                }
            if (consumedByContextMenu) {
                return true
            }
            val consumedBySelect =
                if (buttonPressed) {
                    SelectRuntime.applicationEngine.handleMouseDown(mouseX, mouseY, mappedButton)
                } else {
                    SelectRuntime.applicationEngine.handleMouseUp(mouseX, mouseY, mappedButton)
                }
            if (consumedBySelect) {
                return true
            }
            return false
        }
        if (mouseButton == -1 && ContextMenuRuntime.engine.handleMouseMove(mouseX, mouseY)) {
            return true
        }
        if (mouseButton == -1 && SelectRuntime.applicationEngine.handleMouseMove(mouseX, mouseY)) {
            return true
        }
        return false
    }

    private fun consumeOverlayPointerState(mouseX: Int, mouseY: Int) {
        eventButton = -1
        clearActiveTarget()
        releaseDragCapture()
        lastMouseX = mouseX
        lastMouseY = mouseY
    }

    private fun mapButton(button: Int): MouseButton? =
        when (button) {
            0 -> MouseButton.LEFT
            1 -> MouseButton.RIGHT
            2 -> MouseButton.MIDDLE
            else -> null
        }

    init {
        inspector.installColorPickerHost(systemOverlayHost.systemInspectorColorPickerPopupHost())
    }

    private fun refreshActiveColorSamplerOwner(root: DOMNode?) {
        val inlineByToken = LinkedHashMap<Any, ColorPickerInlineNode>()
        if (root != null) {
            collectActiveInlineColorSamplers(root, inlineByToken)
        }
        val focusedInline = FocusManager.focusedNode() as? ColorPickerInlineNode
        if (focusedInline != null && focusedInline.wantsGlobalPointerInput()) {
            inlineByToken.putIfAbsent(colorSamplerToken(focusedInline), focusedInline)
        }
        activeColorSamplerOwner =
            colorSamplerOwnershipRouter.update(
                popupEyedropperActive = ColorPickerRuntime.engine.hasActiveEyedropper(),
                inlineActiveTokens = inlineByToken.keys.toSet(),
            )
        activeInlineColorSamplerNode =
            when (val owner = activeColorSamplerOwner) {
                is ActiveColorSamplerOwner.Inline -> inlineByToken[owner.token]
                else -> null
            }
    }

    private fun collectActiveInlineColorSamplers(node: DOMNode, out: MutableMap<Any, ColorPickerInlineNode>) {
        if (node is ColorPickerInlineNode && node.wantsGlobalPointerInput()) {
            out.putIfAbsent(colorSamplerToken(node), node)
        }
        for (child in node.children) {
            collectActiveInlineColorSamplers(child, out)
        }
    }

    private fun colorSamplerToken(node: ColorPickerInlineNode): Any = node.key ?: node

    private fun resolveForcedPointerTarget(): DOMNode? {
        if (activeColorSamplerOwner is ActiveColorSamplerOwner.Inline) {
            val inline = activeInlineColorSamplerNode
            if (inline != null && inline.wantsGlobalPointerInput()) {
                return inline
            }
        }
        return null
    }

    private fun appendInlineColorPickerOverlayCommands(out: MutableList<RenderCommand>) {
        val layer = OverlayLayerContracts.resolveTransientLayer(OverlayOwnerScope.Application)
        if (layer != UiLayerId.ApplicationOverlay) return
        if (activeColorSamplerOwner is ActiveColorSamplerOwner.Inline) {
            val inline = activeInlineColorSamplerNode ?: return
            if (!inline.wantsGlobalPointerInput()) return
            inline.appendEyedropperOverlayCommands(
                viewportWidth = lastWidth.coerceAtLeast(1),
                viewportHeight = lastHeight.coerceAtLeast(1),
                out = out,
            )
        }
    }

    private fun captureColorPickerEyedropperSamples() {
        refreshActiveColorSamplerOwner(domTree?.root)
        if (OverlayLayerContracts.resolveTransientLayer(OverlayOwnerScope.System) == UiLayerId.SystemOverlay) {
            systemOverlayHost.captureSystemColorPickerEyedropperSample()
        }
        if (OverlayLayerContracts.resolveTransientLayer(OverlayOwnerScope.Application) !=
            UiLayerId.ApplicationOverlay
        ) {
            return
        }
        when (activeColorSamplerOwner) {
            ActiveColorSamplerOwner.Popup -> ColorPickerRuntime.engine.captureEyedropperSample()
            is ActiveColorSamplerOwner.Inline -> {
                val inline = activeInlineColorSamplerNode
                if (inline != null && inline.wantsGlobalPointerInput()) {
                    inline.captureEyedropperSample()
                }
            }

            ActiveColorSamplerOwner.None -> {
                if (ColorPickerRuntime.engine.hasActiveEyedropper()) {
                    ColorPickerRuntime.engine.captureEyedropperSample()
                }
            }
        }
    }

    private fun flushPendingCleanup() {
        if (pendingCleanupRoots.isEmpty()) return
        val detachedRoots = pendingCleanupRoots.toList()
        pendingCleanupRoots.clear()
        detachedRoots.forEach { root ->
            EventBus.run { root.clearListenersDeep() }
        }
    }

    internal fun debugPendingCleanupCount(): Int = pendingCleanupRoots.size

    internal fun debugBindTreeForTests(tree: DomTree, needsLayout: Boolean = false) {
        domTree = tree
        this.needsLayout = needsLayout
    }

    internal fun debugRefreshHoverTargetForTests(mouseX: Int, mouseY: Int) {
        refreshHoverTarget(mouseX, mouseY)
    }

    internal fun debugHoverTargetForTests(): DOMNode? = hoverTarget

    internal fun debugResolvePointerDownTargetForTests(): DOMNode? = resolvePointerDownTarget()

    internal fun debugResolveClickTargetForTests(): DOMNode? = resolveClickTarget()

    internal fun debugSetNeedsRenderForTests(value: Boolean) {
        needsRender = value
    }

    internal fun debugRebuildIfNeededForTests(): Boolean = rebuildIfNeeded()

    private fun setDragCapture(target: DOMNode) {
        dragCaptureTarget = target
        dragCaptureKey = target.key
        dragCaptureClass = target.javaClass
        dragCaptureFocusKey = FocusManager.focusedNode()?.key
    }

    private fun releaseDragCapture() {
        dragCaptureTarget?.cancelPointerCapture()
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
        if (cls == null) {
            releaseDragCapture()
            return
        }
        if (key == null) {
            val captured = dragCaptureTarget
            if (captured != null && captured.javaClass == cls) {
                if (eventButton != -1) {
                    return
                }
                if (isSameOrAncestor(root, captured)) {
                    return
                }
            }
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

    private fun findByKeyAndClass(node: DOMNode, key: Any, cls: Class<out DOMNode>): DOMNode? {
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
        val chain = collectHoverChain(tree.root, mouseX, mouseY)
        hoverTarget = chain.lastOrNull()
    }

    private fun resolvePointerDownTarget(): DOMNode? = resolveForcedPointerTarget() ?: hoverTarget

    private fun resolvePointerUpTarget(): DOMNode? = dragCaptureTarget ?: resolveForcedPointerTarget() ?: hoverTarget

    private fun resolveClickTarget(): DOMNode? = hoverTarget

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

    private fun bubbleGenericWheel(
        target: DOMNode,
        mouseX: Int,
        mouseY: Int,
        delta: Int,
    ): Boolean {
        var current: DOMNode? = target
        while (current != null) {
            if (current.handleGenericWheel(mouseX, mouseY, delta)) {
                return true
            }
            current = current.parent
        }
        return false
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

    private fun tryCommitLayout(tree: DomTree, phase: String): Boolean {
        return try {
            tree.render(adapter, lastWidth, lastHeight)
            val rootBounds = tree.root.bounds
            if (lastWidth > 0 && lastHeight > 0 && (rootBounds.width <= 0 || rootBounds.height <= 0)) {
                logPipelineError(
                    key = "layout.$phase.invalidBounds",
                    message =
                        "[DSGL] Layout commit produced invalid root bounds " +
                            "${rootBounds.width}x${rootBounds.height} in $phase.",
                )
                return false
            }
            layoutRevision++
            inspector.onLayoutCommitted(tree.root, layoutRevision)
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Throwable,
        ) {
            logPipelineError(
                key = "layout.$phase",
                message = "[DSGL] Layout commit failed in $phase; keeping previous frame: ${error.message}",
            )
            false
        }
    }

    private fun shouldKeepPreviousFrameCommands(
        tree: DomTree,
        rebuiltThisFrame: Boolean,
        layoutCommittedThisFrame: Boolean,
        candidate: List<RenderCommand>,
    ): Boolean {
        val shape = validateCommandShape(candidate)
        if (!shape.valid) {
            logPipelineError(
                key = "shape.guard",
                message =
                    "[DSGL] Guarded invalid command shape " +
                        "(clip=${shape.clipDepth}, transform=${shape.transformDepth}, " +
                        "opacity=${shape.opacityDepth}); keeping previous frame.",
            )
            return composedCommandsBuffer.isNotEmpty()
        }
        if (candidate.isNotEmpty()) return false
        if (composedCommandsBuffer.isEmpty()) return false
        if (!rebuiltThisFrame && !layoutCommittedThisFrame) return false
        if (!hasRenderableNodes(tree.root)) return false
        logPipelineError(
            key = "blank.guard",
            message = "[DSGL] Guarded against blank rebuild frame; keeping previous commands.",
        )
        return true
    }

    private data class CommandShape(
        val valid: Boolean,
        val clipDepth: Int,
        val transformDepth: Int,
        val opacityDepth: Int,
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
            opacityDepth = opacityDepth,
        )
    }

    private fun hasRenderableNodes(node: DOMNode): Boolean {
        if (node.display != Display.None && node.children.isNotEmpty()) {
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
                "chunkVisited=${paintStats.chunkNodesVisitedLastFrame} " +
                "chunkRebuilt=${paintStats.chunkNodesRebuiltLastFrame} " +
                "styled=${styleStats.visitedNodes} styleCacheHit=${styleStats.cacheHits} " +
                "styleRecomputed=${styleStats.recomputedNodes} blankGuardSkips=$blankFrameGuardSkips",
        )
    }
}
