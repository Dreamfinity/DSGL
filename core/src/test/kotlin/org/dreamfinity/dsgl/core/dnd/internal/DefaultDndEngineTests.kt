package org.dreamfinity.dsgl.core.dnd.internal

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import kotlin.test.Test
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
    fun `drop target selection returns null for empty candidates`() {
        val selected = DefaultDndEngine.selectDropTargetCandidate(
            candidates = emptyList(),
            previousTarget = null
        )

        assertNull(selected)
    }
}
