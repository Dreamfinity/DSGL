package org.dreamfinity.dsgl.core.colorpicker.internal

internal object ColorPickerDebugCounters {
    @Volatile
    private var enabled: Boolean =
        java.lang.Boolean
            .getBoolean("dsgl.colorPicker.debugCounters")

    @Volatile
    private var recentSwatchGridComposeCalls: Long = 0L

    @Volatile
    private var recentSwatchNodesCreated: Long = 0L

    @Volatile
    private var recentSwatchNodesRemoved: Long = 0L

    @Volatile
    private var recentSwatchComposeNanos: Long = 0L

    @Volatile
    private var recentSwatchRemoveNanos: Long = 0L

    @Volatile
    private var recentColorsSnapshotReads: Long = 0L

    @Volatile
    private var refreshLayoutCalls: Long = 0L

    @Volatile
    private var refreshLayoutSystemCalls: Long = 0L

    @Volatile
    private var buildLayoutCalls: Long = 0L

    @Volatile
    private var buildLayoutSystemCalls: Long = 0L

    @Volatile
    private var routeSystemInputSlotChecks: Long = 0L

    @Volatile
    private var routeSystemInputSlotHits: Long = 0L

    @Volatile
    private var routeSystemBodyIntentChecks: Long = 0L

    @Volatile
    private var routeSystemBodyIntentHits: Long = 0L

    @Volatile
    private var renderInvalidationCalls: Long = 0L

    data class Snapshot(
        val recentSwatchGridComposeCalls: Long,
        val recentSwatchNodesCreated: Long,
        val recentSwatchNodesRemoved: Long,
        val recentSwatchComposeNanos: Long,
        val recentSwatchRemoveNanos: Long,
        val recentColorsSnapshotReads: Long,
        val refreshLayoutCalls: Long,
        val refreshLayoutSystemCalls: Long,
        val buildLayoutCalls: Long,
        val buildLayoutSystemCalls: Long,
        val routeSystemInputSlotChecks: Long,
        val routeSystemInputSlotHits: Long,
        val routeSystemBodyIntentChecks: Long,
        val routeSystemBodyIntentHits: Long,
        val renderInvalidationCalls: Long,
    )

    fun reset() {
        recentSwatchGridComposeCalls = 0L
        recentSwatchNodesCreated = 0L
        recentSwatchNodesRemoved = 0L
        recentSwatchComposeNanos = 0L
        recentSwatchRemoveNanos = 0L
        recentColorsSnapshotReads = 0L
        refreshLayoutCalls = 0L
        refreshLayoutSystemCalls = 0L
        buildLayoutCalls = 0L
        buildLayoutSystemCalls = 0L
        routeSystemInputSlotChecks = 0L
        routeSystemInputSlotHits = 0L
        routeSystemBodyIntentChecks = 0L
        routeSystemBodyIntentHits = 0L
        renderInvalidationCalls = 0L
    }

    fun snapshot(): Snapshot =
        Snapshot(
            recentSwatchGridComposeCalls = recentSwatchGridComposeCalls,
            recentSwatchNodesCreated = recentSwatchNodesCreated,
            recentSwatchNodesRemoved = recentSwatchNodesRemoved,
            recentSwatchComposeNanos = recentSwatchComposeNanos,
            recentSwatchRemoveNanos = recentSwatchRemoveNanos,
            recentColorsSnapshotReads = recentColorsSnapshotReads,
            refreshLayoutCalls = refreshLayoutCalls,
            refreshLayoutSystemCalls = refreshLayoutSystemCalls,
            buildLayoutCalls = buildLayoutCalls,
            buildLayoutSystemCalls = buildLayoutSystemCalls,
            routeSystemInputSlotChecks = routeSystemInputSlotChecks,
            routeSystemInputSlotHits = routeSystemInputSlotHits,
            routeSystemBodyIntentChecks = routeSystemBodyIntentChecks,
            routeSystemBodyIntentHits = routeSystemBodyIntentHits,
            renderInvalidationCalls = renderInvalidationCalls,
        )

    fun onRecentSwatchCompose(
        createdNodes: Int,
        removedNodes: Int,
        composeDurationNanos: Long,
        removeDurationNanos: Long,
    ) {
        if (!enabled) return
        recentSwatchGridComposeCalls += 1L
        if (createdNodes > 0) {
            recentSwatchNodesCreated += createdNodes.toLong()
        }
        if (removedNodes > 0) {
            recentSwatchNodesRemoved += removedNodes.toLong()
        }
        if (composeDurationNanos > 0L) {
            recentSwatchComposeNanos += composeDurationNanos
        }
        if (removeDurationNanos > 0L) {
            recentSwatchRemoveNanos += removeDurationNanos
        }
    }

    fun onRecentColorsSnapshotRead() {
        if (!enabled) return
        recentColorsSnapshotReads += 1L
    }

    fun onRefreshLayoutCall(systemOwner: Boolean) {
        if (!enabled) return
        refreshLayoutCalls += 1L
        if (systemOwner) {
            refreshLayoutSystemCalls += 1L
        }
    }

    fun onBuildLayoutCall(systemOwner: Boolean) {
        if (!enabled) return
        buildLayoutCalls += 1L
        if (systemOwner) {
            buildLayoutSystemCalls += 1L
        }
    }

    fun onRouteSystemInputSlotCheck(hit: Boolean) {
        if (!enabled) return
        routeSystemInputSlotChecks += 1L
        if (hit) {
            routeSystemInputSlotHits += 1L
        }
    }

    fun onRouteSystemBodyIntentCheck(hit: Boolean) {
        if (!enabled) return
        routeSystemBodyIntentChecks += 1L
        if (hit) {
            routeSystemBodyIntentHits += 1L
        }
    }

    fun onRenderInvalidationCall() {
        if (!enabled) return
        renderInvalidationCalls += 1L
    }
}
