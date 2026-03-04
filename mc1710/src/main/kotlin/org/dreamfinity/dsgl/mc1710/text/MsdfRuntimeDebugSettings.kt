package org.dreamfinity.dsgl.mc1710.text

object MsdfRuntimeDebugSettings {
    @Volatile
    var decorationGuidesEnabled: Boolean = java.lang.Boolean.getBoolean("dsgl.msdf.debug.decorations")
}
