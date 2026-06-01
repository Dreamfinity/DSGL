package org.dreamfinity.dsgl.core.hooks.ref

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.FocusManager

interface ElementHandle {
    val key: String
    val bounds: Rect

    fun requestFocus()

    fun scrollIntoView()
}

internal class NodeElementHandle(
    node: DOMNode,
) : ElementHandle {
    private var nodeRef: DOMNode? = node

    override val key: String
        get() = nodeRef?.key?.toString() ?: ""

    override val bounds: Rect
        get() = nodeRef?.bounds ?: Rect(0, 0, 0, 0)

    override fun requestFocus() {
        FocusManager.requestFocusFrom(nodeRef)
    }

    @Suppress("ForbiddenComment")
    override fun scrollIntoView() {
        // TODO(Veritaris): add scroll container integration when generic scrolling API is available.
    }

    fun detach() {
        nodeRef = null
    }
}
