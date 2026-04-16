package org.dreamfinity.dsgl.core.dsl

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.hooks.DsglContext
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget

/**
 * Builds a retained DOM tree using the DSGL UI DSL.
 *
 * Call this from [DsglWindow.render] to define the UI hierarchy.
 */
fun ui(block: UiScope.() -> Unit): DomTree {
    return buildUiTree(ownerWindow = null, root = ContainerNode(stackLayout = true), block = block)
}

fun ui(root: DOMNode, block: UiScope.() -> Unit): DomTree {
    return buildUiTree(ownerWindow = null, root = root, block = block)
}

fun ui(window: DsglWindow, block: UiScope.() -> Unit): DomTree {
    return buildUiTree(ownerWindow = window, root = ContainerNode(stackLayout = true), block = block)
}

fun ui(window: DsglWindow, root: DOMNode, block: UiScope.() -> Unit): DomTree {
    return buildUiTree(ownerWindow = window, root = root, block = block)
}

private fun buildUiTree(ownerWindow: DsglWindow?, root: DOMNode, block: UiScope.() -> Unit): DomTree {
    val scope = UiScope(root, ownerWindow)
    scope.block()
    return DomTree(root)
}

/**
 * Root DSL scope used by [ui] to add layout and component nodes.
 */
@DsglDsl
class UiScope internal constructor(
    private val parent: DOMNode,
    private val ownerWindow: DsglWindow? = null,
    private val providedContexts: Map<DsglContext<*>, Any?> = emptyMap()
) {
    @PublishedApi
    internal fun requireHookOwnerWindow(): DsglWindow {
        return ownerWindow ?: throw IllegalStateException(
            "Hook APIs require a UiScope owned by a DsglWindow render session."
        )
    }

    internal fun childScope(childParent: DOMNode): UiScope {
        return UiScope(
            parent = childParent,
            ownerWindow = ownerWindow,
            providedContexts = providedContexts
        )
    }

    internal fun <T, R> withProvidedContext(
        context: DsglContext<T>,
        value: T,
        block: UiScope.() -> R
    ): R {
        val nextScope = UiScope(
            parent = parent,
            ownerWindow = ownerWindow,
            providedContexts = providedContexts + (context to value)
        )
        return nextScope.block()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T> readContextValue(context: DsglContext<T>): T {
        if (!providedContexts.containsKey(context)) {
            return context.defaultValue
        }
        return providedContexts[context] as T
    }


    internal fun <T : DOMNode> add(node: T): T {
        return node.applyParent(parent)
    }

    internal fun <T : DOMNode> mount(node: T): T {
        return add(node)
    }

    internal fun applyHandlers(node: DOMNode, props: ComponentProps) {
        node.applyHandlers(props)
    }

    internal fun applyStyle(node: DOMNode, style: StyleScope.() -> Unit) {
        node.applyStyle(style)
    }

    internal fun applyRef(node: DOMNode, ref: RefTarget<ElementHandle>?) {
        if (ref != null) {
            node.refTarget = ref
        }
    }
}

inline fun <T, R> withProps(value: T, block: (T) -> R): R = block(value)