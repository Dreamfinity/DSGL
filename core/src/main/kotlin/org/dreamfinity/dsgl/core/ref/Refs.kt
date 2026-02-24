package org.dreamfinity.dsgl.core.ref

import org.dreamfinity.dsgl.core.DsglWindow

typealias RefCallback<T> = (T?) -> Unit

fun interface RefTarget<T : Any> {
    fun set(value: T?)
}

interface Ref<T : Any> : RefTarget<T> {
    var current: T?

    override fun set(value: T?) {
        current = value
    }
}

class RefObject<T : Any>(
    override var current: T? = null
) : Ref<T>

fun <T : Any> createRef(initial: T? = null): Ref<T> = RefObject(initial)

fun <T : Any> DsglWindow.useRef(initial: T? = null): Ref<T> {
    return useRefSlot(initial)
}
