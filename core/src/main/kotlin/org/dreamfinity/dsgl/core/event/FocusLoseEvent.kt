package org.dreamfinity.dsgl.core.event

/**
 * Blur event fired when an element loses focus.
 */
data class FocusLoseEvent(
    val nextTargetKey: Any? = null
) : Event() {
    override val type: Events
        get() = Events.BLUR
}
