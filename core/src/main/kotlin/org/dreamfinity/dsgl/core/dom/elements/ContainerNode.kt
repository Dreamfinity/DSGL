package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.*
import kotlin.math.roundToInt

/**
 * Layout container node supporting block/inline/flex/grid flows.
 */
class ContainerNode(
    padding: Int = 0,
    gap: Int = 0,
    var backgroundColor: Int? = null,
    var stackLayout: Boolean = false,
    key: Any? = null
) : DOMNode(key) {
    override val styleType: String = "div"

    init {
        this.padding = Insets.all(padding)
        this.gap = gap.coerceAtLeast(0)
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return measureWithConstraint(ctx, null)
    }

    override fun measureForLayout(ctx: UiMeasureContext, availableOuterWidth: Int?): Size {
        val constrainedContentWidth = if (availableOuterWidth != null) {
            constrainContentWidthForOuterLimit(availableOuterWidth, this@ContainerNode)
        } else {
            null
        }
        return measureWithConstraint(ctx, constrainedContentWidth)
    }

    private fun measureWithConstraint(ctx: UiMeasureContext, constrainedContentWidth: Int?): Size {
        if (display == Display.None) {
            return Size(0, 0)
        }
        val visibleChildren = layoutChildren()
        val resolvedWrapWidth = resolvedContentLimit(constrainedContentWidth)
        val boundedExplicitWidth = width?.let { explicit ->
            resolvedWrapWidth?.let { minOf(explicit, it) } ?: explicit
        }
        if (visibleChildren.isEmpty()) {
            val contentWidth = boundedExplicitWidth ?: 0
            val contentHeight = height ?: 0
            return Size(
                contentWidth + padding.horizontal + border.horizontal,
                contentHeight + padding.vertical + border.vertical
            )
        }

        val contentSize = when {
            stackLayout -> measureStack(ctx, visibleChildren)
            display == Display.Flex -> measureFlex(ctx, visibleChildren, resolvedWrapWidth)
            display == Display.Grid -> measureGrid(ctx, visibleChildren, resolvedWrapWidth)
            display == Display.Inline -> measureInline(ctx, visibleChildren, resolvedWrapWidth)
            else -> measureBlock(ctx, visibleChildren, resolvedWrapWidth)
        }
        val contentWidth = when {
            boundedExplicitWidth != null -> boundedExplicitWidth
            resolvedWrapWidth != null -> minOf(contentSize.width, resolvedWrapWidth)
            else -> contentSize.width
        }
        val contentHeight = height ?: contentSize.height
        return Size(
            contentWidth + padding.horizontal + border.horizontal,
            contentHeight + padding.vertical + border.vertical
        )
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        if (display == Display.None) {
            bounds = Rect(x, y, 0, 0)
            return
        }
        bounds = Rect(x, y, width, height)
        val visibleChildren = layoutChildren()
        if (visibleChildren.isEmpty()) return

        when {
            stackLayout -> renderStack(ctx, visibleChildren)
            display == Display.Flex -> renderFlex(ctx, visibleChildren)
            display == Display.Grid -> renderGrid(ctx, visibleChildren)
            display == Display.Inline -> renderInline(ctx, visibleChildren)
            else -> renderBlock(ctx, visibleChildren)
        }
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        backgroundColor?.let {
            out.add(RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, it))
        }
        addBackgroundImageCommand(out)
        addBorderCommands(out)
        super.buildRenderCommands(ctx, out)
    }

    override fun defaultBackgroundColor(): Int? = backgroundColor

    override fun applyBackgroundColor(value: Int?) {
        backgroundColor = value
    }

    private fun layoutChildren(): List<DOMNode> {
        return children.filter { it.display != Display.None }
    }

    private fun measureStack(ctx: UiMeasureContext, children: List<DOMNode>): Size {
        var maxWidth = 0
        var maxHeight = 0
        children.forEach { child ->
            val size = child.measure(ctx)
            maxWidth = maxOf(maxWidth, size.width + child.margin.horizontal)
            maxHeight = maxOf(maxHeight, size.height + child.margin.vertical)
        }
        return Size(maxWidth, maxHeight)
    }

    private fun renderStack(ctx: UiMeasureContext, children: List<DOMNode>) {
        val cx = contentX()
        val cy = contentY()
        val cw = contentWidth()
        val ch = contentHeight()
        children.forEach { child ->
            val size = measureChildForLayout(
                ctx = ctx,
                child = child,
                availableOuterWidth = cw,
                availableOuterHeight = ch
            )
            val childWidth = size.width
            val childHeight = size.height
            val childX = alignedChildX(child, cx, cw, childWidth)
            val childY = alignedChildY(child, cy, ch, childHeight)
            renderContainedChild(ctx, child, cx, cy, cw, ch, childX, childY, childWidth, childHeight)
        }
    }

    private fun measureBlock(ctx: UiMeasureContext, children: List<DOMNode>, wrapWidth: Int?): Size {
        var maxWidth = 0
        var totalHeight = 0
        var hasRows = false
        var lineWidth = 0
        var lineHeight = 0
        var lineHasItems = false

        fun flushInlineLine() {
            if (!lineHasItems) return
            if (hasRows) totalHeight += gap
            totalHeight += lineHeight
            maxWidth = maxOf(maxWidth, lineWidth)
            hasRows = true
            lineWidth = 0
            lineHeight = 0
            lineHasItems = false
        }

        children.forEach { child ->
            val measured = measureChildForLayout(
                ctx = ctx,
                child = child,
                availableOuterWidth = wrapWidth
            )
            val outerWidth = measured.width + child.margin.horizontal
            val outerHeight = measured.height + child.margin.vertical
            if (child.display == Display.Inline) {
                val spacing = if (lineHasItems) gap else 0
                val nextWidth = lineWidth + spacing + outerWidth
                val shouldWrap = wrapWidth != null && lineHasItems && nextWidth > wrapWidth
                if (shouldWrap) {
                    flushInlineLine()
                }
                if (lineHasItems) lineWidth += gap
                lineWidth += outerWidth
                lineHeight = maxOf(lineHeight, outerHeight)
                lineHasItems = true
            } else {
                flushInlineLine()
                if (hasRows) totalHeight += gap
                val blockWidth = if (wrapWidth != null && child.width == null) {
                    wrapWidth
                } else {
                    outerWidth
                }
                maxWidth = maxOf(maxWidth, blockWidth)
                totalHeight += outerHeight
                hasRows = true
            }
        }
        flushInlineLine()

        val resolvedWidth = wrapWidth ?: maxWidth
        return Size(resolvedWidth.coerceAtLeast(0), totalHeight.coerceAtLeast(0))
    }

    private fun renderBlock(ctx: UiMeasureContext, children: List<DOMNode>) {
        val cx = contentX()
        val cy = contentY()
        val cw = contentWidth()
        val ch = contentHeight()
        var cursorY = cy
        var hasRows = false

        data class InlinePlacement(
            val child: DOMNode,
            val width: Int,
            val height: Int,
            val relX: Int,
            val outerHeight: Int
        )

        val line = ArrayList<InlinePlacement>(8)
        var lineWidth = 0
        var lineHeight = 0

        fun flushInlineLine() {
            if (line.isEmpty()) return
            if (hasRows) cursorY += gap
            line.forEach { placement ->
                val child = placement.child
                val x = cx + placement.relX + child.margin.left
                val y = cursorY + child.margin.top
                renderContainedChild(ctx, child, cx, cy, cw, ch, x, y, placement.width, placement.height)
            }
            cursorY += lineHeight
            line.clear()
            lineWidth = 0
            lineHeight = 0
            hasRows = true
        }

        children.forEach { child ->
            val measured = measureChildForLayout(
                ctx = ctx,
                child = child,
                availableOuterWidth = cw,
                availableOuterHeight = ch
            )
            if (child.display == Display.Inline) {
                val outerWidth = measured.width + child.margin.horizontal
                val outerHeight = measured.height + child.margin.vertical
                val spacing = if (line.isEmpty()) 0 else gap
                val nextWidth = lineWidth + spacing + outerWidth
                if (line.isNotEmpty() && nextWidth > cw) {
                    flushInlineLine()
                }
                val relX = if (line.isEmpty()) 0 else lineWidth + gap
                line.add(
                    InlinePlacement(
                        child = child,
                        width = measured.width,
                        height = measured.height,
                        relX = relX,
                        outerHeight = outerHeight
                    )
                )
                lineWidth = relX + outerWidth
                lineHeight = maxOf(lineHeight, outerHeight)
            } else {
                flushInlineLine()
                if (hasRows) cursorY += gap
                val widthToRender = if (child.width == null) {
                    (cw - child.margin.horizontal).coerceAtLeast(0)
                } else {
                    measured.width
                }
                val heightToRender = measured.height
                val childX = alignedChildX(child, cx, cw, widthToRender)
                val childY = cursorY + child.margin.top
                renderContainedChild(ctx, child, cx, cy, cw, ch, childX, childY, widthToRender, heightToRender)
                cursorY += heightToRender + child.margin.vertical
                hasRows = true
            }
        }
        flushInlineLine()
    }

    private fun measureInline(ctx: UiMeasureContext, children: List<DOMNode>, wrapWidth: Int?): Size {
        var maxLineWidth = 0
        var totalHeight = 0
        var lineWidth = 0
        var lineHeight = 0
        var lineHasItems = false

        fun flushLine() {
            if (!lineHasItems) return
            if (totalHeight > 0) totalHeight += gap
            totalHeight += lineHeight
            maxLineWidth = maxOf(maxLineWidth, lineWidth)
            lineWidth = 0
            lineHeight = 0
            lineHasItems = false
        }

        children.forEach { child ->
            val measured = measureChildForLayout(
                ctx = ctx,
                child = child,
                availableOuterWidth = wrapWidth
            )
            val outerWidth = measured.width + child.margin.horizontal
            val outerHeight = measured.height + child.margin.vertical
            val spacing = if (lineHasItems) gap else 0
            val nextWidth = lineWidth + spacing + outerWidth
            if (wrapWidth != null && lineHasItems && nextWidth > wrapWidth) {
                flushLine()
            }
            if (lineHasItems) lineWidth += gap
            lineWidth += outerWidth
            lineHeight = maxOf(lineHeight, outerHeight)
            lineHasItems = true
        }
        flushLine()

        val finalWidth = if (wrapWidth != null) {
            minOf(maxLineWidth, wrapWidth.coerceAtLeast(0))
        } else {
            maxLineWidth
        }
        return Size(finalWidth.coerceAtLeast(0), totalHeight.coerceAtLeast(0))
    }

    private fun renderInline(ctx: UiMeasureContext, children: List<DOMNode>) {
        val cx = contentX()
        val cy = contentY()
        val cw = contentWidth()
        val ch = contentHeight()
        var cursorX = cx
        var cursorY = cy
        var lineHeight = 0
        var lineHasItems = false

        children.forEach { child ->
            val measured = measureChildForLayout(
                ctx = ctx,
                child = child,
                availableOuterWidth = cw,
                availableOuterHeight = ch
            )
            val outerWidth = measured.width + child.margin.horizontal
            val outerHeight = measured.height + child.margin.vertical
            if (lineHasItems && cursorX + gap + outerWidth > cx + cw) {
                cursorX = cx
                cursorY += lineHeight + gap
                lineHeight = 0
                lineHasItems = false
            }
            if (lineHasItems) cursorX += gap
            val childX = cursorX + child.margin.left
            val childY = cursorY + child.margin.top
            renderContainedChild(ctx, child, cx, cy, cw, ch, childX, childY, measured.width, measured.height)
            cursorX += outerWidth
            lineHeight = maxOf(lineHeight, outerHeight)
            lineHasItems = true
        }
    }

    private data class FlexItem(
        val child: DOMNode,
        val measuredMain: Int,
        val measuredCross: Int,
        val mainMarginStart: Int,
        val mainMarginEnd: Int,
        val crossMarginStart: Int,
        val crossMarginEnd: Int,
        val explicitMain: Int?,
        val explicitCross: Int?,
        val resolvedFlexBasis: Int?
    )

    private fun measureFlex(ctx: UiMeasureContext, children: List<DOMNode>, wrapWidth: Int?): Size {
        val isRow = flexDirection == FlexDirection.Row
        var totalMain = 0
        var maxCross = 0
        children.forEachIndexed { index, child ->
            val measured = measureChildForLayout(ctx, child, wrapWidth)
            val main = if (isRow) measured.width else measured.height
            val cross = if (isRow) measured.height else measured.width
            val outerMain = main + if (isRow) child.margin.horizontal else child.margin.vertical
            val outerCross = cross + if (isRow) child.margin.vertical else child.margin.horizontal
            totalMain += outerMain
            if (index > 0) totalMain += gap
            maxCross = maxOf(maxCross, outerCross)
        }
        return if (isRow) {
            Size(totalMain, maxCross)
        } else {
            Size(maxCross, totalMain)
        }
    }

    private fun renderFlex(ctx: UiMeasureContext, children: List<DOMNode>) {
        val isRow = flexDirection == FlexDirection.Row
        val cx = contentX()
        val cy = contentY()
        val availableMain = if (isRow) contentWidth() else contentHeight()
        val availableCross = if (isRow) contentHeight() else contentWidth()
        val availableOuterWidth = contentWidth()
        val availableOuterHeight = contentHeight()

        if (children.isEmpty()) return

        val items = children.map { child ->
            val measured = measureChildForLayout(
                ctx = ctx,
                child = child,
                availableOuterWidth = availableOuterWidth,
                availableOuterHeight = availableOuterHeight
            )
            FlexItem(
                child = child,
                measuredMain = if (isRow) measured.width else measured.height,
                measuredCross = if (isRow) measured.height else measured.width,
                mainMarginStart = if (isRow) child.margin.left else child.margin.top,
                mainMarginEnd = if (isRow) child.margin.right else child.margin.bottom,
                crossMarginStart = if (isRow) child.margin.top else child.margin.left,
                crossMarginEnd = if (isRow) child.margin.bottom else child.margin.right,
                explicitMain = if (isRow) child.width else child.height,
                explicitCross = if (isRow) child.height else child.width,
                resolvedFlexBasis = child.resolveFlexBasisForAxis(
                    ctx = ctx,
                    parentContentWidth = availableOuterWidth,
                    parentContentHeight = availableOuterHeight,
                    axis = flexDirection
                ),
            )
        }

        val baseMain = DoubleArray(items.size)
        var totalOuterBaseMain = 0.0
        var totalGrow = 0.0
        var totalShrinkWeight = 0.0
        items.forEachIndexed { index, item ->
            val base = (item.explicitMain ?: item.resolvedFlexBasis ?: item.measuredMain).coerceAtLeast(0)
            baseMain[index] = base.toDouble()
            totalOuterBaseMain += base + item.mainMarginStart + item.mainMarginEnd
            totalGrow += item.child.flexGrow.coerceAtLeast(0f).toDouble()
            totalShrinkWeight += (item.child.flexShrink.coerceAtLeast(0f) * base).toDouble()
        }
        val gapTotal = gap * (items.size - 1).coerceAtLeast(0)
        val freeSpace = availableMain - totalOuterBaseMain - gapTotal

        val finalMain = DoubleArray(items.size) { baseMain[it] }
        if (freeSpace > 0.0 && totalGrow > 0.0) {
            items.forEachIndexed { index, item ->
                val grow = item.child.flexGrow.coerceAtLeast(0f).toDouble()
                finalMain[index] += freeSpace * (grow / totalGrow)
            }
        } else if (freeSpace < 0.0 && totalShrinkWeight > 0.0) {
            items.forEachIndexed { index, item ->
                val weight = (item.child.flexShrink.coerceAtLeast(0f) * baseMain[index].toFloat()).toDouble()
                finalMain[index] += freeSpace * (weight / totalShrinkWeight)
                if (finalMain[index] < 0.0) finalMain[index] = 0.0
            }
        }

        val usedMain = finalMain.indices.sumOf { index ->
            finalMain[index] +
                    items[index].mainMarginStart +
                    items[index].mainMarginEnd
        } + gapTotal
        val extra = (availableMain - usedMain).coerceAtLeast(0.0)
        val (mainStartOffset, spacing) = justifyOffsets(justifyContent, items.size, extra, gap.toDouble())

        var cursorMain = mainStartOffset
        items.forEachIndexed { index, item ->
            if (index > 0) cursorMain += spacing
            cursorMain += item.mainMarginStart
            val mainSize = finalMain[index].roundToInt().coerceAtLeast(0)
            val crossAvailable = (availableCross - item.crossMarginStart - item.crossMarginEnd).coerceAtLeast(0)
            val crossSize = when {
                item.explicitCross != null -> item.explicitCross
                alignItems == AlignItems.Stretch -> crossAvailable
                else -> item.measuredCross.coerceAtMost(crossAvailable)
            }.coerceAtLeast(0)

            val crossRoom = (availableCross - crossSize - item.crossMarginStart - item.crossMarginEnd).coerceAtLeast(0)
            val crossOffset = when (alignItems) {
                AlignItems.Start, AlignItems.Stretch -> 0
                AlignItems.Center -> crossRoom / 2
                AlignItems.End -> crossRoom
            }

            val childX = if (isRow) {
                cx + cursorMain.roundToInt()
            } else {
                cx + item.crossMarginStart + crossOffset
            }
            val childY = if (isRow) {
                cy + item.crossMarginStart + crossOffset
            } else {
                cy + cursorMain.roundToInt()
            }
            val childWidth = if (isRow) mainSize else crossSize
            val childHeight = if (isRow) crossSize else mainSize
            renderContainedChild(
                ctx = ctx,
                child = item.child,
                parentContentX = cx,
                parentContentY = cy,
                parentContentWidth = if (isRow) availableMain else availableCross,
                parentContentHeight = if (isRow) availableCross else availableMain,
                desiredX = childX,
                desiredY = childY,
                desiredWidth = childWidth,
                desiredHeight = childHeight
            )

            cursorMain += mainSize + item.mainMarginEnd
        }
    }

    private fun justifyOffsets(
        justify: JustifyContent,
        count: Int,
        extra: Double,
        baseGap: Double
    ): Pair<Double, Double> {
        if (count <= 0) return 0.0 to baseGap
        return when (justify) {
            JustifyContent.Start -> 0.0 to baseGap
            JustifyContent.Center -> (extra / 2.0) to baseGap
            JustifyContent.End -> extra to baseGap
            JustifyContent.SpaceBetween -> {
                val spacing = if (count > 1) baseGap + (extra / (count - 1).toDouble()) else baseGap
                0.0 to spacing
            }

            JustifyContent.SpaceAround -> {
                val spacing = baseGap + (extra / count.toDouble())
                (extra / (count.toDouble() * 2.0)) to spacing
            }

            JustifyContent.SpaceEvenly -> {
                val spacing = baseGap + (extra / (count + 1).toDouble())
                (extra / (count + 1).toDouble()) to spacing
            }
        }
    }

    private data class GridPlacement(
        val child: DOMNode,
        val row: Int,
        val column: Int,
        val rowSpan: Int,
        val columnSpan: Int
    )

    private fun measureGrid(ctx: UiMeasureContext, children: List<DOMNode>, fixedWidth: Int?): Size {
        val columns = gridColumns.coerceAtLeast(1)
        if (children.isEmpty()) return Size(fixedWidth ?: 0, 0)

        val placements = computeGridPlacements(children, columns)
        val rowCount = resolveGridRowCount(placements)
        val colWidth = when {
            fixedWidth != null -> ((fixedWidth - gap * (columns - 1)).coerceAtLeast(0)) / columns
            else -> placements.maxOfOrNull { placement ->
                val child = placement.child
                val outerWidth = child.measure(ctx).width + child.margin.horizontal
                val totalGapWithinSpan = gap * (placement.columnSpan - 1).coerceAtLeast(0)
                ((outerWidth - totalGapWithinSpan).coerceAtLeast(0) + placement.columnSpan - 1) /
                        placement.columnSpan.coerceAtLeast(1)
            } ?: 0
        }
        val rowHeights = computeGridRowHeights(ctx, placements, rowCount, colWidth)
        val measuredWidth = fixedWidth ?: (columns * colWidth + gap * (columns - 1))
        val measuredHeight = rowHeights.sum() + gap * (rowHeights.size - 1).coerceAtLeast(0)
        return Size(measuredWidth.coerceAtLeast(0), measuredHeight.coerceAtLeast(0))
    }

    private fun renderGrid(ctx: UiMeasureContext, children: List<DOMNode>) {
        val columns = gridColumns.coerceAtLeast(1)
        val availableWidth = contentWidth()
        val cx = contentX()
        val cy = contentY()
        if (children.isEmpty()) return

        val colWidth = ((availableWidth - gap * (columns - 1)).coerceAtLeast(0)) / columns
        val placements = computeGridPlacements(children, columns)
        val rowCount = resolveGridRowCount(placements)
        val rowHeights = computeGridRowHeights(ctx, placements, rowCount, colWidth)
        val rowOffsets = IntArray(rowHeights.size)
        var accumY = 0
        rowHeights.indices.forEach { row ->
            rowOffsets[row] = accumY
            accumY += rowHeights[row] + gap
        }

        placements.forEach { placement ->
            val child = placement.child
            val cellX = cx + placement.column * (colWidth + gap)
            val cellY = cy + rowOffsets[placement.row]
            val cellWidth = placement.columnSpan * colWidth + (placement.columnSpan - 1) * gap
            val cellHeight = rowSpanHeight(rowHeights, placement.row, placement.rowSpan)

            val availableCellWidth = (cellWidth - child.margin.horizontal).coerceAtLeast(0)
            val availableCellHeight = (cellHeight - child.margin.vertical).coerceAtLeast(0)
            val measured = measureChildForLayout(
                ctx = ctx,
                child = child,
                availableOuterWidth = cellWidth.coerceAtLeast(0),
                availableOuterHeight = cellHeight.coerceAtLeast(0)
            )

            val childWidth = when {
                child.width != null -> child.width!!.coerceAtMost(availableCellWidth)
                justifyItems == JustifyItems.Stretch -> availableCellWidth
                else -> measured.width.coerceAtMost(availableCellWidth)
            }.coerceAtLeast(0)
            val childHeight = when {
                child.height != null -> child.height!!.coerceAtMost(availableCellHeight)
                alignItems == AlignItems.Stretch -> availableCellHeight
                else -> measured.height.coerceAtMost(availableCellHeight)
            }.coerceAtLeast(0)

            val xSpace = (availableCellWidth - childWidth).coerceAtLeast(0)
            val ySpace = (availableCellHeight - childHeight).coerceAtLeast(0)
            val xOffset = when (justifyItems) {
                JustifyItems.Start, JustifyItems.Stretch -> 0
                JustifyItems.Center -> xSpace / 2
                JustifyItems.End -> xSpace
            }
            val yOffset = when (alignItems) {
                AlignItems.Start, AlignItems.Stretch -> 0
                AlignItems.Center -> ySpace / 2
                AlignItems.End -> ySpace
            }

            val childX = cellX + child.margin.left + xOffset
            val childY = cellY + child.margin.top + yOffset
            renderContainedChild(
                ctx,
                child,
                cellX,
                cellY,
                cellWidth,
                cellHeight,
                childX,
                childY,
                childWidth,
                childHeight
            )
        }
    }

    private fun computeGridPlacements(children: List<DOMNode>, columns: Int): List<GridPlacement> {
        if (children.isEmpty()) return emptyList()
        val occupied = HashSet<Long>(children.size * 4)
        val placements = ArrayList<GridPlacement>(children.size)
        var searchRows = (gridRows ?: 1).coerceAtLeast(1)

        children.forEach { child ->
            val colSpan = child.gridColumnSpan.coerceAtLeast(1).coerceAtMost(columns)
            val rowSpan = child.gridRowSpan.coerceAtLeast(1)
            var placed: GridPlacement? = null
            var safety = 0
            while (placed == null && safety < 16) {
                val candidates = candidateGridCells(columns, searchRows)
                for ((row, col) in candidates) {
                    if (canPlaceGridCell(row, col, rowSpan, colSpan, columns, occupied)) {
                        markGridCell(row, col, rowSpan, colSpan, occupied)
                        placed = GridPlacement(
                            child = child,
                            row = row,
                            column = col,
                            rowSpan = rowSpan,
                            columnSpan = colSpan
                        )
                        break
                    }
                }
                if (placed == null) {
                    searchRows = (searchRows * 2).coerceAtMost(children.size * 8 + 32)
                    safety += 1
                }
            }

            placements += placed ?: GridPlacement(
                child = child,
                row = placements.size / columns,
                column = placements.size % columns,
                rowSpan = rowSpan,
                columnSpan = colSpan
            )
        }
        return placements
    }

    private fun candidateGridCells(columns: Int, rows: Int): Sequence<Pair<Int, Int>> {
        return sequence {
            val maxRows = rows.coerceAtLeast(1)
            if (gridAutoFlow == GridAutoFlow.Column) {
                for (col in 0 until columns) {
                    for (row in 0 until maxRows) {
                        yield(row to col)
                    }
                }
            } else {
                for (row in 0 until maxRows) {
                    for (col in 0 until columns) {
                        yield(row to col)
                    }
                }
            }
        }
    }

    private fun canPlaceGridCell(
        row: Int,
        column: Int,
        rowSpan: Int,
        colSpan: Int,
        columns: Int,
        occupied: Set<Long>
    ): Boolean {
        if (column + colSpan > columns) return false
        for (r in row until row + rowSpan) {
            for (c in column until column + colSpan) {
                if (encodeGridCell(r, c) in occupied) return false
            }
        }
        return true
    }

    private fun markGridCell(
        row: Int,
        column: Int,
        rowSpan: Int,
        colSpan: Int,
        occupied: MutableSet<Long>
    ) {
        for (r in row until row + rowSpan) {
            for (c in column until column + colSpan) {
                occupied += encodeGridCell(r, c)
            }
        }
    }

    private fun encodeGridCell(row: Int, column: Int): Long {
        return (row.toLong() shl 32) or (column.toLong() and 0xFFFF_FFFFL)
    }

    private fun resolveGridRowCount(placements: List<GridPlacement>): Int {
        val placedRows = placements.maxOfOrNull { it.row + it.rowSpan } ?: 0
        return maxOf(placedRows, gridRows ?: 0).coerceAtLeast(1)
    }

    private fun computeGridRowHeights(
        ctx: UiMeasureContext,
        placements: List<GridPlacement>,
        rowCount: Int,
        colWidth: Int
    ): IntArray {
        val rowHeights = IntArray(rowCount)
        placements.forEach { placement ->
            val child = placement.child
            val spanWidth = placement.columnSpan * colWidth + (placement.columnSpan - 1) * gap
            val availableWidth = (spanWidth - child.margin.horizontal).coerceAtLeast(0)
            val measured = measureChildForLayout(ctx, child, availableWidth)
            val childHeight = child.height ?: measured.height
            val outerHeight = childHeight + child.margin.vertical
            if (placement.rowSpan <= 1) {
                rowHeights[placement.row] = maxOf(rowHeights[placement.row], outerHeight)
            } else {
                val gapInside = gap * (placement.rowSpan - 1)
                val split = ((outerHeight - gapInside).coerceAtLeast(0) + placement.rowSpan - 1) / placement.rowSpan
                for (row in placement.row until (placement.row + placement.rowSpan).coerceAtMost(rowCount)) {
                    rowHeights[row] = maxOf(rowHeights[row], split)
                }
            }
            if (availableWidth <= 0) {
                rowHeights[placement.row] = maxOf(rowHeights[placement.row], outerHeight)
            }
        }
        return rowHeights
    }

    private fun measureChildForLayout(
        ctx: UiMeasureContext,
        child: DOMNode,
        availableOuterWidth: Int?,
        availableOuterHeight: Int? = null
    ): Size {
        child.resolveLayoutStyleValues(
            ctx = ctx,
            parentContentWidth = availableOuterWidth,
            parentContentHeight = availableOuterHeight
        )
        return child.measureForLayout(ctx, availableOuterWidth)
    }

    private fun constrainContentWidthForOuterLimit(outerLimit: Int, child: DOMNode): Int {
        val extrasX = child.margin.horizontal + child.padding.horizontal + child.border.horizontal
        return (outerLimit - extrasX).coerceAtLeast(0)
    }

    private fun resolvedContentLimit(constrainedContentWidth: Int?): Int? {
        val boundedExplicit = width?.let { explicit ->
            constrainedContentWidth?.let { limit ->
                minOf(explicit, limit.coerceAtLeast(0))
            } ?: explicit
        }
        return boundedExplicit ?: constrainedContentWidth?.coerceAtLeast(0)
    }

    private fun rowSpanHeight(rowHeights: IntArray, row: Int, rowSpan: Int): Int {
        var total = 0
        for (r in row until (row + rowSpan).coerceAtMost(rowHeights.size)) {
            if (r > row) total += gap
            total += rowHeights[r]
        }
        return total
    }

    private fun alignedChildX(child: DOMNode, contentX: Int, availableWidth: Int, childWidth: Int): Int {
        val interactableWidth = (availableWidth - child.margin.horizontal).coerceAtLeast(0)
        val horizontalOffset = when (child.align) {
            StyleAlign.START -> 0
            StyleAlign.CENTER -> (interactableWidth - childWidth) / 2
            StyleAlign.END -> interactableWidth - childWidth
        }
        return contentX + child.margin.left + horizontalOffset.coerceAtLeast(0)
    }

    private fun alignedChildY(child: DOMNode, contentY: Int, availableHeight: Int, childHeight: Int): Int {
        val interactableHeight = (availableHeight - child.margin.vertical).coerceAtLeast(0)
        val verticalOffset = when (child.align) {
            StyleAlign.START -> 0
            StyleAlign.CENTER -> (interactableHeight - childHeight) / 2
            StyleAlign.END -> interactableHeight - childHeight
        }
        return contentY + child.margin.top + verticalOffset.coerceAtLeast(0)
    }

    private fun renderContainedChild(
        ctx: UiMeasureContext,
        child: DOMNode,
        parentContentX: Int,
        parentContentY: Int,
        parentContentWidth: Int,
        parentContentHeight: Int,
        desiredX: Int,
        desiredY: Int,
        desiredWidth: Int,
        desiredHeight: Int
    ) {
        val minX = parentContentX + child.margin.left
        val minY = parentContentY + child.margin.top
        val maxRight = parentContentX + parentContentWidth - child.margin.right
        val maxBottom = parentContentY + parentContentHeight - child.margin.bottom

        val x = desiredX.coerceAtLeast(minX)
        val y = desiredY.coerceAtLeast(minY)

        val maxWidth = (maxRight - x).coerceAtLeast(0)
        val maxHeight = (maxBottom - y).coerceAtLeast(0)
        val width = desiredWidth.coerceAtLeast(0).coerceAtMost(maxWidth)
        val height = desiredHeight.coerceAtLeast(0).coerceAtMost(maxHeight)

        child.render(ctx, x, y, width, height)
    }
}
