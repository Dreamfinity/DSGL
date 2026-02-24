package org.dreamfinity.dsgl.core.dom.reconcile

import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.ui
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class TextSourceReconcileTests {
    @Test
    fun `text props supports static and dynamic sources`() {
        val static = TextProps("Hello")
        assertEquals("Hello", static.source.resolve())

        var counter = 1
        val dynamic = TextProps { "Count=$counter" }
        assertEquals("Count=1", dynamic.source.resolve())
        counter = 2
        assertEquals("Count=2", dynamic.source.resolve())
    }

    @Test
    fun `dynamic text updates on reconcile without replacing text node`() {
        var counter = 0
        val current = ui {
            text { "Count=$counter" }
        }
        val retainedBefore = assertIs<TextNode>(current.root.children.single())
        assertEquals("Count=0", retainedBefore.text)

        counter = 7
        val next = ui {
            text { "Count=$counter" }
        }

        current.reconcileWith(next)

        val retainedAfter = assertIs<TextNode>(current.root.children.single())
        assertSame(retainedBefore, retainedAfter)
        assertEquals("Count=7", retainedAfter.text)
    }
}
