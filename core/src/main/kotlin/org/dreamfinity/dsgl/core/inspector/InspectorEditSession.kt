package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.style.CssUnit
import org.dreamfinity.dsgl.core.style.StyleProperty

internal class InspectorEditSession {
    var activeProperty: StyleProperty? = null
    var activeBuffer: String = ""
    var activeUnit: CssUnit? = null
    var activeIsNumeric: Boolean = false
    var openValueProperty: StyleProperty? = null
    var openValueScrollIndex: Int = 0
    var openUnitProperty: StyleProperty? = null
    var openUnitScrollIndex: Int = 0

    fun begin(
        property: StyleProperty,
        initialBuffer: String,
        initialUnit: CssUnit?,
        isNumeric: Boolean
    ) {
        activeProperty = property
        activeBuffer = initialBuffer
        activeUnit = initialUnit
        activeIsNumeric = isNumeric
    }

    fun clearActiveEdit() {
        activeProperty = null
        activeBuffer = ""
        activeUnit = null
        activeIsNumeric = false
    }

    fun closeAllDropdowns() {
        openValueProperty = null
        openValueScrollIndex = 0
        openUnitProperty = null
        openUnitScrollIndex = 0
    }

    fun resetAll() {
        clearActiveEdit()
        closeAllDropdowns()
    }
}
