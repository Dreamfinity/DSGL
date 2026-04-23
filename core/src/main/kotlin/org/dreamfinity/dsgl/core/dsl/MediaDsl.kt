package org.dreamfinity.dsgl.core.dsl

import org.dreamfinity.dsgl.core.ItemStackRef
import org.dreamfinity.dsgl.core.dom.elements.ImageNode
import org.dreamfinity.dsgl.core.dom.elements.ItemStackNode
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget

/** Image node props; accepts resource id, file://, or http(s) URLs in MC host. */
open class ImageProps(
    var url: String,
) : ComponentProps()

/** Item stack node props for platform-specific stacks. */
open class ItemStackProps(
    var stack: ItemStackRef,
    var size: Int = 18,
    var rotYDeg: Double = 160.0,
    var rotXDeg: Double = -11.0,
) : ComponentProps()

/** Image node from resource, file, or URL (host-dependent). */
@DsglDsl
fun UiScope.img(url: String, props: ImageProps.() -> Unit = {}, ref: RefTarget<ElementHandle>? = null) =
    withProps(ImageProps(url).apply(props)) { props ->
        ImageNode(
            props.url,
            key = props.key,
        ).apply {
            applyStyle(props.style)
            applyHandlers(props)
            applyRef(this, ref)
            add(this)
        }
    }

/** Item stack node for platform-specific stack types. */
@DsglDsl
fun UiScope.itemStack(
    itemStack: ItemStackRef,
    props: ItemStackProps.() -> Unit = {},
    ref: RefTarget<ElementHandle>? = null,
) = withProps(ItemStackProps(itemStack).apply(props)) { props ->
    ItemStackNode(
        props.stack,
        props.size,
        props.rotYDeg,
        props.rotXDeg,
        props.key,
    ).apply {
        applyStyle(this, props.style)
        applyHandlers(this, props)
        applyRef(this, ref)
        add(this)
    }
}
