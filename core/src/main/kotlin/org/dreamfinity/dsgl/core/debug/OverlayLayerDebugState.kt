package org.dreamfinity.dsgl.core.debug

import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurface
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurfaces

data class OverlayLayerDebugSnapshot(
    val applicationOverlayRenderEnabled: Boolean,
    val applicationOverlayTintEnabled: Boolean,
    val applicationOverlayInputEnabled: Boolean,
    val systemOverlayRenderEnabled: Boolean,
    val systemOverlayTintEnabled: Boolean,
    val systemOverlayInputEnabled: Boolean,
    val frameFps: Int,
    val frameTimeMs: Float,
    val frameFpsWindow: Int,
    val frameTimeWindowMs: Float,
)

object OverlayLayerDebugState {
    private const val FRAME_TIMING_WINDOW_SIZE: Int = 60
    private val frameTimeWindowSeconds: DoubleArray = DoubleArray(FRAME_TIMING_WINDOW_SIZE)
    private var frameTimeWindowWriteIndex: Int = 0
    private var frameTimeWindowCount: Int = 0
    private var frameTimeWindowSumSeconds: Double = 0.0

    @Volatile
    var applicationOverlayRenderEnabled: Boolean = true

    @Volatile
    var applicationOverlayTintEnabled: Boolean = false

    @Volatile
    var applicationOverlayInputEnabled: Boolean = true

    @Volatile
    var systemOverlayRenderEnabled: Boolean = true

    @Volatile
    var systemOverlayTintEnabled: Boolean = false

    @Volatile
    var systemOverlayInputEnabled: Boolean = true

    @Volatile
    var frameFps: Int = 0

    @Volatile
    var frameTimeMs: Float = 0f

    @Volatile
    var frameFpsWindow: Int = 0

    @Volatile
    var frameTimeWindowMs: Float = 0f

    val controlsEnabled: Boolean
        get() {
            return controlsEnabledOverride ?: java.lang.Boolean
                .getBoolean("dsgl.overlay.controls")
        }

    fun isRenderEnabled(surface: ScreenDomainSurface): Boolean =
        when {
            surface == ScreenDomainSurfaces.ApplicationPortal -> applicationOverlayRenderEnabled
            surface == ScreenDomainSurfaces.SystemPortal -> systemOverlayRenderEnabled
            else -> true
        }

    fun isTintEnabled(surface: ScreenDomainSurface): Boolean =
        when {
            surface == ScreenDomainSurfaces.ApplicationPortal -> applicationOverlayTintEnabled
            surface == ScreenDomainSurfaces.SystemPortal -> systemOverlayTintEnabled
            else -> true
        }

    fun isInputEnabled(surface: ScreenDomainSurface): Boolean =
        when {
            surface == ScreenDomainSurfaces.ApplicationPortal -> applicationOverlayInputEnabled
            surface == ScreenDomainSurfaces.SystemPortal -> systemOverlayInputEnabled
            else -> true
        }

    fun resetAll() {
        applicationOverlayRenderEnabled = true
        applicationOverlayTintEnabled = false
        applicationOverlayInputEnabled = true
        systemOverlayRenderEnabled = true
        systemOverlayTintEnabled = false
        systemOverlayInputEnabled = true
        frameFps = 0
        frameTimeMs = 0f
        frameFpsWindow = 0
        frameTimeWindowMs = 0f
        frameTimeWindowWriteIndex = 0
        frameTimeWindowCount = 0
        frameTimeWindowSumSeconds = 0.0
        java.util.Arrays
            .fill(frameTimeWindowSeconds, 0.0)
    }

    @Synchronized
    fun updateFrameTiming(dtSeconds: Double) {
        val safeDt = dtSeconds.coerceAtLeast(1.0 / 1000.0)
        frameTimeMs = (safeDt * 1000.0).toFloat()
        frameFps =
            kotlin.math
                .round(1.0 / safeDt)
                .toInt()
                .coerceAtLeast(0)

        if (frameTimeWindowCount == FRAME_TIMING_WINDOW_SIZE) {
            frameTimeWindowSumSeconds -= frameTimeWindowSeconds[frameTimeWindowWriteIndex]
        } else {
            frameTimeWindowCount += 1
        }
        frameTimeWindowSeconds[frameTimeWindowWriteIndex] = safeDt
        frameTimeWindowSumSeconds += safeDt
        frameTimeWindowWriteIndex = (frameTimeWindowWriteIndex + 1) % FRAME_TIMING_WINDOW_SIZE

        val averageDt =
            if (frameTimeWindowCount > 0) {
                frameTimeWindowSumSeconds / frameTimeWindowCount.toDouble()
            } else {
                safeDt
            }
        frameTimeWindowMs = (averageDt * 1000.0).toFloat()
        frameFpsWindow =
            kotlin.math
                .round(1.0 / averageDt)
                .toInt()
                .coerceAtLeast(0)
    }

    fun snapshot(): OverlayLayerDebugSnapshot =
        OverlayLayerDebugSnapshot(
            applicationOverlayRenderEnabled = applicationOverlayRenderEnabled,
            applicationOverlayTintEnabled = applicationOverlayTintEnabled,
            applicationOverlayInputEnabled = applicationOverlayInputEnabled,
            systemOverlayRenderEnabled = systemOverlayRenderEnabled,
            systemOverlayTintEnabled = systemOverlayTintEnabled,
            systemOverlayInputEnabled = systemOverlayInputEnabled,
            frameFps = frameFps,
            frameTimeMs = frameTimeMs,
            frameFpsWindow = frameFpsWindow,
            frameTimeWindowMs = frameTimeWindowMs,
        )

    private var controlsEnabledOverride: Boolean? = null

    internal fun setControlsEnabledTestOverride(value: Boolean?) {
        controlsEnabledOverride = value
    }
}
