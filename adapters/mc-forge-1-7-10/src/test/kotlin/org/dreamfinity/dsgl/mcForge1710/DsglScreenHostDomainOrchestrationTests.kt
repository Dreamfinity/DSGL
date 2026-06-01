package org.dreamfinity.dsgl.mcForge1710

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.colorpicker.ColorFormatMode
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerController
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerLayout
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerStyle
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.components.modal.ModalSpec
import org.dreamfinity.dsgl.core.components.modal.modalPortal
import org.dreamfinity.dsgl.core.dnd.DndRuntime
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ColorPickerInlineNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.RangeInputNode
import org.dreamfinity.dsgl.core.dom.elements.SingleLineInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.dsl.ui
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseLeaveEvent
import org.dreamfinity.dsgl.core.event.MouseMoveEvent
import org.dreamfinity.dsgl.core.overlay.ApplicationOverlayHost
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurface
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.overlay.hasActiveModalPortal
import org.dreamfinity.dsgl.core.overlay.toggleFloatingWindowDemo
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DsglScreenHostDomainOrchestrationTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @Test
    fun `host domain paint orchestration uses six surface render order`() {
        val host = createHost()

        val commands =
            host.debugComposeDomainPaintCommandsForTests(
                applicationRoot = listOf(command(1)),
                applicationPortal = listOf(command(2)),
                systemRoot = listOf(command(3)),
                systemPortal = listOf(command(4)),
                debugRoot = listOf(command(5)),
                debugPortal = listOf(command(6)),
            )

        assertEquals(listOf(1, 2, 3, 4, 5, 6), commandColors(commands))
    }

    @Test
    fun `host domain paint orchestration preserves render debug skips`() {
        val host = createHost()

        val commands =
            host.debugComposeDomainPaintCommandsForTests(
                applicationRoot = listOf(command(1)),
                applicationPortal = listOf(command(2)),
                systemPortal = listOf(command(3)),
                debugRoot = listOf(command(4)),
                shouldRenderSurface = { surface -> surface != ScreenDomainSurfaces.ApplicationPortal },
            )

        assertEquals(listOf(1, 3, 4), commandColors(commands))
    }

    @Test
    fun `host domain input orchestration preserves current priority`() {
        val host = createHost()
        val visited = ArrayList<ScreenDomainSurface>()

        val consumed =
            host.debugFirstDomainInputConsumerForTests(
                canConsume = { layer ->
                    visited += layer
                    layer == ScreenDomainSurfaces.ApplicationRoot
                },
            )

        assertEquals(ScreenDomainSurfaces.ApplicationRoot, consumed)
        assertEquals(
            listOf(
                ScreenDomainSurfaces.DebugPortal,
                ScreenDomainSurfaces.DebugRoot,
                ScreenDomainSurfaces.SystemPortal,
                ScreenDomainSurfaces.SystemRoot,
                ScreenDomainSurfaces.ApplicationPortal,
                ScreenDomainSurfaces.ApplicationRoot,
            ),
            visited,
        )
    }

    @Test
    fun `host domain input orchestration blocks lower domains after consumption`() {
        val host = createHost()
        val visited = ArrayList<ScreenDomainSurface>()

        val consumed =
            host.debugFirstDomainInputConsumerForTests(
                canConsume = { layer ->
                    visited += layer
                    layer == ScreenDomainSurfaces.SystemPortal
                },
            )

        assertEquals(ScreenDomainSurfaces.SystemPortal, consumed)
        assertEquals(
            listOf(
                ScreenDomainSurfaces.DebugPortal,
                ScreenDomainSurfaces.DebugRoot,
                ScreenDomainSurfaces.SystemPortal,
            ),
            visited,
        )
    }

    @Test
    fun `host domain input orchestration preserves debug input disables`() {
        val host = createHost()
        val visited = ArrayList<ScreenDomainSurface>()

        val consumed =
            host.debugFirstDomainInputConsumerForTests(
                canConsume = { layer ->
                    visited += layer
                    layer == ScreenDomainSurfaces.DebugRoot || layer == ScreenDomainSurfaces.ApplicationPortal
                },
                isSurfaceInputEnabled = { surface -> surface != ScreenDomainSurfaces.DebugRoot },
            )

        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumed)
        assertEquals(
            listOf(
                ScreenDomainSurfaces.DebugPortal,
                ScreenDomainSurfaces.SystemPortal,
                ScreenDomainSurfaces.SystemRoot,
                ScreenDomainSurfaces.ApplicationPortal,
            ),
            visited,
        )
    }

    @Test
    fun `host domain input orchestration returns null when no surface consumes`() {
        val host = createHost()

        val consumed = host.debugFirstDomainInputConsumerForTests(canConsume = { false })

        assertNull(consumed)
    }

    @Test
    fun `host suppresses application root release after portal-owned pointer down`() {
        val host = createHost()
        var rootReceivedRelease = false

        val consumedDown =
            host.debugDispatchApplicationPortalThenRootPointerForTests(
                mouseButton = 0,
                buttonPressed = true,
                applicationPortalConsumes = { true },
                applicationRootConsumes = { true },
            )

        val consumedUp =
            host.debugDispatchApplicationPortalThenRootPointerForTests(
                mouseButton = 0,
                buttonPressed = false,
                applicationPortalConsumes = { false },
                applicationRootConsumes = {
                    rootReceivedRelease = true
                    true
                },
            )
        val consumedNextRelease =
            host.debugDispatchApplicationPortalThenRootPointerForTests(
                mouseButton = 0,
                buttonPressed = false,
                applicationPortalConsumes = { false },
                applicationRootConsumes = { true },
            )

        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumedDown)
        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumedUp)
        assertEquals(ScreenDomainSurfaces.ApplicationRoot, consumedNextRelease)
        assertFalse(rootReceivedRelease)
    }

    @Test
    fun `application portal dom target blocks application root frame hover`() {
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 1280, 720) }
        val rootButton =
            ButtonNode("Root", key = "root-button")
                .apply { bounds = Rect(240, 180, 520, 300) }
                .applyParent(root)
        var leaveCount = 0
        EventBus.run {
            rootButton.addEventListener(Events.MOUSELEAVE) { _: MouseLeaveEvent ->
                leaveCount++
            }
        }
        val tree = DomTree(root)
        val host = createHost(tree)
        val applicationOverlay = host.debugApplicationOverlayHostForTests()

        host.debugUpdateFrameInteractionStateForTests(tree, mouseX = 300, mouseY = 230)
        assertSame(rootButton, host.debugHoverTargetForTests())
        assertEquals(0, leaveCount)

        applicationOverlay.onInputFrame(1280, 720)
        applicationOverlay.toggleFloatingWindowDemo(anchorX = 260, anchorY = 200)
        applicationOverlay.render(ctx, 1280, 720)
        val portalBodyX = 540
        val portalBodyY = 370

        host.debugUpdateFrameInteractionStateForTests(
            tree = tree,
            mouseX = portalBodyX,
            mouseY = portalBodyY,
        )
        assertNull(host.debugHoverTargetForTests())
        assertEquals(1, leaveCount)
    }

    @Test
    fun `application portal live pointer consumption clears application root hover immediately`() {
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 1280, 720) }
        val rootButton =
            ButtonNode("Root", key = "root-button")
                .apply { bounds = Rect(240, 180, 520, 300) }
                .applyParent(root)
        var leaveCount = 0
        EventBus.run {
            rootButton.addEventListener(Events.MOUSELEAVE) { _: MouseLeaveEvent ->
                leaveCount++
            }
        }
        val tree = DomTree(root)
        val host = createHost(tree)

        host.debugUpdateFrameInteractionStateForTests(tree, mouseX = 300, mouseY = 230)
        assertSame(rootButton, host.debugHoverTargetForTests())

        val consumedBy =
            host.debugDispatchApplicationPortalThenRootPointerForTests(
                mouseButton = -1,
                buttonPressed = false,
                mouseX = 540,
                mouseY = 370,
                applicationPortalConsumes = { true },
                applicationRootConsumes = { true },
            )

        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumedBy)
        assertNull(host.debugHoverTargetForTests())
        assertEquals(1, leaveCount)
    }

    @Test
    fun `application root frame hover still updates outside application portal dom target`() {
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 1280, 720) }
        val rootButton =
            ButtonNode("Root", key = "root-button")
                .apply { bounds = Rect(620, 500, 120, 32) }
                .applyParent(root)
        val tree = DomTree(root)
        val host = createHost(tree)
        val applicationOverlay = host.debugApplicationOverlayHostForTests()

        applicationOverlay.onInputFrame(1280, 720)
        applicationOverlay.toggleFloatingWindowDemo(anchorX = 260, anchorY = 200)
        applicationOverlay.render(ctx, 1280, 720)

        host.debugUpdateFrameInteractionStateForTests(tree, mouseX = 630, mouseY = 510)

        assertSame(rootButton, host.debugHoverTargetForTests())
    }

    @Test
    fun `application portal dom target clears inline color picker hover before root paint`() {
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 1280, 720) }
        val picker =
            ColorPickerInlineNode(
                controlled = true,
                value = RgbaColor.WHITE,
                mode = ColorFormatMode.RGB,
                alphaEnabled = true,
                key = "picker",
            ).applyParent(root)
        val tree = DomTree(root)
        val host = createHost(tree)
        val applicationOverlay = host.debugApplicationOverlayHostForTests()
        val probeLayout = colorPickerLayoutProbe()
        val style = ColorPickerStyle()
        val hoverX = probeLayout.copyRect.x + 2
        val hoverY = probeLayout.copyRect.y + 2

        tree.render(ctx, 1280, 720)
        host.debugUpdateFrameInteractionStateForTests(tree, mouseX = hoverX, mouseY = hoverY)
        EventBus.post(MouseMoveEvent(hoverX, hoverY, hoverX - 1, hoverY - 1).also { it.target = picker })
        assertRenderColorPresent(tree.paint(ctx), style.buttonHoverColor)

        applicationOverlay.onInputFrame(1280, 720)
        applicationOverlay.toggleFloatingWindowDemo(anchorX = hoverX, anchorY = hoverY)
        applicationOverlay.render(ctx, 1280, 720)
        host.debugUpdateFrameInteractionStateForTests(tree, mouseX = hoverX, mouseY = hoverY)

        assertNull(host.debugHoverTargetForTests())
        assertRenderColorAbsent(tree.paint(ctx), style.buttonHoverColor)
    }

    @Test
    fun `application root frame movement continues captured range drag`() {
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 300, 120) }
        val range =
            RangeInputNode(value = 0L, min = 0L, max = 100L, key = "range")
                .apply {
                    bounds = Rect(20, 40, 100, 12)
                }.applyParent(root)
        val tree = DomTree(root)
        val host = createHost(tree)

        host.debugDispatchApplicationRootPointerDownForTests(tree, mouseX = 20, mouseY = 46)
        host.debugUpdateFrameInteractionStateForTests(tree, mouseX = 120, mouseY = 46)

        assertEquals(100L, range.value)
    }

    @Test
    fun `application root frame movement continues captured text selection drag`() {
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 300, 120) }
        val input =
            SingleLineInputNode(text = "abcdef", key = "text-input")
                .apply {
                    bounds = Rect(20, 40, 120, 12)
                }.applyParent(root)
        val tree = DomTree(root)
        val host = createHost(tree)

        host.debugDispatchApplicationRootPointerDownForTests(tree, mouseX = 20, mouseY = 46)
        host.debugUpdateFrameInteractionStateForTests(tree, mouseX = 56, mouseY = 46)

        assertRenderColorPresent(input.buildCommandsForTest(), input.selectionColor)
    }

    @Test
    fun `application portal pointer down clears pending root dnd before frame movement`() {
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 300, 120) }
        val draggable =
            ContainerNode(key = "draggable")
                .apply {
                    draggable = true
                    bounds = Rect(20, 40, 80, 20)
                }.applyParent(root)
        val tree = DomTree(root)
        val host = createHost(tree)
        val down =
            MouseDownEvent(24, 44, MouseButton.LEFT)
                .also { event ->
                    event.target = draggable
                }

        DndRuntime.engine.cancelActiveDrag()
        try {
            DndRuntime.engine.onMouseDown(root, draggable, down)
            assertTrue(DndRuntime.engine.isPointerCaptured)

            host.debugDispatchApplicationPortalThenRootPointerForTests(
                mouseButton = 0,
                buttonPressed = true,
                mouseX = 60,
                mouseY = 60,
                applicationPortalConsumes = { true },
                applicationRootConsumes = { true },
            )
            DndRuntime.engine.onMouseMove(root, 120, 60)

            assertFalse(DndRuntime.engine.isPointerCaptured)
            assertFalse(DndRuntime.engine.isDragging)
        } finally {
            DndRuntime.engine.cancelActiveDrag()
        }
    }

    @Test
    fun `active application modal cancels root dnd before ghost can render`() {
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 300, 120) }
        val draggable =
            ContainerNode(key = "draggable")
                .apply {
                    draggable = true
                    bounds = Rect(20, 40, 80, 20)
                }.applyParent(root)
        val tree = DomTree(root)
        val host = createHost(tree)
        val overlay = host.debugApplicationOverlayHostForTests()
        val modalKey = "tests.host.modal.cancel.dnd"
        val down =
            MouseDownEvent(24, 44, MouseButton.LEFT)
                .also { event ->
                    event.target = draggable
                }

        DndRuntime.engine.cancelActiveDrag()
        try {
            DndRuntime.engine.onMouseDown(root, draggable, down)
            DndRuntime.engine.onMouseMove(root, 120, 60)
            assertTrue(DndRuntime.engine.isDragging)

            val modalTree =
                ui {
                    modalPortal(
                        modals =
                            listOf(
                                ModalSpec(key = "static-modal") {
                                    text("Static")
                                },
                            ),
                        key = modalKey,
                    ) {
                        text("content")
                    }
                }
            modalTree.render(ctx, 300, 120)
            overlay.render(ctx, 300, 120)
            assertTrue(overlay.hasActiveModalPortal())

            host.debugCancelApplicationRootDndBehindModalForTests()

            assertFalse(DndRuntime.engine.isPointerCaptured)
            assertFalse(DndRuntime.engine.isDragging)
        } finally {
            val emptyModalTree =
                ui {
                    modalPortal(modals = emptyList(), key = modalKey) {
                        text("content")
                    }
                }
            emptyModalTree.render(ctx, 300, 120)
            overlay.render(ctx, 300, 120)
            DndRuntime.engine.cancelActiveDrag()
        }
    }

    @Test
    fun `active application modal frame blocks and cancels root dnd`() {
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 300, 120) }
        val draggable =
            ContainerNode(key = "draggable")
                .apply {
                    draggable = true
                    bounds = Rect(20, 40, 80, 20)
                }.applyParent(root)
        val tree = DomTree(root)
        val host = createHost(tree)
        val overlay = host.debugApplicationOverlayHostForTests()
        val modalKey = "tests.host.modal.frame.blocks.dnd"
        val down =
            MouseDownEvent(24, 44, MouseButton.LEFT)
                .also { event ->
                    event.target = draggable
                }

        DndRuntime.engine.cancelActiveDrag()
        try {
            activateStaticModal(overlay, modalKey)
            DndRuntime.engine.onMouseDown(root, draggable, down)
            DndRuntime.engine.onMouseMove(root, 120, 60)
            assertTrue(DndRuntime.engine.isDragging)

            host.debugUpdateFrameInteractionStateForTests(tree, mouseX = 120, mouseY = 60)

            assertFalse(DndRuntime.engine.isPointerCaptured)
            assertFalse(DndRuntime.engine.isDragging)
        } finally {
            clearStaticModal(overlay, modalKey)
            DndRuntime.engine.cancelActiveDrag()
        }
    }

    @Test
    fun `active application modal suppresses root dnd ghost commands`() {
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 300, 120) }
        val draggable =
            ContainerNode(key = "draggable")
                .apply {
                    draggable = true
                    bounds = Rect(20, 40, 80, 20)
                }.applyParent(root)
        val tree = DomTree(root)
        val host = createHost(tree)
        val overlay = host.debugApplicationOverlayHostForTests()
        val modalKey = "tests.host.modal.suppresses.ghost.commands"
        val down =
            MouseDownEvent(24, 44, MouseButton.LEFT)
                .also { event ->
                    event.target = draggable
                }

        DndRuntime.engine.cancelActiveDrag()
        try {
            activateStaticModal(overlay, modalKey)
            DndRuntime.engine.onMouseDown(root, draggable, down)
            DndRuntime.engine.onMouseMove(root, 120, 60)
            assertTrue(DndRuntime.engine.isDragging)
            assertTrue(overlay.hasActiveModalPortal())

            val staged =
                host.debugStageApplicationOverlayCommandsForTests(
                    tree = tree,
                    applicationOverlayCommands = overlay.paint(ctx),
                    measureContext = ctx,
                )

            assertFalse(
                staged.any { command ->
                    command is RenderCommand.DrawText && command.text == "drag"
                },
            )
        } finally {
            clearStaticModal(overlay, modalKey)
            DndRuntime.engine.cancelActiveDrag()
        }
    }

    @Test
    fun `application portal pointer release clears active root dnd`() {
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 300, 120) }
        val draggable =
            ContainerNode(key = "draggable")
                .apply {
                    draggable = true
                    bounds = Rect(20, 40, 80, 20)
                }.applyParent(root)
        val tree = DomTree(root)
        val host = createHost(tree)
        val down =
            MouseDownEvent(24, 44, MouseButton.LEFT)
                .also { event ->
                    event.target = draggable
                }

        DndRuntime.engine.cancelActiveDrag()
        try {
            host.debugDispatchApplicationPortalThenRootPointerForTests(
                mouseButton = 0,
                buttonPressed = true,
                mouseX = 60,
                mouseY = 60,
                applicationPortalConsumes = { true },
                applicationRootConsumes = { true },
            )
            DndRuntime.engine.onMouseDown(root, draggable, down)
            DndRuntime.engine.onMouseMove(root, 120, 60)
            assertTrue(DndRuntime.engine.isDragging)

            host.debugDispatchApplicationPortalThenRootPointerForTests(
                mouseButton = 0,
                buttonPressed = false,
                mouseX = 120,
                mouseY = 60,
                applicationPortalConsumes = { true },
                applicationRootConsumes = { true },
            )

            assertFalse(DndRuntime.engine.isPointerCaptured)
            assertFalse(DndRuntime.engine.isDragging)
        } finally {
            DndRuntime.engine.cancelActiveDrag()
        }
    }

    private fun createHost(): DsglScreenHost = createHost(DomTree(ContainerNode(key = "root")))

    private fun createHost(tree: DomTree): DsglScreenHost =
        object : DsglScreenHost(
            object : DsglWindow() {
                override fun render(): DomTree = tree
            },
        ) {}.also { host ->
            host.debugBindTreeForTests(tree, needsLayout = false)
        }

    private fun command(color: Int): RenderCommand = RenderCommand.DrawRect(0, 0, 1, 1, color)

    private fun commandColors(commands: List<RenderCommand>): List<Int> =
        commands.map { command ->
            (command as RenderCommand.DrawRect).color
        }

    private fun assertRenderColorPresent(commands: List<RenderCommand>, color: Int) {
        assertEquals(true, commands.any { command -> command is RenderCommand.DrawRect && command.color == color })
    }

    private fun assertRenderColorAbsent(commands: List<RenderCommand>, color: Int) {
        assertEquals(false, commands.any { command -> command is RenderCommand.DrawRect && command.color == color })
    }

    private fun SingleLineInputNode.buildCommandsForTest(): List<RenderCommand> =
        ArrayList<RenderCommand>().also { out ->
            buildRenderCommands(ctx, out)
        }

    private fun activateStaticModal(overlay: ApplicationOverlayHost, modalKey: String) {
        val modalTree =
            ui {
                modalPortal(
                    modals =
                        listOf(
                            ModalSpec(key = "static-modal") {
                                text("Static")
                            },
                        ),
                    key = modalKey,
                ) {
                    text("content")
                }
            }
        modalTree.render(ctx, 300, 120)
        overlay.render(ctx, 300, 120)
        assertTrue(overlay.hasActiveModalPortal())
    }

    private fun clearStaticModal(overlay: ApplicationOverlayHost, modalKey: String) {
        val emptyModalTree =
            ui {
                modalPortal(modals = emptyList(), key = modalKey) {
                    text("content")
                }
            }
        emptyModalTree.render(ctx, 300, 120)
        overlay.render(ctx, 300, 120)
    }

    private fun colorPickerLayoutProbe(): ColorPickerLayout =
        ColorPickerController(
            initial =
                ColorPickerState(
                    color = RgbaColor.WHITE,
                    previous = RgbaColor.WHITE,
                    mode = ColorFormatMode.RGB,
                    alphaEnabled = true,
                    closeOnSelect = false,
                ),
        ).buildLayout(Rect(0, 0, 1280, 392))
}
