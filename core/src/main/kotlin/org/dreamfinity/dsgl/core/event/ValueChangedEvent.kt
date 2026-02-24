package org.dreamfinity.dsgl.core.event

/**
 * Change event fired on committed value changes.
 */
data class ValueChangedEvent(
    val value: String,
    val parsedValue: Any? = null
) : Event() {
    override val type: Events
        get() = Events.CHANGE
}
