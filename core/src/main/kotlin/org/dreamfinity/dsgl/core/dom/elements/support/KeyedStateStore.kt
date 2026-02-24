package org.dreamfinity.dsgl.core.dom.elements.support

/**
 * Small keyed state store with bounded growth for node-persisted editor state.
 */
internal class KeyedStateStore<S>(
    private val maxEntries: Int = 1024
) {
    private val states: LinkedHashMap<Any, S> = LinkedHashMap()

    fun load(key: Any?): S? {
        if (key == null) return null
        return states[key]
    }

    fun save(key: Any?, state: S) {
        if (key == null) return
        states[key] = state
        trimOldest()
    }

    fun remove(key: Any?) {
        if (key == null) return
        states.remove(key)
    }

    fun clear() {
        states.clear()
    }

    private fun trimOldest() {
        while (states.size > maxEntries) {
            val iterator = states.entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }
}
