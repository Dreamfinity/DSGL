package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.dsl.ui
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContainerDslTests {
    @Test
    fun `div overlapChildren opts into overlapping child layout`() {
        val tree =
            ui {
                div({
                    key = "plain"
                })
                div({
                    key = "overlap"
                    overlapChildren = true
                })
            }

        val plain =
            tree.root.children
                .first { node -> node.key == "plain" } as ContainerNode
        val overlap =
            tree.root.children
                .first { node -> node.key == "overlap" } as ContainerNode

        assertFalse(plain.stackLayout)
        assertTrue(overlap.stackLayout)
    }
}
