package org.dreamfinity.dsgl.core.dnd

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.hooks.HookUsageException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DndHooksRuntimeIntegrationTests {
    @Test
    fun `useDraggable can be called multiple times with distinct component keys in one render`() {
        val window = object : DsglWindow() {
            override fun render(): DomTree {
                useDraggable(id = "card-a", nodeKey = "card-a")
                useDraggable(id = "card-b", nodeKey = "card-b")
                useDroppable(id = "lane-a", nodeKey = "lane-a")
                useDroppable(id = "lane-b", nodeKey = "lane-b")
                return DomTree(ContainerNode(key = "dnd.hooks.root"))
            }
        }

        renderWithHookSession(window)
    }

    @Test
    fun `duplicate hook-component identity in same render fails loudly`() {
        val window = object : DsglWindow() {
            override fun render(): DomTree {
                useDraggable(id = "card-a", nodeKey = "same")
                useDraggable(id = "card-b", nodeKey = "same")
                return DomTree(ContainerNode(key = "dnd.hooks.duplicate.root"))
            }
        }

        window.beginRenderBuild()
        val error = assertFailsWith<HookUsageException> {
            window.render()
        }
        assertEquals(error.message?.contains("Duplicate component identity"), true)
        window.endRenderBuild()
    }

    private fun renderWithHookSession(window: DsglWindow): DomTree {
        window.beginRenderBuild()
        return try {
            window.render()
        } finally {
            window.endRenderBuild()
        }
    }
}
