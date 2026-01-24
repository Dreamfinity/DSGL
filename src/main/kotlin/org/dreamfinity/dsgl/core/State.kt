package org.dreamfinity.dsgl.core

import kotlin.reflect.KProperty

interface State<T> {
    val value: T
}

class MutableState<T>(
    initial: T,
    private val onChange: () -> Unit = {}
) : State<T> {
    private var _value: T = initial

    override val value: T
        get() = _value

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = _value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (value != _value) {
            _value = value
            onChange()
        }
    }
}

fun <T> mutableStateOf(initial: T, onChange: () -> Unit = {}): MutableState<T> {
    return MutableState(initial, onChange)
}
