package org.dreamfinity.dsgl.core.dnd

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.hooks.HookUsageException
import org.dreamfinity.dsgl.core.dnd.internal.DefaultDndEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import java.util.WeakHashMap

class DndHooksRuntimeIntegrationTests {
    @Test
    fun `dnd hooks are callable from UiScope without window qualification`() {
        val baselineListeners = monitorListenersSnapshot().keys
        val window = object : DsglWindow() {
            override fun render(): DomTree {
                return ui {
                    useDraggable(id = "card-a", nodeKey = "card-a")
                    useDroppable(id = "lane-a", nodeKey = "lane-a")
                    useSortable(
                        id = "card-b",
                        nodeKey = "card-b",
                        containerId = "lane-a",
                        items = listOf("card-b")
                    )
                    useDragDropMonitor(DragDropMonitorCallbacks())
                }
            }
        }

        try {
            renderWithHookSession(window, commit = true)
            assertEquals(baselineListeners.size + 1, monitorListenersSnapshot().size)
        } finally {
            window.disposeHookRuntime()
            assertEquals(baselineListeners, monitorListenersSnapshot().keys)
        }
    }

    @Test
    fun `duplicate hook-component identity in same render fails loudly`() {
        val window = object : DsglWindow() {
            override fun render(): DomTree {
                return ui {
                    useDraggable(id = "card-a", nodeKey = "same")
                    useDraggable(id = "card-b", nodeKey = "same")
                }
            }
        }

        window.beginRenderBuild()
        val error = assertFailsWith<HookUsageException> {
            window.render()
        }
        assertEquals(error.message?.contains("Duplicate component identity"), true)
        window.endRenderBuild()
    }

    @Test
    fun `drag-drop monitor keeps one subscription and refreshes callbacks`() {
        val baselineListeners = monitorListenersSnapshot()
        val callbackCalls: MutableList<String> = arrayListOf()
        val window = object : DsglWindow() {
            var callbackVersion: String = "v1"

            override fun render(): DomTree {
                return ui {
                    useDragDropMonitor(
                        DragDropMonitorCallbacks(
                            onDragCancel = { callbackCalls += callbackVersion }
                        )
                    )
                }
            }
        }

        try {
            renderWithHookSession(window, commit = true)
            val afterFirstCommit = monitorListenersSnapshot()
            val addedTokens = afterFirstCommit.keys - baselineListeners.keys
            assertEquals(1, addedTokens.size)
            val token = addedTokens.first()

            window.callbackVersion = "v2"
            renderWithHookSession(window, commit = true)
            val afterSecondCommit = monitorListenersSnapshot()
            assertEquals(afterFirstCommit.keys, afterSecondCommit.keys)

            val listener = afterSecondCommit[token]
            assertNotNull(listener)
            listener.onDragCancel(sampleActiveDrag())
            assertEquals(listOf("v2"), callbackCalls)
        } finally {
            window.disposeHookRuntime()
            assertEquals(baselineListeners.keys, monitorListenersSnapshot().keys)
        }
    }

    @Test
    fun `drag-drop monitor cleans up on disappearance and re-subscribes on reappearance`() {
        val baselineCount = monitorListenersSnapshot().size
        val window = object : DsglWindow() {
            var showMonitor: Boolean = true

            override fun render(): DomTree {
                return ui {
                    if (showMonitor) {
                        useDragDropMonitor(DragDropMonitorCallbacks())
                    }
                }
            }
        }

        try {
            renderWithHookSession(window, commit = true)
            assertEquals(baselineCount + 1, monitorListenersSnapshot().size)

            renderWithHookSession(window, commit = true)
            assertEquals(baselineCount + 1, monitorListenersSnapshot().size)

            window.showMonitor = false
            renderWithHookSession(window, commit = true)
            assertEquals(baselineCount, monitorListenersSnapshot().size)

            window.showMonitor = true
            renderWithHookSession(window, commit = true)
            assertEquals(baselineCount + 1, monitorListenersSnapshot().size)
        } finally {
            window.disposeHookRuntime()
            assertEquals(baselineCount, monitorListenersSnapshot().size)
        }
    }

    @Test
    fun `sortable container state persists while mounted and resets after disappearance`() {
        val window = object : DsglWindow() {
            var showSortable: Boolean = true

            override fun render(): DomTree {
                return ui {
                    if (showSortable) {
                        useSortable(
                            id = "card-a",
                            nodeKey = "card-a",
                            containerId = "lane",
                            items = listOf("card-a")
                        )
                    }
                }
            }
        }

        try {
            renderWithHookSession(window, commit = true)
            val firstStateIdentity = sortableStateIdentity(window, "lane")
            assertNotNull(firstStateIdentity)

            renderWithHookSession(window, commit = true)
            assertEquals(firstStateIdentity, sortableStateIdentity(window, "lane"))

            window.showSortable = false
            renderWithHookSession(window, commit = true)
            assertEquals(null, sortableStateIdentity(window, "lane"))

            window.showSortable = true
            renderWithHookSession(window, commit = true)
            val secondStateIdentity = sortableStateIdentity(window, "lane")
            assertNotNull(secondStateIdentity)
            assertNotEquals(firstStateIdentity, secondStateIdentity)
        } finally {
            window.disposeHookRuntime()
        }
    }

    private fun renderWithHookSession(window: DsglWindow, commit: Boolean = false): DomTree {
        window.beginRenderBuild()
        var rendered: DomTree? = null
        var succeeded = false
        try {
            rendered = window.render()
            succeeded = true
            return rendered
        } finally {
            window.endRenderBuild()
            if (commit && succeeded) {
                window.commitRenderBuild()
            } else {
                window.discardRenderBuild()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun monitorListenersSnapshot(): Map<Long, DndMonitorListener> {
        val field = DefaultDndEngine::class.java.getDeclaredField("monitorListeners")
        field.isAccessible = true
        val listeners = field.get(DefaultDndEngine) as LinkedHashMap<Long, DndMonitorListener>
        return listeners.toMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun sortableStateIdentity(window: DsglWindow, containerId: String): Int? {
        val hooksFileClass = Class.forName("org.dreamfinity.dsgl.core.dnd.DndHooksKt")
        val byWindowField = hooksFileClass.getDeclaredField("sortableStateByWindow")
        byWindowField.isAccessible = true
        val byWindow = byWindowField.get(null) as WeakHashMap<DsglWindow, Any>

        val windowState = byWindow[window] ?: return null
        val byContainerField = windowState.javaClass.getDeclaredField("byContainerId")
        byContainerField.isAccessible = true
        val byContainer = byContainerField.get(windowState) as Map<String, Any>

        val record = byContainer[containerId] ?: return null
        val stateField = record.javaClass.getDeclaredField("state")
        stateField.isAccessible = true
        val state = stateField.get(record) ?: return null
        return System.identityHashCode(state)
    }

    private fun sampleActiveDrag(): ActiveDrag {
        return ActiveDrag(
            id = "sample",
            type = "sample",
            sourceKey = "source",
            overKey = null,
            data = null,
            cursorX = 0,
            cursorY = 0,
            transform = Transform(0.0, 0.0),
            dropEffect = DropEffect.NONE,
            dataTransfer = DataTransfer()
        )
    }
}
