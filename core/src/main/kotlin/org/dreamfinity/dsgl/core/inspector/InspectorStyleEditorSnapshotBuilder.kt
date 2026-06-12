package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.style.ComputedStyle
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleInspection
import org.dreamfinity.dsgl.core.style.StyleProperty
import org.dreamfinity.dsgl.core.style.StylePropertyRegistry
import org.dreamfinity.dsgl.core.style.parseColor

internal enum class InspectorStyleEditorActionType {
    ResetProperty,
    ToggleValueSelect,
    SelectValueOption,
    OpenColorPicker,
    Decrement,
    Increment,
    ToggleUnitSelect,
    SelectUnitOption,
    ResetSelectedOverrides,
    ClearAllOverrides,
}

internal data class InspectorStyleEditorActionSpec(
    val bounds: Rect,
    val type: InspectorStyleEditorActionType,
    val property: StyleProperty? = null,
    val step: Float = 1f,
    val payload: String? = null,
)

internal data class InspectorStyleEditorDropdownLayout(
    val rect: Rect,
    val property: StyleProperty,
    val unitSelect: Boolean,
    val totalOptions: Int,
    val visibleRows: Int,
)

internal data class InspectorStyleEditorSnapshotBuildContext(
    val panelRect: Rect,
    val panelBounds: Rect,
    val selected: DOMNode,
    val inspection: StyleInspection,
    val editableProperties: List<StyleProperty>,
    val startY: Int,
    val lineHeightPx: Int,
    val rowHeightPx: Int,
    val secondaryFontSizePx: Int,
    val pointerProjectionScrollY: Int,
    val mouseX: Int,
    val mouseY: Int,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val openValueSelectProperty: StyleProperty?,
    val openUnitSelectProperty: StyleProperty?,
    val openValueSelectScrollIndex: Int,
    val openUnitSelectScrollIndex: Int,
)

internal data class InspectorStyleEditorSnapshotBuildResult(
    val endY: Int,
    val variableTooltip: InspectorTooltipSnapshot?,
    val rows: List<InspectorStyleEditorRowSnapshot>,
    val dropdowns: List<InspectorDropdownSnapshot>,
    val dropdownLayouts: List<InspectorStyleEditorDropdownLayout>,
    val actionSpecs: List<InspectorStyleEditorActionSpec>,
    val resetRect: Rect,
    val clearRect: Rect,
    val openValueSelectScrollIndex: Int,
    val openUnitSelectScrollIndex: Int,
)

