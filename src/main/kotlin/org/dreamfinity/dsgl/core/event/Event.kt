package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.dom.DOMNode

abstract class Event {
    abstract val type: Events
    var cancelled: Boolean = false
    var target: DOMNode? = null
}
