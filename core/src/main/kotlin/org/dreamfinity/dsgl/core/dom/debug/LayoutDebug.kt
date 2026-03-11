package org.dreamfinity.dsgl.core.dom.debug

object LayoutDebug {
    @Volatile
    var validateLayouts: Boolean = false

    @Volatile
    var strictBounds: Boolean = false

    @Volatile
    var drawBounds: Boolean = false

    @Volatile
    var logViolations: Boolean = true

    @Volatile
    var lastViolationCount: Int = 0
}