internal class InspectorStyleEditorSnapshotBuilder(
    private val resolveLiteralFromComputed: (ComputedStyle, StyleProperty) -> String,
    private val renderExpressionLabel: (StyleExpression) -> String,
) {
    fun build(context: InspectorStyleEditorSnapshotBuildContext): InspectorStyleEditorSnapshotBuildResult {
        var y = context.startY + context.lineHeightPx
        var variableTooltip: InspectorTooltipSnapshot? = null
        var openValueSelectScrollIndex = context.openValueSelectScrollIndex
        var openUnitSelectScrollIndex = context.openUnitSelectScrollIndex

        val rowSnapshots = ArrayList<InspectorStyleEditorRowSnapshot>(context.editableProperties.size)
        val dropdownSnapshots = ArrayList<InspectorDropdownSnapshot>(2)
        val dropdownLayouts = ArrayList<InspectorStyleEditorDropdownLayout>(2)
        val actionSpecs = ArrayList<InspectorStyleEditorActionSpec>(context.editableProperties.size * 4)

        val rowLeft = context.panelRect.x + 10
        val rowWidth = context.panelRect.width - 20
        val btnWidth = 40
        val gap = 6
        val controlHeight = (context.rowHeightPx - 8).coerceAtLeast(22)
        val labelLineHeight = (context.secondaryFontSizePx - 2).coerceAtLeast(18)

        context.editableProperties.forEach { property ->
            val overrideExpr = StyleEngine.inspectorOverrideFor(context.selected, property)
            val effectiveValue =
                overrideExpr?.let(renderExpressionLabel)
                    ?: resolveLiteralFromComputed(context.inspection.computed, property)
            val sourceTag =
                if (overrideExpr != null) {
                    "ins"
                } else {
                    context.inspection.propertySources[property]
                        ?.source ?: "default"
                }

            val labelText = "${property.key} [$sourceTag]"
            val buttonsRight = rowLeft + rowWidth - 8
            val maxLabelWidth = (rowWidth - btnWidth - gap - 36).coerceAtLeast(80)
            val labelWidth = (rowWidth * 0.40f).toInt().coerceIn(80, maxLabelWidth)
            val labelMaxChars =
                InspectorPresentationSupport.estimateMaxChars((labelWidth - 12).coerceAtLeast(24), labelLineHeight)
            val labelLineCount =
                InspectorPresentationSupport
                    .wrapText(labelText, labelMaxChars)
                    .size
                    .coerceAtLeast(1)
            val rowHeight = maxOf(context.rowHeightPx, labelLineCount * labelLineHeight + 10, controlHeight + 8)
            val rowRect = Rect(rowLeft, y, rowWidth, rowHeight)
            val controlX = rowRect.x + labelWidth
            val controlWidth = (buttonsRight - controlX - btnWidth - gap).coerceAtLeast(36)
            val controlY = rowRect.y + ((rowRect.height - controlHeight) / 2)
            val resetRect = Rect(buttonsRight - btnWidth, controlY, btnWidth, controlHeight)
            actionSpecs +=
                InspectorStyleEditorActionSpec(
                    bounds = resetRect,
                    type = InspectorStyleEditorActionType.ResetProperty,
                    property = property,
                )

            val editor =
                InspectorEditorRegistry.describe(
                    property = property,
                    literal = effectiveValue,
                    expression = overrideExpr,
                )
            val controlRect = Rect(controlX, controlY, controlWidth, controlHeight)

            var rowSnapshot =
                InspectorStyleEditorRowSnapshot(
                    property = property,
                    sourceTag = sourceTag,
                    rowRect = rowRect,
                    labelText = labelText,
                    resetRect = resetRect,
                    editorKind = editor.kind,
                    controlRect = controlRect,
                    controlValue = effectiveValue,
                    controlOpen = false,
                    controlHovered = false,
                    inputActive = false,
                    decrementRect = null,
                    inputRect = null,
                    incrementRect = null,
                    unitRect = null,
                    unitValue = null,
                    unitOpen = false,
                    colorPreviewRect = null,
                    colorPreviewColor = null,
                )

            when (editor.kind) {
                InspectorEditorKind.EnumSelect,
                InspectorEditorKind.FontSelect,
                -> {
                    val isOpen = context.openValueSelectProperty == property
                    actionSpecs +=
                        InspectorStyleEditorActionSpec(
                            bounds = controlRect,
                            type = InspectorStyleEditorActionType.ToggleValueSelect,
                            property = property,
                        )
                    rowSnapshot =
                        rowSnapshot.copy(
                            controlOpen = isOpen,
                            controlHovered =
                                projectRectForPointer(controlRect, context.pointerProjectionScrollY).contains(
                                    context.mouseX,
                                    context.mouseY,
                                ),
                        )
                }

                InspectorEditorKind.StringInput -> {
                    var previewRect: Rect? = null
                    var previewColor: Int? = null
                    if (editor.showColorPreview) {
                        previewRect =
                            Rect(
                                x = controlRect.x + controlRect.width - (controlRect.height - 8).coerceAtLeast(10) - 6,
                                y = controlRect.y + 4,
                                width = (controlRect.height - 8).coerceAtLeast(10),
                                height = (controlRect.height - 8).coerceAtLeast(10),
                            )
                        previewColor = runCatching { parseColor(effectiveValue) }.getOrNull()
                        actionSpecs +=
                            InspectorStyleEditorActionSpec(
                                bounds = previewRect,
                                type = InspectorStyleEditorActionType.OpenColorPicker,
                                property = property,
                            )
                    }
                    rowSnapshot =
                        rowSnapshot.copy(
                            controlValue = effectiveValue,
                            inputActive = false,
                            colorPreviewRect = previewRect,
                            colorPreviewColor = previewColor,
                        )
                }

                InspectorEditorKind.NumericInput -> {
                    val step = StylePropertyRegistry.descriptor(property).numericStep
                    val parsed = InspectorEditorRegistry.parseNumericLiteral(property, effectiveValue)
                    val numericValue = parsed?.numberText ?: "0"
                    val unit = parsed?.unit ?: InspectorEditorRegistry.defaultNumericUnit(property)
                    val buttonWidth = 34
                    val unitWidth = if (editor.supportsUnits) 68 else 0
                    val inputWidth =
                        (controlRect.width - buttonWidth * 2 - unitWidth - (if (editor.supportsUnits) 12 else 6))
                            .coerceAtLeast(64)
                    val decRect = Rect(controlRect.x, controlRect.y, buttonWidth, controlRect.height)
                    val inputRect = Rect(decRect.x + decRect.width + 4, controlRect.y, inputWidth, controlRect.height)
                    val incRect =
                        Rect(inputRect.x + inputRect.width + 4, controlRect.y, buttonWidth, controlRect.height)
                    actionSpecs +=
                        InspectorStyleEditorActionSpec(
                            bounds = decRect,
                            type = InspectorStyleEditorActionType.Decrement,
                            property = property,
                            step = step,
                        )
                    actionSpecs +=
                        InspectorStyleEditorActionSpec(
                            bounds = incRect,
                            type = InspectorStyleEditorActionType.Increment,
                            property = property,
                            step = step,
                        )
                    var unitRect: Rect? = null
                    if (editor.supportsUnits) {
                        unitRect = Rect(incRect.x + incRect.width + 4, controlRect.y, unitWidth, controlRect.height)
                        actionSpecs +=
                            InspectorStyleEditorActionSpec(
                                bounds = unitRect,
                                type = InspectorStyleEditorActionType.ToggleUnitSelect,
                                property = property,
                            )
                    }
                    rowSnapshot =
                        rowSnapshot.copy(
                            controlValue = numericValue,
                            inputActive = false,
                            decrementRect = decRect,
                            inputRect = inputRect,
                            incrementRect = incRect,
                            unitRect = unitRect,
                            unitValue = if (editor.supportsUnits) unit?.token else null,
                            unitOpen = context.openUnitSelectProperty == property,
                            controlOpen = false,
                        )
                }
            }

            val projectedRowRect = projectRectForPointer(rowRect, context.pointerProjectionScrollY)
            if (overrideExpr is StyleExpression.VariableRef &&
                projectedRowRect.contains(context.mouseX, context.mouseY)
            ) {
                val resolved = StyleEngine.resolveInspectorVariable(overrideExpr.name)
                val body = resolved.getOrElse { "unresolved (${it.message ?: "unknown error"})" }
                variableTooltip =
                    InspectorTooltipSnapshot(
                        text = "${overrideExpr.name} = $body",
                        rect =
                            Rect(
                                x =
                                    (projectedRowRect.x + projectedRowRect.width - 360).coerceAtLeast(
                                        context.panelBounds.x + 8,
                                    ),
                                y =
                                    (projectedRowRect.y - context.lineHeightPx - 8).coerceAtLeast(
                                        context.panelBounds.y + 8,
                                    ),
                                width = 352,
                                height = context.lineHeightPx + 10,
                            ),
                    )
            }

            if (context.openValueSelectProperty == property && editor.options.isNotEmpty()) {
                val dropdown =
                    buildDropdownSnapshot(
                        x = controlRect.x,
                        y = controlRect.y + controlRect.height + 2,
                        width = controlRect.width,
                        options = editor.options,
                        property = property,
                        unitSelect = false,
                        pointerProjectionScrollY = context.pointerProjectionScrollY,
                        rowHeightPx = context.rowHeightPx,
                        viewportWidth = context.viewportWidth,
                        viewportHeight = context.viewportHeight,
                        mouseX = context.mouseX,
                        mouseY = context.mouseY,
                        currentScrollIndex = openValueSelectScrollIndex,
                    )
                openValueSelectScrollIndex = dropdown.nextScrollIndex
                dropdownLayouts += dropdown.layout
                dropdownSnapshots += dropdown.snapshot
                actionSpecs += dropdown.optionActionSpecs
                rowSnapshot = rowSnapshot.copy(controlOpen = true)
            }
            if (context.openUnitSelectProperty == property && editor.supportsUnits) {
                val units = InspectorEditorRegistry.unitOptions().map { it.token }
                val dropdown =
                    buildDropdownSnapshot(
                        x = controlRect.x + controlRect.width - 90,
                        y = controlRect.y + controlRect.height + 2,
                        width = 90,
                        options = units,
                        property = property,
                        unitSelect = true,
                        pointerProjectionScrollY = context.pointerProjectionScrollY,
                        rowHeightPx = context.rowHeightPx,
                        viewportWidth = context.viewportWidth,
                        viewportHeight = context.viewportHeight,
                        mouseX = context.mouseX,
                        mouseY = context.mouseY,
                        currentScrollIndex = openUnitSelectScrollIndex,
                    )
                openUnitSelectScrollIndex = dropdown.nextScrollIndex
                dropdownLayouts += dropdown.layout
                dropdownSnapshots += dropdown.snapshot
                actionSpecs += dropdown.optionActionSpecs
                rowSnapshot = rowSnapshot.copy(unitOpen = true)
            }

            rowSnapshots += rowSnapshot
            y += rowHeight + 4
        }

        val actionHeight = (context.secondaryFontSizePx + 10).coerceAtLeast(28)
        val resetRect = Rect(rowLeft, y, 140, actionHeight)
        val clearRect = Rect(rowLeft + 148, y, 160, actionHeight)
        actionSpecs += InspectorStyleEditorActionSpec(resetRect, InspectorStyleEditorActionType.ResetSelectedOverrides)
        actionSpecs += InspectorStyleEditorActionSpec(clearRect, InspectorStyleEditorActionType.ClearAllOverrides)
        y += actionHeight + 4

        return InspectorStyleEditorSnapshotBuildResult(
            endY = y,
            variableTooltip = variableTooltip,
            rows = rowSnapshots,
            dropdowns = dropdownSnapshots,
            dropdownLayouts = dropdownLayouts,
            actionSpecs = actionSpecs,
            resetRect = resetRect,
            clearRect = clearRect,
            openValueSelectScrollIndex = openValueSelectScrollIndex,
            openUnitSelectScrollIndex = openUnitSelectScrollIndex,
        )
    }

    private fun projectRectForPointer(rect: Rect, pointerProjectionScrollY: Int): Rect {
        if (pointerProjectionScrollY <= 0) return rect
        return Rect(rect.x, rect.y - pointerProjectionScrollY, rect.width, rect.height)
    }

    private fun buildDropdownSnapshot(
        x: Int,
        y: Int,
        width: Int,
        options: List<String>,
        property: StyleProperty,
        unitSelect: Boolean,
        pointerProjectionScrollY: Int,
        rowHeightPx: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        mouseX: Int,
        mouseY: Int,
        currentScrollIndex: Int,
    ): BuiltDropdownSnapshot {
        val maxRows = 8
        val visibleRows = minOf(maxRows, options.size)
        val maxFirst = (options.size - visibleRows).coerceAtLeast(0)
        val first = currentScrollIndex.coerceIn(0, maxFirst)
        val shown = options.subList(first, first + visibleRows)
        val optionHeight = rowHeightPx
        val popupHeight = optionHeight * shown.size + 6
        val safeViewportW = viewportWidth.coerceAtLeast(1)
        val safeViewportH = viewportHeight.coerceAtLeast(1)
        val clampedX = x.coerceIn(2, (safeViewportW - width - 2).coerceAtLeast(2))
        val clampedY = y.coerceIn(2, (safeViewportH - popupHeight - 2).coerceAtLeast(2))
        val popupRect = Rect(clampedX, clampedY, width, popupHeight)

        val layout =
            InspectorStyleEditorDropdownLayout(
                rect = popupRect,
                property = property,
                unitSelect = unitSelect,
                totalOptions = options.size,
                visibleRows = visibleRows,
            )

        var optionY = popupRect.y + 3
        val optionSnapshots = ArrayList<InspectorDropdownOptionSnapshot>(shown.size)
        val optionActionSpecs = ArrayList<InspectorStyleEditorActionSpec>(shown.size)
        shown.forEach { option ->
            val optionRect = Rect(popupRect.x + 3, optionY, popupRect.width - 6, optionHeight - 2)
            val hovered = projectRectForPointer(optionRect, pointerProjectionScrollY).contains(mouseX, mouseY)
            optionSnapshots +=
                InspectorDropdownOptionSnapshot(
                    rect = optionRect,
                    text = ellipsize(option, 30),
                    value = option,
                    hovered = hovered,
                )
            optionActionSpecs +=
                InspectorStyleEditorActionSpec(
                    bounds = optionRect,
                    type =
                        if (unitSelect) {
                            InspectorStyleEditorActionType.SelectUnitOption
                        } else {
                            InspectorStyleEditorActionType.SelectValueOption
                        },
                    property = property,
                    payload = option,
                )
            optionY += optionHeight
        }

        val footer =
            if (options.size > visibleRows) {
                "${first + 1}-${first + visibleRows}/${options.size}"
            } else {
                null
            }
        val snapshot =
            InspectorDropdownSnapshot(
                popupRect = popupRect,
                property = property,
                unitSelect = unitSelect,
                options = optionSnapshots,
                footerText = footer,
            )
        return BuiltDropdownSnapshot(
            snapshot = snapshot,
            layout = layout,
            optionActionSpecs = optionActionSpecs,
            nextScrollIndex = first,
        )
    }

    private fun ellipsize(raw: String, maxChars: Int): String {
        if (maxChars <= 1) return raw.take(1)
        if (raw.length <= maxChars) return raw
        val keep = (maxChars - 3).coerceAtLeast(0)
        return raw.take(keep) + "..."
    }

    private data class BuiltDropdownSnapshot(
        val snapshot: InspectorDropdownSnapshot,
        val layout: InspectorStyleEditorDropdownLayout,
        val optionActionSpecs: List<InspectorStyleEditorActionSpec>,
        val nextScrollIndex: Int,
    )
}
