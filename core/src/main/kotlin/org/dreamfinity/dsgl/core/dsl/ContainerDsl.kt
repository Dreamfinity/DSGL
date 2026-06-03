package org.dreamfinity.dsgl.core.dsl

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget

/** Generic container; layout is controlled by style.display. */
fun UiScope.div(
    props: ComponentProps.() -> Unit,
    ref: RefTarget<ElementHandle>? = null,
    block: UiScope.() -> Unit = {},
) = withProps(ComponentProps().apply(props)) { props ->
    ContainerNode(
        stackLayout = props.overlapChildren,
        key = props.key,
    ).apply {
        applyStyle(this, props.style)
        applyHandlers(this, props)
        applyRef(this, ref)
        add(this)
        childScope(this).block()
    }
}
