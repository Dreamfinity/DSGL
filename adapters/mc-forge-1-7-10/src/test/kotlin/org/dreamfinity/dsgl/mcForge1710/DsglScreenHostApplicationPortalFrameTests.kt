package org.dreamfinity.dsgl.mcForge1710

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.components.modal.BackdropMode
import org.dreamfinity.dsgl.core.components.modal.ModalSpec
import org.dreamfinity.dsgl.core.components.modal.modalPortal
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dsl.button
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.dsl.ui
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.portal.hasActiveModalPortal
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DsglScreenHostApplicationPortalFrameTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @After
    fun cleanup() {
        StyleEngine.setStylesDirectory(null)
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `application modal frame movement clears stale hovered portal commands before staging`() {
        val hoverColor = 0xFF_12_34_56.toInt()
        installStylesheet(
            """
            button:hover {
              background-color: #123456;
            }
            """.trimIndent(),
        )
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 300, 120) }
        val tree = DomTree(root)
        val host = createHost(tree)
        val applicationPortal = host.debugApplicationPortalHostForTests()
        val modalKey = "tests.host.modal.frame.hover.commands"

        try {
            renderStaticModalWithButton(modalKey)
            host.debugSyncApplicationPortalSurfaceForTests(ctx, width = 300, height = 120)

            assertTrue(applicationPortal.hasActiveModalPortal())
            assertTrue(applicationPortal.handleMouseMove(76, 25))
            assertRenderColorPresent(
                host.debugCollectApplicationPortalCommandsForTests(ctx),
                hoverColor,
            )

            host.debugUpdateFrameInteractionStateForTests(tree, mouseX = 4, mouseY = 4)
            val settledCommands = host.debugCollectApplicationPortalCommandsForTests(ctx)
            val stagedCommands =
                host.debugStageApplicationPortalCommandsForTests(
                    tree = tree,
                    applicationPortalCommands = settledCommands,
                    measureContext = ctx,
                )

            assertRenderColorAbsent(settledCommands, hoverColor)
            assertRenderColorAbsent(stagedCommands, hoverColor)
        } finally {
            renderEmptyModal(modalKey)
            host.debugSyncApplicationPortalSurfaceForTests(ctx, width = 300, height = 120)
        }
    }

    @Test
    fun `application modal button click updates portal commands after root repaint in same frame`() {
        val modalKey = "tests.host.modal.frame.click.commands"
        var clicked = false
        var tree = renderClickStateModal(modalKey = modalKey, clicked = clicked, onClick = { clicked = true })
        val host = createHost(tree)
        val applicationPortal = host.debugApplicationPortalHostForTests()

        try {
            host.debugSyncApplicationPortalSurfaceForTests(ctx, width = 300, height = 120)
            assertTrue(applicationPortal.hasActiveModalPortal())
            assertRenderTextPresent(host.debugCollectApplicationPortalCommandsForTests(ctx), "Before")

            assertTrue(applicationPortal.handleMouseDown(76, 25, MouseButton.LEFT))
            assertTrue(applicationPortal.handleMouseUp(76, 25, MouseButton.LEFT))
            assertTrue(clicked)

            tree = renderClickStateModal(modalKey = modalKey, clicked = clicked, onClick = { clicked = true })
            host.debugBindTreeForTests(tree, needsLayout = false)
            host.debugSyncApplicationPortalSurfaceForTests(ctx, width = 300, height = 120)

            val finalPortalCommands = host.debugCollectApplicationPortalCommandsForTests(ctx)
            assertRenderTextAbsent(finalPortalCommands, "Before")
            assertRenderTextPresent(finalPortalCommands, "After")
        } finally {
            renderEmptyModal(modalKey)
            host.debugSyncApplicationPortalSurfaceForTests(ctx, width = 300, height = 120)
        }
    }

    private fun renderStaticModalWithButton(modalKey: String) {
        val modalTree =
            ui {
                modalPortal(
                    modals =
                        listOf(
                            ModalSpec(
                                key = "static-modal",
                                backdrop = BackdropMode.Static,
                                keyboard = false,
                            ) {
                                button("Hover", {
                                    key = "$modalKey.button"
                                    onMouseClick = {}
                                })
                            },
                        ),
                    key = modalKey,
                ) {
                    text("content")
                }
            }
        modalTree.render(ctx, 300, 120)
    }

    private fun renderClickStateModal(modalKey: String, clicked: Boolean, onClick: () -> Unit): DomTree {
        val modalTree =
            ui {
                modalPortal(
                    modals =
                        listOf(
                            ModalSpec(
                                key = "click-modal",
                                backdrop = BackdropMode.Static,
                                keyboard = false,
                            ) {
                                button(if (clicked) "After" else "Before", {
                                    key = "$modalKey.button"
                                    onMouseClick = { onClick() }
                                })
                            },
                        ),
                    key = modalKey,
                ) {
                    text("content")
                }
            }
        modalTree.render(ctx, 300, 120)
        return modalTree
    }

    private fun renderEmptyModal(modalKey: String) {
        val modalTree =
            ui {
                modalPortal(modals = emptyList(), key = modalKey) {
                    text("content")
                }
            }
        modalTree.render(ctx, 300, 120)
    }

    private fun createHost(tree: DomTree): DsglScreenHost =
        object : DsglScreenHost(
            object : DsglWindow() {
                override fun render(): DomTree = tree
            },
        ) {}.also { host ->
            host.debugBindTreeForTests(tree, needsLayout = false)
        }

    private fun installStylesheet(contents: String) {
        val dir = Files.createTempDirectory("dsgl-host-domain-test").toFile()
        dir.resolve("test.dss").writeText(contents)
        StyleEngine.setStylesDirectory(dir)
        StyleEngine.forceReloadStylesheets()
    }

    private fun assertRenderColorPresent(commands: List<RenderCommand>, color: Int) {
        assertTrue(commands.any { command -> command is RenderCommand.DrawRect && command.color == color })
    }

    private fun assertRenderColorAbsent(commands: List<RenderCommand>, color: Int) {
        assertFalse(commands.any { command -> command is RenderCommand.DrawRect && command.color == color })
    }

    private fun assertRenderTextPresent(commands: List<RenderCommand>, text: String) {
        assertTrue(commands.any { command -> command is RenderCommand.DrawText && command.text == text })
    }

    private fun assertRenderTextAbsent(commands: List<RenderCommand>, text: String) {
        assertFalse(commands.any { command -> command is RenderCommand.DrawText && command.text == text })
    }
}
