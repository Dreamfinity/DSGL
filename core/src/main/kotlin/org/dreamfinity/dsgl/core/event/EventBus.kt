package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.dom.DOMNode
import java.util.EnumMap
import java.util.WeakHashMap

typealias EventCallback = (Event) -> Unit

/**
 * Central event router for UI nodes. Supports bubbling for most events.
 */
object EventBus {
    private val listeners: MutableMap<Events, MutableMap<DOMNode, ArrayList<EventCallback>>> =
        EnumMap(Events::class.java)
    private val nonBubblingEvents: Set<Events> = setOf(
        Events.MOUSEENTER,
        Events.MOUSELEAVE,
        Events.MOUSEOVER
    )

    private fun getEventMap(eventType: Events): MutableMap<DOMNode, ArrayList<EventCallback>> {
        return listeners.getOrPut(eventType) { WeakHashMap() }
    }

    /** Adds a listener for a typed event on this node. */
    fun <E : Event> DOMNode.addEventListener(eventType: Events, callback: (E) -> Unit) {
        getEventMap(eventType).getOrPut(this) { arrayListOf() }.add(callback as EventCallback)
    }

    /** Adds a listener by string event name. */
    fun <E : Event> DOMNode.addEventListener(eventType: String, callback: (E) -> Unit) {
        addEventListener(Events.valueOf(eventType.uppercase()), callback)
    }

    /** Removes a listener for a typed event on this node. */
    fun <E : Event> DOMNode.removeEventListener(eventType: Events, callback: ((E) -> Unit)? = null) {
        val eventTypeListeners = listeners[eventType] ?: return
        val eventListeners = eventTypeListeners[this] ?: return
        if (callback == null) {
            eventListeners.clear()
            return
        }
        eventListeners.remove(callback)
    }

    /** Removes a listener by string event name. */
    fun <E : Event> DOMNode.removeEventListener(eventType: String, callback: ((E) -> Unit)? = null) {
        removeEventListener(Events.valueOf(eventType.uppercase()), callback)
    }

    /** Clears listeners for this node and its direct children. */
    fun DOMNode.clearListeners() {
        this.children.forEach { child ->
            listeners.values.forEach { it.remove(child) }
        }
        listeners.values.forEach { it.remove(this) }
    }

    /** Clears listeners for this node and its entire subtree. */
    fun DOMNode.clearListenersDeep() {
        this.children.forEach { child -> child.clearListenersDeep() }
        clearListeners()
    }

    /** Posts an event to all listeners, respecting bubbling and cancellation. */
    fun post(event: Event) {
        val allListeners = listeners[event.type] ?: return
        if (event.cancelled) return

        when (event) {
            is KeyboardKeyDownEvent -> KeyModifiers.update(event.keyCode, true)
            is KeyboardKeyUpEvent -> KeyModifiers.update(event.keyCode, false)
        }

        if (event.type == Events.CLICK || event.type == Events.MOUSEDOWN) {
            FocusManager.updateFocusFromTarget(event.target)
        }

        if ((event.type == Events.KEYUP || event.type == Events.KEYDOWN) && event.target == null) {
            FocusManager.focusedNode()?.let { event.target = it }
        }

        val target = event.target
        if (target != null) {
            val shouldBubble = !nonBubblingEvents.contains(event.type)
            var current: DOMNode? = target
            while (current != null) {
                val callbacks = allListeners[current]
                if (callbacks != null) {
                    for (callback in callbacks) {
                        callback.invoke(event)
                        if (event.cancelled) return
                    }
                }
                if (!shouldBubble) return
                current = current.parent
            }
            return
        }

        val validListeners = when (event.type) {
            Events.KEYUP, Events.KEYDOWN -> allListeners
            Events.MOUSEDOWN, Events.CLICK, Events.MOUSEUP, Events.WHEEL, Events.MOUSEOVER ->
                allListeners.filter { it.key.hovered(event as MouseEvent) }
            Events.MOUSEOUT, Events.MOUSELEAVE ->
                allListeners.filter { !it.key.hovered(event as MouseEvent) }
            Events.MOUSEMOVE -> allListeners
            Events.DRAG -> allListeners
            Events.MOUSEENTER -> allListeners
        }

        validListeners.forEach { (_, callbacks) ->
            callbacks.forEach { callback ->
                callback.invoke(event)
            }
        }
    }
}
