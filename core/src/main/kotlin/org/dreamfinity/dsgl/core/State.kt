package org.dreamfinity.dsgl.core

import kotlin.reflect.KProperty

/**
 * Read-only UI state holder.
 */
interface State<T> {
    val value: T
}

/**
 * Mutable state that triggers [onChange] when updated.
 */
class MutableState<T>(
    initial: T,
    private val onChange: () -> Unit = {},
) : State<T> {
    private var _value: T = initial

    override val value: T
        get() = _value

    /** Property delegate getter. */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = _value

    /** Property delegate setter that fires [onChange] on change. */
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (value != _value) {
            _value = value
            onChange()
        }
    }
}

/** Creates mutable state with an optional change callback. */
fun <T> mutableStateOf(initial: T, onChange: () -> Unit = {}): MutableState<T> = MutableState(initial, onChange)
