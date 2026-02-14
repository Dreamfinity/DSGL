package org.dreamfinity.dsgl.core.event

/**
 * Focus event fired when an element receives focus.
 */
data class FocusGainEvent(
    val previousTargetKey: Any? = null
) : Event() {
    override val type: Events
        get() = Events.FOCUS
}

