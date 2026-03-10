package org.dreamfinity.dsgl.core.debug

import org.dreamfinity.dsgl.core.overlay.UiLayerId

data class OverlayLayerDebugSnapshot(
    val applicationOverlayRenderEnabled: Boolean,
    val applicationOverlayInputEnabled: Boolean,
    val systemOverlayRenderEnabled: Boolean,
    val systemOverlayInputEnabled: Boolean
)

object OverlayLayerDebugState {
    @Volatile
    var applicationOverlayRenderEnabled: Boolean = true

    @Volatile
    var applicationOverlayInputEnabled: Boolean = true

    @Volatile
    var systemOverlayRenderEnabled: Boolean = true

    @Volatile
    var systemOverlayInputEnabled: Boolean = true

    val controlsEnabled: Boolean
        get() {
            return controlsEnabledOverride ?: java.lang.Boolean.getBoolean("dsgl.overlay.controls")
        }

    fun isRenderEnabled(layer: UiLayerId): Boolean {
        return when (layer) {
            UiLayerId.ApplicationOverlay -> applicationOverlayRenderEnabled
            UiLayerId.SystemOverlay -> systemOverlayRenderEnabled
            UiLayerId.Debug -> true
            UiLayerId.ApplicationRoot -> true
        }
    }

    fun isInputEnabled(layer: UiLayerId): Boolean {
        return when (layer) {
            UiLayerId.ApplicationOverlay -> applicationOverlayInputEnabled
            UiLayerId.SystemOverlay -> systemOverlayInputEnabled
            UiLayerId.Debug -> true
            UiLayerId.ApplicationRoot -> true
        }
    }

    fun resetAll() {
        applicationOverlayRenderEnabled = true
        applicationOverlayInputEnabled = true
        systemOverlayRenderEnabled = true
        systemOverlayInputEnabled = true
    }

    fun snapshot(): OverlayLayerDebugSnapshot {
        return OverlayLayerDebugSnapshot(
            applicationOverlayRenderEnabled = applicationOverlayRenderEnabled,
            applicationOverlayInputEnabled = applicationOverlayInputEnabled,
            systemOverlayRenderEnabled = systemOverlayRenderEnabled,
            systemOverlayInputEnabled = systemOverlayInputEnabled
        )
    }

    private var controlsEnabledOverride: Boolean? = null

    internal fun setControlsEnabledTestOverride(value: Boolean?) {
        controlsEnabledOverride = value
    }
}
