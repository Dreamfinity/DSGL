package org.dreamfinity.dsgl.core.event

/**
 * Input event fired on user-caused value changes while editing.
 */
data class InputEvent(
    val value: String,
    val parsedValue: Any? = null
) : Event() {
    override val type: Events
        get() = Events.INPUT
}

