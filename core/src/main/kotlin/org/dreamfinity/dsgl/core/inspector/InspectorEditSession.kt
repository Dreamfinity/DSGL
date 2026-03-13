package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.dom.elements.TextEditState
import org.dreamfinity.dsgl.core.dom.layout.Rect
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
    val textState: TextEditState = TextEditState()
    var textSelectionDragActive: Boolean = false
    var textSelectionDragProperty: StyleProperty? = null
    var textSelectionDragRect: Rect = Rect(0, 0, 0, 0)

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
        textState.caretIndex = initialBuffer.length
        textState.clearSelection()
        textState.clampToLength(initialBuffer.length)
        textState.resetBlinkClock()
        textSelectionDragActive = false
        textSelectionDragProperty = null
        textSelectionDragRect = Rect(0, 0, 0, 0)
    }

    fun clearActiveEdit() {
        activeProperty = null
        activeBuffer = ""
        activeUnit = null
        activeIsNumeric = false
        textState.caretIndex = 0
        textState.clearSelection()
        textState.resetBlinkClock()
        textSelectionDragActive = false
        textSelectionDragProperty = null
        textSelectionDragRect = Rect(0, 0, 0, 0)
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
