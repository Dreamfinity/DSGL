package org.dreamfinity.dsgl.core.portal

object DomainSurfaceDebugVisualization {
    var applicationPortalFillColor: Int = 0x22307BC8
    var applicationPortalBorderColor: Int = 0xAA4DA4FF.toInt()
    var systemPortalFillColor: Int = 0x22A84BD8
    var systemPortalBorderColor: Int = 0xAAE18BFF.toInt()
    val enabled: Boolean
        get() {
            return testOverride ?: java.lang.Boolean
                .getBoolean("dsgl.domain.debug")
        }

    private var testOverride: Boolean? = null

    internal fun setTestOverride(value: Boolean?) {
        testOverride = value
    }
}
