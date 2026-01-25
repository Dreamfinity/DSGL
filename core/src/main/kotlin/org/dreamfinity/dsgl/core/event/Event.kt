package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.dom.DOMNode

/**
 * Base UI event routed through [EventBus].
 */
abstract class Event {
    abstract val type: Events
    /** When true, stops event propagation. */
    var cancelled: Boolean = false
    /** Event target resolved by the host. */
    var target: DOMNode? = null
}
