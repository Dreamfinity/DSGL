package org.dreamfinity.dsgl.core.dsl

import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget

/** Button node props. */
open class ButtonProps(
    var text: String,
) : TextProps()

/**
 * Button-specific DSL scope.
 */
@DsglDsl
class ButtonScope internal constructor(
    private val node: ButtonNode,
) {
    fun onClick(handler: (MouseClickEvent) -> Unit) {
        node.onClick(handler)
    }
}

/** Button node with optional extra button scope. */
@DsglDsl
fun UiScope.button(
    text: String,
    props: ButtonProps.() -> Unit = {},
    ref: RefTarget<ElementHandle>? = null,
    block: ButtonScope.() -> Unit = {},
) = withProps(ButtonProps(text).apply(props)) { props ->
    ButtonNode(
        props.text,
        key = props.key,
    ).apply {
        applyStyle(this, props.style)
        applyHandlers(this, props)
        applyRef(this, ref)
        add(this)
        ButtonScope(this).block()
    }
}
