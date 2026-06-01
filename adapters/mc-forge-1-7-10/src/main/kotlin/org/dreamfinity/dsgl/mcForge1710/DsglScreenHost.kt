package org.dreamfinity.dsgl.mcForge1710

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.HotReloadBridge
import org.dreamfinity.dsgl.core.animation.*
import org.dreamfinity.dsgl.core.colorpicker.*
import org.dreamfinity.dsgl.core.debug.DebugDomainPortalHost
import org.dreamfinity.dsgl.core.debug.DebugDomainRootHost
import org.dreamfinity.dsgl.core.debug.OverlayLayerDebugState
import org.dreamfinity.dsgl.core.dnd.*
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.*
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.hooks.HookHotReloadRemountException
import org.dreamfinity.dsgl.core.hooks.HookRenderSessionMode
import org.dreamfinity.dsgl.core.host.DsglWindowHost
import org.dreamfinity.dsgl.core.host.Viewport
import org.dreamfinity.dsgl.core.host.rawMouseToDsglX
import org.dreamfinity.dsgl.core.host.rawMouseToDsglY
import org.dreamfinity.dsgl.core.input.ClipboardAccess
import org.dreamfinity.dsgl.core.input.ClipboardBridge
import org.dreamfinity.dsgl.core.inspector.*
import org.dreamfinity.dsgl.core.overlay.ApplicationOverlayHost
import org.dreamfinity.dsgl.core.overlay.DomainSurfaceHost
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurface
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.overlay.appendPortalOverlayCommands
import org.dreamfinity.dsgl.core.overlay.captureColorPickerEyedropperSample
import org.dreamfinity.dsgl.core.overlay.closeFloatingPortals
import org.dreamfinity.dsgl.core.overlay.handlePortalKeyDownAfterDom
import org.dreamfinity.dsgl.core.overlay.handlePortalKeyDownBeforeDom
import org.dreamfinity.dsgl.core.overlay.handlePortalKeyUpAfterDom
import org.dreamfinity.dsgl.core.overlay.handlePortalKeyUpBeforeDom
import org.dreamfinity.dsgl.core.overlay.handlePortalPointerAfterDom
import org.dreamfinity.dsgl.core.overlay.handlePortalPointerBeforeDom
import org.dreamfinity.dsgl.core.overlay.hasActiveColorPickerEyedropper
import org.dreamfinity.dsgl.core.overlay.hasActiveModalPortal
import org.dreamfinity.dsgl.core.overlay.hasDomPointerTargetAt
import org.dreamfinity.dsgl.core.overlay.hasOpenColorPickerPortal
import org.dreamfinity.dsgl.core.overlay.hasOpenContextMenuPortal
import org.dreamfinity.dsgl.core.overlay.hasOpenSelectPortal
import org.dreamfinity.dsgl.core.overlay.syncPortalFrame
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayHost
import org.dreamfinity.dsgl.core.overlay.toggleFloatingWindowDemo
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.*
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Collections
import java.util.IdentityHashMap

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
    private var higherSurfacePointerButton: Int = -1
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
    private val debugDomainRootHost: DebugDomainRootHost = DebugDomainRootHost()
    private val debugDomainPortalHost: DebugDomainPortalHost = DebugDomainPortalHost()
    private val domainOrchestrator: ScreenDomainSurfaceOrchestrator = ScreenDomainSurfaceOrchestrator()
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
        higherSurfacePointerButton = -1
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
        syncFeatureRuntimeFrame(
            tree = tree,
            dsglMouseX = frameCursor.mouseX,
            dsglMouseY = frameCursor.mouseY,
        )
        cancelApplicationRootDndBehindModal()
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
        val debugDomainCommands = collectDebugDomainCommands()
        updateFrameInteractionState(
            tree = tree,
            dtSeconds = dtSeconds,
            dsglMouseX = frameCursor.mouseX,
            dsglMouseY = frameCursor.mouseY,
            appOverlayInputEnabled = overlayState.appOverlayInputEnabled,
            systemOverlayInputEnabled = overlayState.systemOverlayInputEnabled,
            inspectorBlocks = overlayState.inspectorBlocks,
        )
        val commands =
            paintApplicationRootOrFallback(
                tree = tree,
                stylesAlreadyApplied = layoutPhase.stylesAlreadyApplied,
                mouseX = mouseX,
                mouseY = mouseY,
                partialTicks = partialTicks,
            ) ?: return
        stageApplicationOverlayCommands(
            tree = tree,
            applicationOverlayCommands = applicationOverlayCommands,
            appOverlayRenderEnabled = overlayState.appOverlayRenderEnabled,
        )
        composeAndPresentFrame(
            tree = tree,
            commands = commands,
            debugDomainCommands = debugDomainCommands,
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

    private data class DebugDomainCommands(
        val root: List<RenderCommand>,
        val portal: List<RenderCommand>,
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
        val appOverlayRenderEnabled = OverlayLayerDebugState.isRenderEnabled(ScreenDomainSurfaces.ApplicationPortal)
        val systemOverlayRenderEnabled = OverlayLayerDebugState.isRenderEnabled(ScreenDomainSurfaces.SystemPortal)
        val appOverlayInputEnabled = OverlayLayerDebugState.isInputEnabled(ScreenDomainSurfaces.ApplicationPortal)
        val systemOverlayInputEnabled = OverlayLayerDebugState.isInputEnabled(ScreenDomainSurfaces.SystemPortal)
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
        applicationOverlayHost.syncPortalFrame(
            measureContext = adapter,
            viewportWidth = lastWidth,
            viewportHeight = lastHeight,
            viewportScale = 1f,
            mouseX = dsglMouseX,
            mouseY = dsglMouseY,
        )
        systemOverlayHost.syncPortalFrame(adapter, lastWidth, lastHeight, 1f)
        refreshActiveColorSamplerOwner(tree.root)
    }

    private fun cancelApplicationRootDndBehindModal() {
        if (applicationOverlayHost.hasActiveModalPortal()) {
            DndRuntime.engine.cancelActiveDrag()
        }
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
            systemOverlayHost.appendPortalOverlayCommands(
                measureContext = adapter,
                viewportWidth = lastWidth,
                viewportHeight = lastHeight,
                out = systemOverlayCommandsBuffer,
            )
        }
    }

    private fun collectDebugDomainCommands(): DebugDomainCommands {
        val root =
            runCatching {
                debugDomainRootHost.render(adapter, lastWidth, lastHeight)
                debugDomainRootHost.paint(adapter)
            }.getOrElse {
                emptyList()
            }
        val portal =
            runCatching {
                debugDomainPortalHost.render(adapter, lastWidth, lastHeight)
                debugDomainPortalHost.paint(adapter)
            }.getOrElse {
                emptyList()
            }
        return DebugDomainCommands(root = root, portal = portal)
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
        val applicationRootFrameBlocked =
            isApplicationRootFrameBlocked(
                dsglMouseX = dsglMouseX,
                dsglMouseY = dsglMouseY,
                appOverlayInputEnabled = appOverlayInputEnabled,
                systemOverlayInputEnabled = systemOverlayInputEnabled,
                inspectorBlocks = inspectorBlocks,
            )
        val prevX = if (lastMoveX == Int.MIN_VALUE) dsglMouseX else lastMoveX
        val prevY = if (lastMoveY == Int.MIN_VALUE) dsglMouseY else lastMoveY
        val dx = dsglMouseX - prevX
        val dy = dsglMouseY - prevY
        val applicationModalBlocks = applicationOverlayHost.hasActiveModalPortal()
        if (applicationRootFrameBlocked) {
            if (applicationModalBlocks) {
                DndRuntime.engine.cancelActiveDrag()
            }
            clearHoverChainStates(postLeaveEvents = true, mouseX = dsglMouseX, mouseY = dsglMouseY)
            hoverTarget = null
        } else {
            updateFrameApplicationRootInteraction(tree, dsglMouseX, dsglMouseY, prevX, prevY, dx, dy)
        }
        if (!applicationModalBlocks) {
            DndRuntime.engine.onFrame(tree.root, dtSeconds)
        }
        lastMoveX = dsglMouseX
        lastMoveY = dsglMouseY
    }

    private fun isApplicationRootFrameBlocked(
        dsglMouseX: Int,
        dsglMouseY: Int,
        appOverlayInputEnabled: Boolean,
        systemOverlayInputEnabled: Boolean,
        inspectorBlocks: Boolean,
    ): Boolean {
        if (appOverlayInputEnabled && applicationOverlayHost.hasActiveModalPortal()) return true
        if (isApplicationRootPointerDragActive() && dragCaptureTarget != null) return false
        if (inspectorBlocks || higherSurfacePointerButton != -1) return true
        if (isApplicationPortalFrameBlocking(dsglMouseX, dsglMouseY, appOverlayInputEnabled)) return true
        if (systemOverlayInputEnabled && systemOverlayHost.hasOpenPortal()) return true
        return isColorPickerFrameBlocking(
            appOverlayInputEnabled = appOverlayInputEnabled,
            systemOverlayInputEnabled = systemOverlayInputEnabled,
        )
    }

    private fun isApplicationPortalFrameBlocking(
        dsglMouseX: Int,
        dsglMouseY: Int,
        appOverlayInputEnabled: Boolean,
    ): Boolean {
        if (!appOverlayInputEnabled) return false
        return applicationOverlayHost.hasOpenContextMenuPortal() ||
            applicationOverlayHost.hasOpenSelectPortal() ||
            applicationOverlayHost.hasDomPointerTargetAt(dsglMouseX, dsglMouseY)
    }

    private fun isColorPickerFrameBlocking(
        appOverlayInputEnabled: Boolean,
        systemOverlayInputEnabled: Boolean,
    ): Boolean {
        val inlineSamplerOwnsSession = activeColorSamplerOwner is ActiveColorSamplerOwner.Inline
        val systemPickerBlocks = systemOverlayInputEnabled && systemOverlayHost.isSystemColorPickerOpen()
        val applicationPickerBlocks =
            appOverlayInputEnabled &&
                applicationOverlayHost.hasOpenColorPickerPortal() &&
                !inlineSamplerOwnsSession
        return systemPickerBlocks || applicationPickerBlocks
    }

    private fun updateFrameApplicationRootInteraction(
        tree: DomTree,
        dsglMouseX: Int,
        dsglMouseY: Int,
        prevX: Int,
        prevY: Int,
        dx: Int,
        dy: Int,
    ) {
        updateHover(tree.root, hoverChain, dsglMouseX, dsglMouseY, dx, dy)
        hoverTarget = hoverChain.lastOrNull()
        if (dragCaptureTarget != null && hasFocusChangedSinceCapture()) {
            releaseDragCapture()
        }
        if (dx != 0 || dy != 0) {
            if (isApplicationRootPointerDragActive()) {
                dispatchApplicationRootPointerDragDelta(
                    tree = tree,
                    mouseX = dsglMouseX,
                    mouseY = dsglMouseY,
                    previousMouseX = prevX,
                    previousMouseY = prevY,
                    dx = dx,
                    dy = dy,
                )
                lastMouseX = dsglMouseX
                lastMouseY = dsglMouseY
            } else {
                DndRuntime.engine.onMouseMove(tree.root, dsglMouseX, dsglMouseY)
                val moveEvent = MouseMoveEvent(dsglMouseX, dsglMouseY, prevX, prevY)
                moveEvent.target = resolveForcedPointerTarget() ?: dragCaptureTarget ?: hoverTarget
                EventBus.post(moveEvent)
            }
        }
    }

    private fun stageApplicationOverlayCommands(
        tree: DomTree,
        applicationOverlayCommands: List<RenderCommand>,
        appOverlayRenderEnabled: Boolean,
        measureContext: UiMeasureContext = adapter,
    ) {
        applicationOverlayCommandsBuffer.clear()
        if (appOverlayRenderEnabled) {
            applicationOverlayCommandsBuffer.addAll(applicationOverlayCommands)
            if (!applicationOverlayHost.hasActiveModalPortal()) {
                DndRuntime.engine.appendPlaceholderCommands(applicationOverlayCommandsBuffer)
                DndRuntime.engine.appendOverlayCommands(
                    tree.root,
                    measureContext,
                    lastWidth,
                    lastHeight,
                    applicationOverlayCommandsBuffer,
                )
            }
            applicationOverlayHost.appendPortalOverlayCommands(
                measureContext = measureContext,
                viewportWidth = lastWidth,
                viewportHeight = lastHeight,
                out = applicationOverlayCommandsBuffer,
            )
            appendInlineColorPickerOverlayCommands(applicationOverlayCommandsBuffer)
        }
    }

    private fun composeAndPresentFrame(
        tree: DomTree,
        commands: List<RenderCommand>,
        debugDomainCommands: DebugDomainCommands,
        rebuiltThisFrame: Boolean,
        layoutCommittedThisFrame: Boolean,
    ) {
        domainOrchestrator.composePaintCommands(
            applicationRoot = commands,
            applicationPortal = applicationOverlayCommandsBuffer,
            systemPortal = systemOverlayCommandsBuffer,
            debugRoot = debugDomainCommands.root,
            debugPortal = debugDomainCommands.portal,
            out = stagingCommandsBuffer,
            shouldRenderSurface = OverlayLayerDebugState::isRenderEnabled,
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
        applicationOverlayHost.closeFloatingPortals()
        systemOverlayHost.clearRefs()
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
        debugDomainRootHost.clearRefs()
        debugDomainPortalHost.clearRefs()
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
            applicationOverlayHost.closeFloatingPortals()
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
        runOverlayInputFrame(applicationOverlayHost)
        runOverlayInputFrame(systemOverlayHost)
        applicationOverlayHost.syncPortalFrame(
            measureContext = adapter,
            viewportWidth = lastWidth,
            viewportHeight = lastHeight,
            viewportScale = 1f,
            mouseX = if (lastMoveX == Int.MIN_VALUE) lastMouseX else lastMoveX,
            mouseY = if (lastMoveY == Int.MIN_VALUE) lastMouseY else lastMoveY,
        )
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
            applicationOverlayHost.toggleFloatingWindowDemo(demoAnchorX, demoAnchorY)
            mc.dispatchKeypresses()
            return true
        }
        if (keyCode == Keyboard.KEY_ESCAPE && inspector.cancelPickMode()) {
            logInspectorInput("escape cancelled inspector pick mode")
            mc.dispatchKeypresses()
            return true
        }
        when (
            dispatchDomainKeyDown(
                keyCode = keyCode,
                keyChar = keyChar,
                inspectorMouseX = inspectorMouseX,
                inspectorMouseY = inspectorMouseY,
            )
        ) {
            DomainKeyDispatchResult.HigherSurfaceConsumed -> {
                mc.dispatchKeypresses()
                return true
            }

            DomainKeyDispatchResult.ApplicationRootHandled, DomainKeyDispatchResult.None -> return false
        }
    }

    private enum class DomainKeyDispatchResult {
        None,
        HigherSurfaceConsumed,
        ApplicationRootHandled,
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
        when (
            dispatchDomainKeyUp(
                keyCode = keyCode,
                keyChar = keyChar,
                inspectorMouseX = inspectorMouseX,
                inspectorMouseY = inspectorMouseY,
            )
        ) {
            DomainKeyDispatchResult.HigherSurfaceConsumed -> {
                mc.dispatchKeypresses()
                return true
            }

            DomainKeyDispatchResult.ApplicationRootHandled, DomainKeyDispatchResult.None -> return false
        }
    }

    override fun handleMouseInput() {
        updateSize(force = false)
        rebuildIfNeeded()
        val tree = prepareMouseInputTree() ?: return
        val inputEvent = readMouseInputEvent()
        syncMouseInputFrame(tree, inputEvent)
        when (dispatchDomainPointerPhase(tree, inputEvent)) {
            DomainPointerDispatchResult.HigherSurfaceConsumed -> return
            DomainPointerDispatchResult.ApplicationRootHandled, DomainPointerDispatchResult.None ->
                finishMouseInputEvent(inputEvent)
        }
    }

    private enum class DomainPointerDispatchResult {
        None,
        HigherSurfaceConsumed,
        ApplicationRootHandled,
    }

    private data class MouseInputEvent(
        val mouseX: Int,
        val mouseY: Int,
        val dWheel: Int,
        val mouseButton: Int,
    )

    private data class DomainPointerDispatchContext(
        val inputEvent: MouseInputEvent,
        val mappedButton: MouseButton?,
        val buttonPressed: Boolean,
        val applicationRootPressMove: Boolean,
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
        applicationOverlayHost.syncPortalFrame(
            measureContext = adapter,
            viewportWidth = lastWidth,
            viewportHeight = lastHeight,
            viewportScale = 1f,
            mouseX = inputEvent.mouseX,
            mouseY = inputEvent.mouseY,
        )
        systemOverlayHost.syncPortalFrame(
            measureContext = adapter,
            viewportWidth = lastWidth,
            viewportHeight = lastHeight,
            viewportScale = 1f,
        )
        runOverlayInputFrame(applicationOverlayHost)
        runOverlayInputFrame(systemOverlayHost)
        runOverlayInputFrame(debugDomainRootHost)
        runOverlayInputFrame(debugDomainPortalHost)
        inspectorPointerCaptured = inspector.isPointerCaptured
        systemOverlayHost.syncFrame(
            inspectedRoot = tree.root,
            inspectedLayoutRevision = layoutRevision,
            cursorX = inputEvent.mouseX,
            cursorY = inputEvent.mouseY,
            inspectorPointerCaptured = inspectorPointerCaptured,
        )
        refreshActiveColorSamplerOwner(tree.root)
    }

    private fun runOverlayInputFrame(host: DomainSurfaceHost) {
        host.onInputFrame(lastWidth, lastHeight)
    }

    private fun dispatchDomainPointerPhase(tree: DomTree, inputEvent: MouseInputEvent): DomainPointerDispatchResult {
        val context =
            DomainPointerDispatchContext(
                inputEvent = inputEvent,
                mappedButton = mapButton(inputEvent.mouseButton),
                buttonPressed = Mouse.getEventButtonState(),
                applicationRootPressMove = inputEvent.mouseButton == -1 && eventButton != -1,
            )
        val consumedBy =
            domainOrchestrator.firstInputConsumer(
                canConsume = { surface ->
                    consumeDomainPointerSurface(surface = surface, tree = tree, context = context)
                },
                isSurfaceInputEnabled = OverlayLayerDebugState::isInputEnabled,
            )
        return when (consumedBy) {
            null ->
                if (isHigherSurfaceOwnedPointerRelease(context)) {
                    higherSurfacePointerButton = -1
                    consumeOverlayPointerState(
                        mouseX = inputEvent.mouseX,
                        mouseY = inputEvent.mouseY,
                        cancelRootDnd = context.inputEvent.mouseButton != -1,
                    )
                    DomainPointerDispatchResult.HigherSurfaceConsumed
                } else {
                    DomainPointerDispatchResult.None
                }
            ScreenDomainSurfaces.ApplicationRoot -> DomainPointerDispatchResult.ApplicationRootHandled
            else -> {
                updateHigherSurfacePointerOwnership(context)
                consumeOverlayPointerState(
                    mouseX = inputEvent.mouseX,
                    mouseY = inputEvent.mouseY,
                    cancelRootDnd = context.inputEvent.mouseButton != -1,
                )
                DomainPointerDispatchResult.HigherSurfaceConsumed
            }
        }
    }

    private fun consumeDomainPointerSurface(
        surface: ScreenDomainSurface,
        tree: DomTree,
        context: DomainPointerDispatchContext,
    ): Boolean =
        when (surface) {
            ScreenDomainSurfaces.DebugPortal -> consumeDebugPortalPointerSurface(context)
            ScreenDomainSurfaces.DebugRoot -> consumeDebugRootPointerSurface(context)
            ScreenDomainSurfaces.SystemPortal -> consumeSystemPortalPointerSurface(context)
            ScreenDomainSurfaces.SystemRoot -> false
            ScreenDomainSurfaces.ApplicationPortal -> consumeApplicationPortalPointerSurface(context)
            ScreenDomainSurfaces.ApplicationRoot ->
                if (isHigherSurfaceOwnedPointerRelease(context)) {
                    false
                } else {
                    dispatchApplicationRootPointerSurface(
                        tree = tree,
                        inputEvent = context.inputEvent,
                    )
                }

            else -> false
        }

    private fun isHigherSurfaceOwnedPointerRelease(context: DomainPointerDispatchContext): Boolean =
        context.inputEvent.mouseButton != -1 &&
            !context.buttonPressed &&
            context.inputEvent.mouseButton == higherSurfacePointerButton

    private fun updateHigherSurfacePointerOwnership(context: DomainPointerDispatchContext) {
        if (context.inputEvent.mouseButton == -1 || context.mappedButton == null) return
        if (context.buttonPressed) {
            higherSurfacePointerButton = context.inputEvent.mouseButton
        } else if (higherSurfacePointerButton == context.inputEvent.mouseButton) {
            higherSurfacePointerButton = -1
        }
    }

    private fun consumeDebugRootPointerSurface(context: DomainPointerDispatchContext): Boolean {
        if (context.applicationRootPressMove) {
            return false
        }
        return consumeDebugPointerEvent(
            host = debugDomainRootHost,
            mouseX = context.inputEvent.mouseX,
            mouseY = context.inputEvent.mouseY,
            dWheel = context.inputEvent.dWheel,
            mappedButton = context.mappedButton,
            mouseButton = context.inputEvent.mouseButton,
            buttonPressed = context.buttonPressed,
        )
    }

    private fun consumeDebugPortalPointerSurface(context: DomainPointerDispatchContext): Boolean {
        if (context.applicationRootPressMove) {
            return false
        }
        return consumeDebugPointerEvent(
            host = debugDomainPortalHost,
            mouseX = context.inputEvent.mouseX,
            mouseY = context.inputEvent.mouseY,
            dWheel = context.inputEvent.dWheel,
            mappedButton = context.mappedButton,
            mouseButton = context.inputEvent.mouseButton,
            buttonPressed = context.buttonPressed,
        )
    }

    private fun consumeSystemPortalPointerSurface(context: DomainPointerDispatchContext): Boolean {
        if (context.applicationRootPressMove) {
            return false
        }
        return consumeSystemOverlayPointerEvent(
            mouseX = context.inputEvent.mouseX,
            mouseY = context.inputEvent.mouseY,
            dWheel = context.inputEvent.dWheel,
            mouseButton = context.inputEvent.mouseButton,
            mappedButton = context.mappedButton,
            buttonPressed = context.buttonPressed,
        )
    }

    private fun consumeApplicationPortalPointerSurface(context: DomainPointerDispatchContext): Boolean {
        if (context.applicationRootPressMove) {
            return false
        }
        return consumeApplicationOverlayPointerEvent(
            mouseX = context.inputEvent.mouseX,
            mouseY = context.inputEvent.mouseY,
            dWheel = context.inputEvent.dWheel,
            mouseButton = context.inputEvent.mouseButton,
            mappedButton = context.mappedButton,
            buttonPressed = context.buttonPressed,
        )
    }

    private fun dispatchApplicationRootPointerSurface(tree: DomTree, inputEvent: MouseInputEvent): Boolean {
        refreshHoverTarget(inputEvent.mouseX, inputEvent.mouseY)
        if (inputEvent.mouseButton > 2) {
            return false
        }
        val pointerHandled = dispatchApplicationRootPointerPhase(tree, inputEvent)
        val wheelHandled = dispatchApplicationRootWheelPhase(inputEvent)
        return pointerHandled || wheelHandled
    }

    private fun dispatchApplicationRootPointerPhase(tree: DomTree, inputEvent: MouseInputEvent): Boolean {
        if (inputEvent.mouseButton != -1 && Mouse.getEventButtonState()) {
            dispatchApplicationRootPointerDown(tree, inputEvent, Minecraft.getSystemTime())
            return true
        } else if (inputEvent.mouseButton != -1 && eventButton == inputEvent.mouseButton) {
            dispatchApplicationRootPointerUp(tree, inputEvent)
            return true
        } else if (eventButton != -1 && lastMouseEvent > 0L) {
            dispatchApplicationRootPointerDrag(tree, inputEvent)
            return true
        }
        return false
    }

    private fun dispatchApplicationRootPointerDown(tree: DomTree, inputEvent: MouseInputEvent, eventTimeMs: Long) {
        eventButton = inputEvent.mouseButton
        lastMouseEvent = eventTimeMs
        mapButton(inputEvent.mouseButton)?.let { mappedButton ->
            val event = MouseDownEvent(inputEvent.mouseX, inputEvent.mouseY, mappedButton)
            event.target = resolvePointerDownTarget()
            EventBus.post(event)
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
                if (captureTarget == null && !event.cancelled) {
                    DndRuntime.engine.onMouseDown(tree.root, event.target ?: hoverTarget, event)
                }
            } else if (!event.cancelled) {
                DndRuntime.engine.onMouseDown(tree.root, event.target ?: hoverTarget, event)
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
                dispatchApplicationRootPointerDragDelta(
                    tree = tree,
                    mouseX = inputEvent.mouseX,
                    mouseY = inputEvent.mouseY,
                    previousMouseX = lastMouseX,
                    previousMouseY = lastMouseY,
                    dx = dx,
                    dy = dy,
                    mappedButton = mappedButton,
                )
            }
        }
    }

    private fun dispatchApplicationRootPointerDragDelta(
        tree: DomTree,
        mouseX: Int,
        mouseY: Int,
        previousMouseX: Int,
        previousMouseY: Int,
        dx: Int,
        dy: Int,
        mappedButton: MouseButton? = mapButton(eventButton),
    ) {
        val button = mappedButton ?: return
        val captured = dragCaptureTarget
        if (captured == null) {
            DndRuntime.engine.onMouseMove(tree.root, mouseX, mouseY)
        }
        val dragEvent =
            MouseDragEvent(
                previousMouseX,
                previousMouseY,
                dx,
                dy,
                button,
            )
        if (captured != null || !DndRuntime.engine.isDragging) {
            dragEvent.target = captured ?: hoverTarget
            EventBus.post(dragEvent)
        }
        captured?.continuePointerCapture(
            mouseX = mouseX,
            mouseY = mouseY,
            mouseDX = dx,
            mouseDY = dy,
            button = button,
        )
    }

    private fun dispatchApplicationRootWheelPhase(inputEvent: MouseInputEvent): Boolean {
        if (inputEvent.dWheel != 0) {
            val wheelTarget = resolveWheelTarget()
            if (wheelTarget != null) {
                val wheelEvent = MouseWheelEvent(inputEvent.mouseX, inputEvent.mouseY, inputEvent.dWheel)
                wheelEvent.target = wheelTarget
                EventBus.post(wheelEvent)
                if (!wheelEvent.cancelled) {
                    bubbleGenericWheel(wheelTarget, inputEvent.mouseX, inputEvent.mouseY, inputEvent.dWheel)
                }
                return true
            }
        }
        return false
    }

    private fun finishMouseInputEvent(inputEvent: MouseInputEvent) {
        lastMouseX = inputEvent.mouseX
        lastMouseY = inputEvent.mouseY
    }

    private fun dispatchDomainKeyDown(
        keyCode: Int,
        keyChar: Char,
        inspectorMouseX: Int,
        inspectorMouseY: Int,
    ): DomainKeyDispatchResult {
        val consumedBy =
            domainOrchestrator.firstInputConsumer(
                canConsume = { surface ->
                    when (surface) {
                        ScreenDomainSurfaces.DebugPortal -> debugDomainPortalHost.handleKeyDown(keyCode, keyChar)
                        ScreenDomainSurfaces.DebugRoot -> debugDomainRootHost.handleKeyDown(keyCode, keyChar)

                        ScreenDomainSurfaces.SystemPortal ->
                            consumeSystemOverlayKeyDown(
                                keyCode = keyCode,
                                keyChar = keyChar,
                                inspectorMouseX = inspectorMouseX,
                                inspectorMouseY = inspectorMouseY,
                            )

                        ScreenDomainSurfaces.ApplicationPortal -> consumeApplicationOverlayKeyDown(keyCode, keyChar)
                        ScreenDomainSurfaces.ApplicationRoot -> {
                            dispatchApplicationRootKeyDown(keyCode, keyChar)
                            true
                        }
                        ScreenDomainSurfaces.SystemRoot -> false
                        else -> false
                    }
                },
                isSurfaceInputEnabled = OverlayLayerDebugState::isInputEnabled,
            )
        return when (consumedBy) {
            null -> DomainKeyDispatchResult.None
            ScreenDomainSurfaces.ApplicationRoot -> DomainKeyDispatchResult.ApplicationRootHandled
            else -> DomainKeyDispatchResult.HigherSurfaceConsumed
        }
    }

    private fun dispatchDomainKeyUp(
        keyCode: Int,
        keyChar: Char,
        inspectorMouseX: Int,
        inspectorMouseY: Int,
    ): DomainKeyDispatchResult {
        val consumedBy =
            domainOrchestrator.firstInputConsumer(
                canConsume = { surface ->
                    when (surface) {
                        ScreenDomainSurfaces.DebugPortal -> debugDomainPortalHost.handleKeyUp(keyCode, keyChar)
                        ScreenDomainSurfaces.DebugRoot -> debugDomainRootHost.handleKeyUp(keyCode, keyChar)

                        ScreenDomainSurfaces.SystemPortal ->
                            consumeSystemOverlayKeyUp(
                                keyCode = keyCode,
                                keyChar = keyChar,
                                inspectorMouseX = inspectorMouseX,
                                inspectorMouseY = inspectorMouseY,
                            )

                        ScreenDomainSurfaces.ApplicationPortal -> consumeApplicationOverlayKeyUp(keyCode, keyChar)
                        ScreenDomainSurfaces.ApplicationRoot -> dispatchApplicationRootKeyUp(keyCode, keyChar)
                        ScreenDomainSurfaces.SystemRoot -> false
                        else -> false
                    }
                },
                isSurfaceInputEnabled = OverlayLayerDebugState::isInputEnabled,
            )
        return when (consumedBy) {
            null -> DomainKeyDispatchResult.None
            ScreenDomainSurfaces.ApplicationRoot -> DomainKeyDispatchResult.ApplicationRootHandled
            else -> DomainKeyDispatchResult.HigherSurfaceConsumed
        }
    }

    private fun dispatchApplicationRootKeyDown(keyCode: Int, keyChar: Char) {
        // TODO(Veritaris): remove this handling from production build
        if (keyCode == Keyboard.KEY_F6) {
            StyleEngine.forceReloadStylesheets()
            requestRebuild("style reload")
        }
        if (pressedKeys.add(keyCode)) {
            val downEvent = dispatchFocusedApplicationRootKeyDown(keyCode, keyChar)
            if (!downEvent.cancelled && keyCode == Keyboard.KEY_ESCAPE && downEvent.target != null) {
                FocusManager.clearFocus()
                downEvent.cancelled = true
            }
            if (downEvent.cancelled) {
                pressedKeys.remove(keyCode)
            } else {
                window.onKeyTyped(keyChar, keyCode)
                if (keyCode == Keyboard.KEY_ESCAPE) {
                    mc.displayGuiScreen(null)
                }
            }
        }
    }

    private fun dispatchApplicationRootKeyUp(keyCode: Int, keyChar: Char): Boolean {
        if (!pressedKeys.remove(keyCode)) return false
        dispatchFocusedApplicationRootKeyUp(keyCode, keyChar)
        return true
    }

    private fun dispatchFocusedApplicationRootKeyDown(keyCode: Int, keyChar: Char): KeyboardKeyDownEvent {
        val downEvent = KeyboardKeyDownEvent(keyChar, keyCode)
        val root = domTree?.root
        if (root != null) {
            downEvent.target = FocusManager.focusedNodeWithin(root)
            if (downEvent.target != null) {
                EventBus.post(downEvent)
            }
        }
        return downEvent
    }

    private fun dispatchFocusedApplicationRootKeyUp(keyCode: Int, keyChar: Char) {
        val root = domTree?.root ?: return
        val focused = FocusManager.focusedNodeWithin(root) ?: return
        val upEvent = KeyboardKeyUpEvent(keyChar, keyCode)
        upEvent.target = focused
        EventBus.post(upEvent)
    }

    private fun consumeSystemOverlayKeyDown(
        keyCode: Int,
        keyChar: Char,
        inspectorMouseX: Int,
        inspectorMouseY: Int,
    ): Boolean {
        if (systemOverlayHost.handlePortalKeyDown(keyCode, keyChar)) {
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

    private fun consumeSystemOverlayKeyUp(
        keyCode: Int,
        keyChar: Char,
        inspectorMouseX: Int,
        inspectorMouseY: Int,
    ): Boolean {
        if (systemOverlayHost.handlePortalKeyUp(keyCode, keyChar)) {
            return true
        }
        if (systemOverlayHost.handleKeyUp(keyCode, keyChar)) {
            return true
        }
        val keyboardBlocked =
            inspector.active &&
                (
                    inspector.shouldConsumeKeyboard(inspectorMouseX, inspectorMouseY) ||
                        inspector.mode == InspectorMode.Locked
                )
        if (keyboardBlocked) {
            logInspectorInput("keyboard up consumed keyCode=$keyCode")
            return true
        }
        return false
    }

    private fun consumeApplicationOverlayKeyDown(keyCode: Int, keyChar: Char): Boolean {
        if (applicationOverlayHost.handlePortalKeyDownBeforeDom(keyCode, keyChar)) {
            return true
        }
        if (applicationOverlayHost.handleKeyDown(keyCode, keyChar)) {
            return true
        }
        if (applicationOverlayHost.handlePortalKeyDownAfterDom(keyCode, keyChar)) {
            return true
        }
        return false
    }

    private fun consumeApplicationOverlayKeyUp(keyCode: Int, keyChar: Char): Boolean {
        if (applicationOverlayHost.handlePortalKeyUpBeforeDom(keyCode, keyChar)) {
            return true
        }
        if (applicationOverlayHost.handleKeyUp(keyCode, keyChar)) {
            return true
        }
        if (applicationOverlayHost.handlePortalKeyUpAfterDom(keyCode, keyChar)) {
            return true
        }
        return false
    }

    private fun consumeDebugPointerEvent(
        host: DomainSurfaceHost,
        mouseX: Int,
        mouseY: Int,
        dWheel: Int,
        mappedButton: MouseButton?,
        mouseButton: Int,
        buttonPressed: Boolean,
    ): Boolean {
        if (dWheel != 0 && host.handleMouseWheel(mouseX, mouseY, dWheel)) {
            return true
        }
        if (mouseButton != -1 && mappedButton != null) {
            return if (buttonPressed) {
                host.handleMouseDown(mouseX, mouseY, mappedButton)
            } else {
                host.handleMouseUp(mouseX, mouseY, mappedButton)
            }
        }
        if (mouseButton == -1 && host.handleMouseMove(mouseX, mouseY)) {
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
        if (dWheel != 0 && systemOverlayHost.handlePortalMouseWheel(mouseX, mouseY, dWheel)) {
            return true
        }
        if (dWheel != 0 && systemOverlayHost.handleMouseWheel(mouseX, mouseY, dWheel)) {
            return true
        }
        if (mouseButton != -1 && mappedButton != null) {
            val consumedBySystemSelect =
                if (buttonPressed) {
                    systemOverlayHost.handlePortalMouseDown(mouseX, mouseY, mappedButton)
                } else {
                    systemOverlayHost.handlePortalMouseUp(mouseX, mouseY, mappedButton)
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
        } else if (mouseButton == -1 && systemOverlayHost.handlePortalMouseMove(mouseX, mouseY)) {
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
            if (
                applicationOverlayHost.handlePortalPointerBeforeDom(
                    mouseX = mouseX,
                    mouseY = mouseY,
                    dWheel = dWheel,
                    button = mappedButton,
                    pressed = buttonPressed,
                )
            ) {
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

        return applicationOverlayHost.handlePortalPointerAfterDom(
            mouseX = mouseX,
            mouseY = mouseY,
            dWheel = dWheel,
            button = mappedButton,
            pressed = buttonPressed,
        )
    }

    private fun consumeOverlayPointerState(mouseX: Int, mouseY: Int, cancelRootDnd: Boolean = false) {
        if (cancelRootDnd) {
            DndRuntime.engine.cancelActiveDrag()
        }
        eventButton = -1
        clearActiveTarget()
        releaseDragCapture()
        clearHoverChainStates(postLeaveEvents = true, mouseX = mouseX, mouseY = mouseY)
        hoverTarget = null
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

    private fun isApplicationRootPointerDragActive(): Boolean = eventButton != -1 && lastMouseEvent > 0L

    init {
        inspector.installColorPickerPortalService(systemOverlayHost.systemInspectorColorPickerService())
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
                popupEyedropperActive = applicationOverlayHost.hasActiveColorPickerEyedropper(),
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
        val surface = ScreenDomainSurfaces.portalSurfaceForOwner(OverlayOwnerScope.Application)
        if (surface != ScreenDomainSurfaces.ApplicationPortal) return
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
        if (ScreenDomainSurfaces.portalSurfaceForOwner(OverlayOwnerScope.System) == ScreenDomainSurfaces.SystemPortal) {
            systemOverlayHost.captureSystemColorPickerEyedropperSample()
        }
        if (ScreenDomainSurfaces.portalSurfaceForOwner(OverlayOwnerScope.Application) !=
            ScreenDomainSurfaces.ApplicationPortal
        ) {
            return
        }
        when (activeColorSamplerOwner) {
            ActiveColorSamplerOwner.Popup -> applicationOverlayHost.captureColorPickerEyedropperSample()
            is ActiveColorSamplerOwner.Inline -> {
                val inline = activeInlineColorSamplerNode
                if (inline != null && inline.wantsGlobalPointerInput()) {
                    inline.captureEyedropperSample()
                }
            }

            ActiveColorSamplerOwner.None -> {
                if (applicationOverlayHost.hasActiveColorPickerEyedropper()) {
                    applicationOverlayHost.captureColorPickerEyedropperSample()
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

    internal fun debugComposeDomainPaintCommandsForTests(
        applicationRoot: List<RenderCommand>,
        applicationPortal: List<RenderCommand>,
        systemRoot: List<RenderCommand> = emptyList(),
        systemPortal: List<RenderCommand>,
        debugRoot: List<RenderCommand>,
        debugPortal: List<RenderCommand> = emptyList(),
        shouldRenderSurface: (ScreenDomainSurface) -> Boolean = { true },
    ): List<RenderCommand> {
        val out = ArrayList<RenderCommand>()
        domainOrchestrator.composePaintCommands(
            applicationRoot = applicationRoot,
            applicationPortal = applicationPortal,
            systemRoot = systemRoot,
            systemPortal = systemPortal,
            debugRoot = debugRoot,
            debugPortal = debugPortal,
            out = out,
            shouldRenderSurface = shouldRenderSurface,
        )
        return out
    }

    internal fun debugStageApplicationOverlayCommandsForTests(
        tree: DomTree,
        applicationOverlayCommands: List<RenderCommand>,
        appOverlayRenderEnabled: Boolean = true,
        measureContext: UiMeasureContext,
    ): List<RenderCommand> {
        stageApplicationOverlayCommands(
            tree = tree,
            applicationOverlayCommands = applicationOverlayCommands,
            appOverlayRenderEnabled = appOverlayRenderEnabled,
            measureContext = measureContext,
        )
        return applicationOverlayCommandsBuffer.toList()
    }

    internal fun debugFirstDomainInputConsumerForTests(
        canConsume: (ScreenDomainSurface) -> Boolean,
        isSurfaceInputEnabled: (ScreenDomainSurface) -> Boolean = { true },
    ): ScreenDomainSurface? =
        domainOrchestrator.firstInputConsumer(
            canConsume = canConsume,
            isSurfaceInputEnabled = isSurfaceInputEnabled,
        )

    internal fun debugDispatchApplicationPortalThenRootPointerForTests(
        mouseButton: Int,
        buttonPressed: Boolean,
        mouseX: Int = 0,
        mouseY: Int = 0,
        applicationPortalConsumes: () -> Boolean,
        applicationRootConsumes: () -> Boolean,
    ): ScreenDomainSurface? {
        val context =
            DomainPointerDispatchContext(
                inputEvent =
                    MouseInputEvent(
                        mouseX = mouseX,
                        mouseY = mouseY,
                        dWheel = 0,
                        mouseButton = mouseButton,
                    ),
                mappedButton = mapButton(mouseButton),
                buttonPressed = buttonPressed,
                applicationRootPressMove = false,
            )
        val consumedBy =
            domainOrchestrator.firstInputConsumer(
                canConsume = { surface ->
                    when (surface) {
                        ScreenDomainSurfaces.ApplicationPortal -> applicationPortalConsumes()
                        ScreenDomainSurfaces.ApplicationRoot ->
                            if (isHigherSurfaceOwnedPointerRelease(context)) {
                                false
                            } else {
                                applicationRootConsumes()
                            }

                        else -> false
                    }
                },
            )
        if (consumedBy != null && consumedBy != ScreenDomainSurfaces.ApplicationRoot) {
            updateHigherSurfacePointerOwnership(context)
            consumeOverlayPointerState(
                mouseX = mouseX,
                mouseY = mouseY,
                cancelRootDnd = context.inputEvent.mouseButton != -1,
            )
        } else if (consumedBy == null && isHigherSurfaceOwnedPointerRelease(context)) {
            higherSurfacePointerButton = -1
            consumeOverlayPointerState(
                mouseX = mouseX,
                mouseY = mouseY,
                cancelRootDnd = context.inputEvent.mouseButton != -1,
            )
            return ScreenDomainSurfaces.ApplicationPortal
        }
        return consumedBy
    }

    internal fun debugApplicationOverlayHostForTests(): ApplicationOverlayHost = applicationOverlayHost

    internal fun debugUpdateFrameInteractionStateForTests(
        tree: DomTree,
        mouseX: Int,
        mouseY: Int,
        appOverlayInputEnabled: Boolean = true,
        systemOverlayInputEnabled: Boolean = true,
        inspectorBlocks: Boolean = false,
    ) {
        updateFrameInteractionState(
            tree = tree,
            dtSeconds = 1.0 / 60.0,
            dsglMouseX = mouseX,
            dsglMouseY = mouseY,
            appOverlayInputEnabled = appOverlayInputEnabled,
            systemOverlayInputEnabled = systemOverlayInputEnabled,
            inspectorBlocks = inspectorBlocks,
        )
    }

    internal fun debugCancelApplicationRootDndBehindModalForTests() {
        cancelApplicationRootDndBehindModal()
    }

    internal fun debugDispatchApplicationRootPointerDownForTests(
        tree: DomTree,
        mouseX: Int,
        mouseY: Int,
        mouseButton: Int = 0,
    ) {
        val inputEvent =
            MouseInputEvent(
                mouseX = mouseX,
                mouseY = mouseY,
                dWheel = 0,
                mouseButton = mouseButton,
            )
        refreshHoverTarget(mouseX, mouseY)
        dispatchApplicationRootPointerDown(tree, inputEvent, eventTimeMs = 1L)
        finishMouseInputEvent(inputEvent)
        lastMoveX = mouseX
        lastMoveY = mouseY
    }

    private fun setDragCapture(target: DOMNode) {
        dragCaptureTarget = target
        dragCaptureKey = target.key
        dragCaptureClass = target.javaClass
        dragCaptureFocusKey = FocusManager.focusedNode()?.key
    }

    private fun releaseDragCapture() {
        dragCaptureTarget?.cancelPointerCapture()
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
        val captured = dragCaptureTarget
        val currentFocus = FocusManager.focusedNode()
        if (captured != null && isSameOrAncestor(captured, currentFocus)) return false
        val currentFocusKey = currentFocus?.key
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

    private fun clearHoverChainStates(
        postLeaveEvents: Boolean = false,
        mouseX: Int = lastMoveX,
        mouseY: Int = lastMoveY,
    ) {
        hoverChain.forEach { node ->
            node.setHoveredState(false)
            if (postLeaveEvents) {
                val leave = MouseLeaveEvent(mouseX, mouseY)
                leave.target = node
                EventBus.post(leave)
                node.onmouseleave?.invoke(leave)
            }
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
