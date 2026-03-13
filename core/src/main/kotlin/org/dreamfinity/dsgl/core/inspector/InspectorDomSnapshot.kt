package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.dom.layout.Rect

internal data class InspectorDomSnapshot(
    val panelState: InspectorPanelState,
    val panelRect: Rect,
    val headerRect: Rect?,
    val bodyRect: Rect?,
    val headerText: String,
    val minimizedLines: List<String>,
    val infoLines: List<String>,
    val parentLabel: String?,
    val childLabels: List<String>,
    val styleEditorHeight: Int,
    val styleLines: List<String>
)
