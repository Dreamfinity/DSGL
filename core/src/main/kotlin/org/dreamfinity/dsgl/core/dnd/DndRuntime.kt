package org.dreamfinity.dsgl.core.dnd

import org.dreamfinity.dsgl.core.dnd.internal.DefaultDndEngine
import java.util.concurrent.ConcurrentHashMap

object DndRuntime {
    val engine: DndEngine = DefaultDndEngine
}

object DndSystem {
    private val payloadById: MutableMap<String, Any> = ConcurrentHashMap()

    fun registerPayload(id: String, payload: Any?) {
        if (payload == null) {
            payloadById.remove(id)
            return
        }
        payloadById[id] = payload
    }

    fun payload(id: String?): Any? {
        if (id == null) return null
        return payloadById[id]
    }

    fun monitor(nodeKey: Any? = null): DndMonitorState = DndRuntime.engine.monitor(nodeKey)

    fun activeDrag(): ActiveDrag? = DndRuntime.engine.activeDrag()

    fun setSmoothingFactor(value: Double) {
        DndRuntime.engine.setSmoothingFactor(value)
    }

    fun getSmoothingFactor(): Double = DndRuntime.engine.getSmoothingFactor()
}
