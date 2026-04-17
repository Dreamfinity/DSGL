package org.dreamfinity.dsgl.core.dsl

import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget
import org.dreamfinity.dsgl.core.style.TextFormatting

/** Static text props. */
open class TextProps(value: String = "") : ComponentProps() {
    var source: TextSource = TextSource.Static(value)

    constructor(valueProvider: () -> String) : this() {
        source = TextSource.Dynamic(valueProvider)
    }

    var value: String
        get() = source.resolve()
        set(newValue) {
            source = TextSource.Static(newValue)
        }
}


/** Text node. Supports static and rebuild-driven dynamic text. */
fun UiScope.text(
    props: TextProps.() -> Unit,
    ref: RefTarget<ElementHandle>? = null
) = withProps(TextProps().apply(props)) { props ->
    TextNode(
        props.source,
        key = props.key
    ).apply {
        applyStyle(this, props.style)
        applyHandlers(this, props)
        applyRef(this, ref)
        add(this)
    }
}

fun UiScope.text(
    value: String,
    props: (TextProps.() -> Unit) = {},
    ref: RefTarget<ElementHandle>? = null,
) {
    text(
        props = {
            this.value = value
            props()
        },
        ref = ref
    )
}

fun UiScope.text(
    value: String,
    minecraftFormatting: Boolean,
    props: TextProps.() -> Unit = {},
    ref: RefTarget<ElementHandle>? = null,
) {
    text(
        props = {
            this.value = value
            props()

            if (minecraftFormatting) {
                val prev = style
                style = {
                    textFormatting = TextFormatting.Minecraft
                    prev()
                }
            }
        },
        ref = ref
    )
}

fun UiScope.dynamicText(
    value: () -> String
) {
    dynamicText(value = value, ref = null)
}

fun UiScope.dynamicText(
    value: () -> String,
    props: TextProps.() -> Unit = {},
    ref: RefTarget<ElementHandle>? = null,
) {
    text(
        props = {
            source = TextSource.Dynamic(value)
            props()
        },
        ref = ref
    )
}

fun UiScope.dynamicText(
    value: () -> String,
    minecraftFormatting: Boolean,
    props: TextProps.() -> Unit = {},
    ref: RefTarget<ElementHandle>? = null,
) {
    text(
        props = {
            source = TextSource.Dynamic(value)
            props()

            if (minecraftFormatting) {
                val prev = style
                style = {
                    textFormatting = TextFormatting.Minecraft
                    prev()
                }
            }
        },
        ref = ref
    )
}
