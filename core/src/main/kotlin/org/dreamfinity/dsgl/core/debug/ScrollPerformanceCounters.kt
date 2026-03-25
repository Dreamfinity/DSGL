package org.dreamfinity.dsgl.core.debug

import java.util.concurrent.atomic.AtomicLong

data class ScrollPerformanceSnapshot(
    val paintCalls: Long,
    val guardedScrollVisualFastPathRuns: Long,
    val scrollVisualGeometryRefreshRuns: Long,
    val scrollVisualGeometryTranslatedNodes: Long,
    val fullRerenderLayoutRuns: Long,
    val measureChildForLayoutCalls: Long,
    val scrollContainerStateCalls: Long,
    val stickyResolutionCalls: Long,
    val stickyHorizontalResolutionCalls: Long,
    val stickyVerticalResolutionCalls: Long,
    val chunkTraversalCalls: Long,
    val chunkRebuildCalls: Long,
    val paintTotalNanos: Long,
    val styleApplyNanos: Long,
    val fullRerenderLayoutNanos: Long,
    val chunkRebuildNanos: Long
)

object ScrollPerformanceCounters {
    private val paintCalls = AtomicLong(0L)
    private val guardedScrollVisualFastPathRuns = AtomicLong(0L)
    private val scrollVisualGeometryRefreshRuns = AtomicLong(0L)
    private val scrollVisualGeometryTranslatedNodes = AtomicLong(0L)
    private val fullRerenderLayoutRuns = AtomicLong(0L)
    private val measureChildForLayoutCalls = AtomicLong(0L)
    private val scrollContainerStateCalls = AtomicLong(0L)
    private val stickyResolutionCalls = AtomicLong(0L)
    private val stickyHorizontalResolutionCalls = AtomicLong(0L)
    private val stickyVerticalResolutionCalls = AtomicLong(0L)
    private val chunkTraversalCalls = AtomicLong(0L)
    private val chunkRebuildCalls = AtomicLong(0L)
    private val paintTotalNanos = AtomicLong(0L)
    private val styleApplyNanos = AtomicLong(0L)
    private val fullRerenderLayoutNanos = AtomicLong(0L)
    private val chunkRebuildNanos = AtomicLong(0L)

    internal fun recordPaintCall(durationNanos: Long) {
        paintCalls.incrementAndGet()
        paintTotalNanos.addAndGet(durationNanos.coerceAtLeast(0L))
    }

    internal fun recordStyleApplyDuration(durationNanos: Long) {
        styleApplyNanos.addAndGet(durationNanos.coerceAtLeast(0L))
    }

    internal fun recordFullRerenderLayoutDuration(durationNanos: Long) {
        fullRerenderLayoutRuns.incrementAndGet()
        fullRerenderLayoutNanos.addAndGet(durationNanos.coerceAtLeast(0L))
    }

    internal fun incrementGuardedScrollVisualFastPathRuns() {
        guardedScrollVisualFastPathRuns.incrementAndGet()
    }

    internal fun recordScrollVisualGeometryRefresh(translatedNodes: Int) {
        scrollVisualGeometryRefreshRuns.incrementAndGet()
        scrollVisualGeometryTranslatedNodes.addAndGet(translatedNodes.coerceAtLeast(0).toLong())
    }

    internal fun incrementMeasureChildForLayoutCalls() {
        measureChildForLayoutCalls.incrementAndGet()
    }

    internal fun incrementScrollContainerStateCalls() {
        scrollContainerStateCalls.incrementAndGet()
    }

    internal fun incrementStickyResolutionCalls() {
        stickyResolutionCalls.incrementAndGet()
    }

    internal fun incrementStickyHorizontalResolutionCalls() {
        stickyHorizontalResolutionCalls.incrementAndGet()
    }

    internal fun incrementStickyVerticalResolutionCalls() {
        stickyVerticalResolutionCalls.incrementAndGet()
    }

    internal fun incrementChunkTraversalCalls() {
        chunkTraversalCalls.incrementAndGet()
    }

    internal fun incrementChunkRebuildCalls() {
        chunkRebuildCalls.incrementAndGet()
    }

    internal fun recordChunkRebuildDuration(durationNanos: Long) {
        chunkRebuildNanos.addAndGet(durationNanos.coerceAtLeast(0L))
    }

    fun snapshot(): ScrollPerformanceSnapshot {
        return ScrollPerformanceSnapshot(
            paintCalls = paintCalls.get(),
            guardedScrollVisualFastPathRuns = guardedScrollVisualFastPathRuns.get(),
            scrollVisualGeometryRefreshRuns = scrollVisualGeometryRefreshRuns.get(),
            scrollVisualGeometryTranslatedNodes = scrollVisualGeometryTranslatedNodes.get(),
            fullRerenderLayoutRuns = fullRerenderLayoutRuns.get(),
            measureChildForLayoutCalls = measureChildForLayoutCalls.get(),
            scrollContainerStateCalls = scrollContainerStateCalls.get(),
            stickyResolutionCalls = stickyResolutionCalls.get(),
            stickyHorizontalResolutionCalls = stickyHorizontalResolutionCalls.get(),
            stickyVerticalResolutionCalls = stickyVerticalResolutionCalls.get(),
            chunkTraversalCalls = chunkTraversalCalls.get(),
            chunkRebuildCalls = chunkRebuildCalls.get(),
            paintTotalNanos = paintTotalNanos.get(),
            styleApplyNanos = styleApplyNanos.get(),
            fullRerenderLayoutNanos = fullRerenderLayoutNanos.get(),
            chunkRebuildNanos = chunkRebuildNanos.get()
        )
    }

    fun resetForTests() {
        paintCalls.set(0L)
        guardedScrollVisualFastPathRuns.set(0L)
        scrollVisualGeometryRefreshRuns.set(0L)
        scrollVisualGeometryTranslatedNodes.set(0L)
        fullRerenderLayoutRuns.set(0L)
        measureChildForLayoutCalls.set(0L)
        scrollContainerStateCalls.set(0L)
        stickyResolutionCalls.set(0L)
        stickyHorizontalResolutionCalls.set(0L)
        stickyVerticalResolutionCalls.set(0L)
        chunkTraversalCalls.set(0L)
        chunkRebuildCalls.set(0L)
        paintTotalNanos.set(0L)
        styleApplyNanos.set(0L)
        fullRerenderLayoutNanos.set(0L)
        chunkRebuildNanos.set(0L)
    }
}
