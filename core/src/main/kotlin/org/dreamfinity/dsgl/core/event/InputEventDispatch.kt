package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.dom.DOMNode

/**
 * Emits input-related events with the given [target].
 */
fun postFocus(target: DOMNode, previousTargetKey: Any? = null) {
    val event = FocusGainEvent(previousTargetKey)
    event.target = target
    EventBus.post(event)
}

fun postBlur(target: DOMNode, nextTargetKey: Any? = null) {
    val event = FocusLoseEvent(nextTargetKey)
    event.target = target
    EventBus.post(event)
}

fun postInput(target: DOMNode, value: String, parsedValue: Any? = null) {
    val event = InputEvent(value, parsedValue)
    event.target = target
    EventBus.post(event)
}

fun postChange(target: DOMNode, value: String, parsedValue: Any? = null) {
    val event = ValueChangedEvent(value, parsedValue)
    event.target = target
    EventBus.post(event)
}

