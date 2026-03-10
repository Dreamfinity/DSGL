package org.dreamfinity.dsgl.core.overlay.system

internal object SystemOverlayDebugCounters {
    @Volatile
    private var enabled: Boolean = java.lang.Boolean.getBoolean("dsgl.systemOverlay.debugCounters")

    @Volatile
    private var rawNodeCreated: Long = 0L

    @Volatile
    private var rawNodeReused: Long = 0L

    @Volatile
    private var rawNodeRemoved: Long = 0L

    @Volatile
    private var rawNodeActive: Long = 0L

    @Volatile
    private var rawNodePeakActive: Long = 0L

    data class Snapshot(
        val rawNodeCreated: Long,
        val rawNodeReused: Long,
        val rawNodeRemoved: Long,
        val rawNodeActive: Long,
        val rawNodePeakActive: Long
    )

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun reset() {
        rawNodeCreated = 0L
        rawNodeReused = 0L
        rawNodeRemoved = 0L
        rawNodeActive = 0L
        rawNodePeakActive = 0L
    }

    fun snapshot(): Snapshot {
        return Snapshot(
            rawNodeCreated = rawNodeCreated,
            rawNodeReused = rawNodeReused,
            rawNodeRemoved = rawNodeRemoved,
            rawNodeActive = rawNodeActive,
            rawNodePeakActive = rawNodePeakActive
        )
    }

    fun onRawNodeCreated() {
        if (!enabled) return
        rawNodeCreated += 1L
        val active = rawNodeActive + 1L
        rawNodeActive = active
        if (active > rawNodePeakActive) {
            rawNodePeakActive = active
        }
    }

    fun onRawNodeReused() {
        if (!enabled) return
        rawNodeReused += 1L
    }

    fun onRawNodeRemoved() {
        if (!enabled) return
        rawNodeRemoved += 1L
        rawNodeActive = (rawNodeActive - 1L).coerceAtLeast(0L)
    }
}
