package org.dreamfinity.dsgl.core.debug

import org.dreamfinity.dsgl.core.portal.ScreenDomainSurface
import org.dreamfinity.dsgl.core.portal.ScreenDomainSurfaces

data class DomainSurfaceDebugSnapshot(
    val applicationPortalRenderEnabled: Boolean,
    val applicationPortalTintEnabled: Boolean,
    val applicationPortalInputEnabled: Boolean,
    val systemPortalRenderEnabled: Boolean,
    val systemPortalTintEnabled: Boolean,
    val systemPortalInputEnabled: Boolean,
    val frameFps: Int,
    val frameTimeMs: Float,
    val frameFpsWindow: Int,
    val frameTimeWindowMs: Float,
)

object DomainSurfaceDebugState {
    private const val FRAME_TIMING_WINDOW_SIZE: Int = 60
    private val frameTimeWindowSeconds: DoubleArray = DoubleArray(FRAME_TIMING_WINDOW_SIZE)
    private var frameTimeWindowWriteIndex: Int = 0
    private var frameTimeWindowCount: Int = 0
    private var frameTimeWindowSumSeconds: Double = 0.0

    @Volatile
    var applicationPortalRenderEnabled: Boolean = true

    @Volatile
    var applicationPortalTintEnabled: Boolean = false

    @Volatile
    var applicationPortalInputEnabled: Boolean = true

    @Volatile
    var systemPortalRenderEnabled: Boolean = true

    @Volatile
    var systemPortalTintEnabled: Boolean = false

    @Volatile
    var systemPortalInputEnabled: Boolean = true

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
                .getBoolean("dsgl.domain.controls")
        }

    fun isRenderEnabled(surface: ScreenDomainSurface): Boolean =
        when {
            surface == ScreenDomainSurfaces.ApplicationPortal -> applicationPortalRenderEnabled
            surface == ScreenDomainSurfaces.SystemPortal -> systemPortalRenderEnabled
            else -> true
        }

    fun isTintEnabled(surface: ScreenDomainSurface): Boolean =
        when {
            surface == ScreenDomainSurfaces.ApplicationPortal -> applicationPortalTintEnabled
            surface == ScreenDomainSurfaces.SystemPortal -> systemPortalTintEnabled
            else -> true
        }

    fun isInputEnabled(surface: ScreenDomainSurface): Boolean =
        when {
            surface == ScreenDomainSurfaces.ApplicationPortal -> applicationPortalInputEnabled
            surface == ScreenDomainSurfaces.SystemPortal -> systemPortalInputEnabled
            else -> true
        }

    fun resetAll() {
        applicationPortalRenderEnabled = true
        applicationPortalTintEnabled = false
        applicationPortalInputEnabled = true
        systemPortalRenderEnabled = true
        systemPortalTintEnabled = false
        systemPortalInputEnabled = true
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

    fun snapshot(): DomainSurfaceDebugSnapshot =
        DomainSurfaceDebugSnapshot(
            applicationPortalRenderEnabled = applicationPortalRenderEnabled,
            applicationPortalTintEnabled = applicationPortalTintEnabled,
            applicationPortalInputEnabled = applicationPortalInputEnabled,
            systemPortalRenderEnabled = systemPortalRenderEnabled,
            systemPortalTintEnabled = systemPortalTintEnabled,
            systemPortalInputEnabled = systemPortalInputEnabled,
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
