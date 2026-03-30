package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.style.ComputedStyle
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.*

class InspectorStyleEditorSnapshotBuilderTests {

    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `builder creates row snapshots dropdown layouts and color-preview action specs`() {
        val (_, selected) = inspectedSelection()
        StyleEngine.setInspectorOverrideLiteral(selected, StyleProperty.BACKGROUND_COLOR, "#FF336699").getOrThrow()
        val inspection = StyleEngine.inspect(selected)

        val result = builder().build(
            context(
                selected = selected,
                inspection = inspection,
                editableProperties = listOf(
                    StyleProperty.BACKGROUND_COLOR,
                    StyleProperty.WIDTH,
                    StyleProperty.DISPLAY
                ),
                openValueSelectProperty = StyleProperty.DISPLAY,
                openUnitSelectProperty = StyleProperty.WIDTH,
                openValueSelectScrollIndex = 99,
                openUnitSelectScrollIndex = 99
            )
        )

        assertEquals(3, result.rows.size)
        val colorRow = result.rows.firstOrNull { it.property == StyleProperty.BACKGROUND_COLOR }
            ?: error("background color row missing")
        val widthRow = result.rows.firstOrNull { it.property == StyleProperty.WIDTH }
            ?: error("width row missing")
        val displayRow = result.rows.firstOrNull { it.property == StyleProperty.DISPLAY }
            ?: error("display row missing")

        assertNotNull(colorRow.colorPreviewRect)
        assertNotNull(colorRow.colorPreviewColor)
        assertNotNull(widthRow.decrementRect)
        assertNotNull(widthRow.inputRect)
        assertNotNull(widthRow.incrementRect)
        assertNotNull(widthRow.unitRect)
        assertTrue(displayRow.controlOpen)
        assertTrue(widthRow.unitOpen)

        assertTrue(result.dropdowns.any { it.property == StyleProperty.DISPLAY && !it.unitSelect })
        assertTrue(result.dropdowns.any { it.property == StyleProperty.WIDTH && it.unitSelect })
        assertEquals(2, result.dropdownLayouts.size)
        assertEquals(0, result.openValueSelectScrollIndex)
        assertEquals(0, result.openUnitSelectScrollIndex)

        assertTrue(
            result.actionSpecs.any {
                it.type == InspectorStyleEditorActionType.OpenColorPicker && it.property == StyleProperty.BACKGROUND_COLOR
            }
        )
        assertTrue(result.resetRect.width > 0 && result.resetRect.height > 0)
        assertTrue(result.clearRect.width > 0 && result.clearRect.height > 0)
    }

    @Test
    fun `builder projects dropdown hover through pointer projection scroll and preserves option value`() {
        val (_, selected) = inspectedSelection()
        val inspection = StyleEngine.inspect(selected)
        val baseContext = context(
            selected = selected,
            inspection = inspection,
            editableProperties = listOf(StyleProperty.DISPLAY),
            openValueSelectProperty = StyleProperty.DISPLAY,
            pointerProjectionScrollY = 32,
            mouseX = 0,
            mouseY = 0
        )
        val baseline = builder().build(baseContext)
        val dropdown = baseline.dropdowns.firstOrNull() ?: error("display dropdown missing")
        val option = dropdown.options.firstOrNull() ?: error("display dropdown option missing")
        val projectedOptionY = option.rect.y - 32

        val hovered = builder().build(
            baseContext.copy(
                mouseX = option.rect.x + 2,
                mouseY = projectedOptionY + (option.rect.height / 2).coerceAtLeast(1)
            )
        )
        val hoveredDropdown = hovered.dropdowns.firstOrNull() ?: error("display dropdown missing after hover pass")
        val hoveredOption = hoveredDropdown.options.firstOrNull { it.value == option.value }
            ?: error("hovered option missing")

        assertTrue(hoveredOption.hovered)
        assertEquals(option.value, hoveredOption.value)
    }

    @Test
    fun `builder emits variable tooltip when pointer hovers variable-backed row`() {
        val (_, selected) = inspectedSelection()
        StyleEngine.setInspectorOverride(
            selected,
            StyleProperty.BACKGROUND_COLOR,
            StyleExpression.VariableRef("--missing-color")
        )
        val inspection = StyleEngine.inspect(selected)
        val panelRect = Rect(20, 20, 360, 260)
        val rowY = 64 + 32

        val result = builder().build(
            context(
                selected = selected,
                inspection = inspection,
                panelRect = panelRect,
                editableProperties = listOf(StyleProperty.BACKGROUND_COLOR),
                mouseX = panelRect.x + 18,
                mouseY = rowY + 8
            )
        )

        val tooltip = result.variableTooltip ?: error("expected variable tooltip")
        assertTrue(tooltip.text.contains("--missing-color"))
        assertTrue(tooltip.rect.width > 0 && tooltip.rect.height > 0)
    }

    private fun builder(): InspectorStyleEditorSnapshotBuilder {
        return InspectorStyleEditorSnapshotBuilder(
            resolveLiteralFromComputed = ::literalForProperty,
            renderExpressionLabel = ::expressionLabel
        )
    }

    private fun context(
        selected: ContainerNode,
        inspection: org.dreamfinity.dsgl.core.style.StyleInspection,
        panelRect: Rect = Rect(20, 20, 360, 260),
        editableProperties: List<StyleProperty>,
        pointerProjectionScrollY: Int = 0,
        mouseX: Int = 180,
        mouseY: Int = 120,
        openValueSelectProperty: StyleProperty? = null,
        openUnitSelectProperty: StyleProperty? = null,
        openValueSelectScrollIndex: Int = 0,
        openUnitSelectScrollIndex: Int = 0
    ): InspectorStyleEditorSnapshotBuildContext {
        return InspectorStyleEditorSnapshotBuildContext(
            panelRect = panelRect,
            panelBounds = panelRect,
            selected = selected,
            inspection = inspection,
            editableProperties = editableProperties,
            startY = 64,
            lineHeightPx = 32,
            rowHeightPx = 34,
            secondaryFontSizePx = 24,
            pointerProjectionScrollY = pointerProjectionScrollY,
            mouseX = mouseX,
            mouseY = mouseY,
            viewportWidth = 1280,
            viewportHeight = 720,
            openValueSelectProperty = openValueSelectProperty,
            openUnitSelectProperty = openUnitSelectProperty,
            openValueSelectScrollIndex = openValueSelectScrollIndex,
            openUnitSelectScrollIndex = openUnitSelectScrollIndex
        )
    }

    private fun inspectedSelection(): Pair<ContainerNode, ContainerNode> {
        val root = ContainerNode(key = "root").apply {
            bounds = Rect(0, 0, 1280, 720)
        }
        val selected = ContainerNode(key = "target").apply {
            bounds = Rect(980, 140, 120, 30)
        }
        selected.applyParent(root)
        return root to selected
    }

    private fun literalForProperty(style: ComputedStyle, property: StyleProperty): String {
        return when (property) {
            StyleProperty.BACKGROUND_COLOR -> "#FF336699"
            StyleProperty.WIDTH -> "24px"
            StyleProperty.DISPLAY -> "block"
            else -> when (property) {
                StyleProperty.FONT_ID -> style.fontId ?: "minecraft"
                else -> "0"
            }
        }
    }

    private fun expressionLabel(expression: StyleExpression): String {
        return when (expression) {
            is StyleExpression.Literal -> expression.value
            is StyleExpression.VariableRef -> "var(${expression.name})"
        }
    }
}
