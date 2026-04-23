package org.dreamfinity.dsgl.core.overlay

object OverlayDebugVisualization {
    var applicationOverlayFillColor: Int = 0x22307BC8
    var applicationOverlayBorderColor: Int = 0xAA4DA4FF.toInt()
    var systemOverlayFillColor: Int = 0x22A84BD8
    var systemOverlayBorderColor: Int = 0xAAE18BFF.toInt()
    val enabled: Boolean
        get() {
            return testOverride ?: java.lang.Boolean
                .getBoolean("dsgl.overlay.debug")
        }

    private var testOverride: Boolean? = null

    internal fun setTestOverride(value: Boolean?) {
        testOverride = value
    }
}
