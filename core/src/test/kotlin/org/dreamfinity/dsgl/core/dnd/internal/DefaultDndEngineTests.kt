package org.dreamfinity.dsgl.core.dnd.internal

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class DefaultDndEngineTests {
    @Test
    fun `drop target selection prefers deepest candidate over previous ancestor`() {
        val list = ContainerNode(key = "list")
        val folder = ContainerNode(key = "folder")

        val selected = DefaultDndEngine.selectDropTargetCandidate(
            candidates = listOf(list, folder),
            previousTarget = list
        )

        assertSame(folder, selected)
    }

    @Test
    fun `drop target selection keeps deepest candidate when already selected`() {
        val folder = ContainerNode(key = "folder")

        val selected = DefaultDndEngine.selectDropTargetCandidate(
            candidates = listOf(folder),
            previousTarget = folder
        )

        assertSame(folder, selected)
    }

    @Test
    fun `shiftCommand translates checkerboard command without changing checker offsets`() {
        val shift = DefaultDndEngine::class.java.getDeclaredMethod(
            "shiftCommand",
            RenderCommand::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        shift.isAccessible = true
        val command = RenderCommand.DrawCheckerboard(
            x = 10,
            y = 15,
            width = 20,
            height = 25,
            cellSize = 4,
            lightColor = 0xFFCCDDEE.toInt(),
            darkColor = 0xFF445566.toInt(),
            offsetX = 3,
            offsetY = 7
        )

        val shifted = shift.invoke(DefaultDndEngine, command, 8, -5) as RenderCommand.DrawCheckerboard

        assertEquals(18, shifted.x)
        assertEquals(10, shifted.y)
        assertEquals(command.offsetX, shifted.offsetX)
        assertEquals(command.offsetY, shifted.offsetY)
    }

    @Test
    fun `drop target selection returns null for empty candidates`() {
        val selected = DefaultDndEngine.selectDropTargetCandidate(
            candidates = emptyList(),
            previousTarget = null
        )

        assertNull(selected)
    }
}
