package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.style.StyleProperty

internal data class InspectorHighlightSnapshot(
    val marginRect: Rect,
    val borderRect: Rect,
    val paddingRect: Rect,
    val contentRect: Rect,
    val parentContentRect: Rect?
)

internal data class InspectorTooltipSnapshot(
    val text: String,
    val rect: Rect
)

internal data class InspectorStyleEditorRowSnapshot(
    val property: StyleProperty,
    val sourceTag: String,
    val rowRect: Rect,
    val labelText: String,
    val resetRect: Rect,
    val editorKind: InspectorEditorKind,
    val controlRect: Rect,
    val controlValue: String,
    val controlOpen: Boolean,
    val controlHovered: Boolean,
    val inputActive: Boolean,
    val decrementRect: Rect?,
    val inputRect: Rect?,
    val incrementRect: Rect?,
    val unitRect: Rect?,
    val unitValue: String?,
    val unitOpen: Boolean,
    val colorPreviewRect: Rect?,
    val colorPreviewColor: Int?
)

internal data class InspectorDropdownOptionSnapshot(
    val rect: Rect,
    val text: String,
    val value: String,
    val hovered: Boolean
)

internal data class InspectorDropdownSnapshot(
    val popupRect: Rect,
    val property: StyleProperty,
    val unitSelect: Boolean,
    val options: List<InspectorDropdownOptionSnapshot>,
    val footerText: String?
)
