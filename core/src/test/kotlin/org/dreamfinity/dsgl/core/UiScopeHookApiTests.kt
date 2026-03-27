package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.ref.useRef
import kotlin.test.Test
import kotlin.test.assertEquals

class UiScopeHookApiTests {
    @Test
    fun `hooks are callable from UiScope without window qualification`() {
        val window = UiScopeHookWindow()

        window.initial = 2
        renderWithHookSession(window, commit = true)
        assertEquals(2, window.stateSeen)
        assertEquals(4, window.memoSeen)
        assertEquals(2, window.callbackSeen)
        assertEquals(2, window.refSeen)
        assertEquals(listOf("run:2"), window.effectEvents)

        window.initial = 7
        renderWithHookSession(window, commit = true)
        assertEquals(2, window.stateSeen)
        assertEquals(4, window.memoSeen)
        assertEquals(2, window.callbackSeen)
        assertEquals(2, window.refSeen)
        assertEquals(listOf("run:2"), window.effectEvents)
    }

    private fun renderWithHookSession(window: DsglWindow, commit: Boolean): DomTree {
        window.beginRenderBuild()
        var renderSucceeded = false
        return try {
            window.render().also {
                renderSucceeded = true
            }
        } finally {
            window.endRenderBuild()
            if (commit && renderSucceeded) {
                window.commitRenderBuild()
            } else {
                window.discardRenderBuild()
            }
        }
    }

    private class UiScopeHookWindow : DsglWindow() {
        var initial: Int = 0
        var stateSeen: Int? = null
        var memoSeen: Int? = null
        var callbackSeen: Int? = null
        var refSeen: Int? = null
        val effectEvents: MutableList<String> = arrayListOf()

        override fun render(): DomTree {
            return ui {
                var count by useState(initial)
                val valueRef by useRef<Int>()
                val memoValue by useMemo(count) { count * 2 }
                val callback by useCallback(count) {
                    val captured = count
                    { captured }
                }
                useEffect(count) {
                    val captured = count
                    effectEvents += "run:$captured"
                    onDispose { effectEvents += "cleanup:$captured" }
                }

                valueRef.current = count
                stateSeen = count
                memoSeen = memoValue
                callbackSeen = callback()
                refSeen = valueRef.current
            }
        }
    }
}
