package org.dreamfinity.dsgl.core.contextmenu

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine

class ContextMenuEngine(
    private val clock: ContextMenuClock = SystemContextMenuClock,
    private val measurementCache: ContextMenuMeasurementCache = ContextMenuMeasurementCache()
) : ContextMenuHost {
    private data class OpenLevel(
        val token: Long,
        val entries: List<MenuEntry>,
        val placementMode: Int,
        val fontId: String?,
        val fontSize: Int?,
        var anchorRect: Rect,
        var parentLevelIndex: Int,
        var parentEntryIndex: Int,
        var panelRect: Rect = Rect(0, 0, 1, 1),
        var hoveredIndex: Int = -1,
        var selectedIndex: Int = -1,
        var scrollOffset: Int = 0,
        var measurement: ContextMenuMeasurementCache.Measurement? = null
    )

    private data class PendingOpen(
        val parentLevel: Int,
        val parentEntryIndex: Int,
        val dueMs: Long
    )

    private data class PendingTrim(
        val fromLevel: Int,
        val dueMs: Long
    )

    data class StackSnapshot(
        val levelCount: Int,
        val hoveredIndices: List<Int>,
        val selectedIndices: List<Int>
    )

    private val levels: MutableList<OpenLevel> = ArrayList(4)
    private var style: ContextMenuStyle = ContextMenuStyle()
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var viewportScale: Float = 1f
    private var layoutDirty: Boolean = true
    private var pendingOpen: PendingOpen? = null
    private var pendingTrim: PendingTrim? = null
    private var lastPointerX: Int = 0
    private var lastPointerY: Int = 0
    private var lastStyleRevision: Long = Long.MIN_VALUE
    private var lastMeasureContext: UiMeasureContext? = null

    fun setStyle(next: ContextMenuStyle) {
        if (style == next) return
        style = next
        layoutDirty = true
    }

    fun currentStyle(): ContextMenuStyle = style

    fun measurementComputeCount(): Long = measurementCache.computeCount

    fun snapshot(): StackSnapshot {
        return StackSnapshot(
            levelCount = levels.size,
            hoveredIndices = levels.map { it.hoveredIndex },
            selectedIndices = levels.map { it.selectedIndex }
        )
    }

    fun debugPanelRect(levelIndex: Int): Rect? {
        return levels.getOrNull(levelIndex)?.panelRect
    }

    fun debugEntryRect(levelIndex: Int, entryIndex: Int): Rect? {
        val level = levels.getOrNull(levelIndex) ?: return null
        return entryRect(level, entryIndex)
    }

    override fun openAtCursor(model: ContextMenuModel, x: Int, y: Int) {
        if (model.entries.isEmpty()) {
            closeAll()
            return
        }
        levels.clear()
        levels += OpenLevel(
            token = model.token,
            entries = model.entries,
            placementMode = PLACEMENT_CURSOR,
            fontId = model.fontId,
            fontSize = model.fontSize,
            anchorRect = Rect(x, y, 0, 0),
            parentLevelIndex = -1,
            parentEntryIndex = -1
        )
        pendingOpen = null
        pendingTrim = null
        layoutDirty = true
    }

    override fun openAnchored(model: ContextMenuModel, anchorRect: Rect) {
        if (model.entries.isEmpty()) {
            closeAll()
            return
        }
        levels.clear()
        levels += OpenLevel(
            token = model.token,
            entries = model.entries,
            placementMode = PLACEMENT_ANCHORED,
            fontId = model.fontId,
            fontSize = model.fontSize,
            anchorRect = anchorRect,
            parentLevelIndex = -1,
            parentEntryIndex = -1
        )
        pendingOpen = null
        pendingTrim = null
        layoutDirty = true
    }

    override fun closeAll() {
        levels.clear()
        pendingOpen = null
        pendingTrim = null
        layoutDirty = true
    }

    override fun isOpen(): Boolean = levels.isNotEmpty()

    fun onFrame(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        viewportScale: Float = 1f
    ) {
        lastMeasureContext = measureContext
        if (this.viewportWidth != viewportWidth || this.viewportHeight != viewportHeight) {
            this.viewportWidth = viewportWidth
            this.viewportHeight = viewportHeight
            layoutDirty = true
        }
        if (this.viewportScale != viewportScale) {
            this.viewportScale = viewportScale
            layoutDirty = true
        }
        val styleRevision = StyleEngine.currentStyleRevision()
        if (styleRevision != lastStyleRevision) {
            lastStyleRevision = styleRevision
            layoutDirty = true
        }
        processTimers()
        ensureLayout()
    }

    fun appendOverlayCommands(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>
    ) {
        if (!isOpen()) return
        onFrame(
            measureContext = measureContext,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            viewportScale = viewportScale
        )
        if (!isOpen()) return

        out += RenderCommand.PushClip(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1))
        levels.forEach { level ->
            val panel = level.panelRect
            val measurement = level.measurement ?: return@forEach
            val fontId = level.fontId ?: style.fontId
            val fontSize = level.fontSize ?: style.fontSize
            val shadowX = panel.x + 2
            val shadowY = panel.y + 2
            out += RenderCommand.DrawRect(shadowX, shadowY, panel.width, panel.height, style.panelShadowColor)
            out += RenderCommand.DrawRect(panel.x, panel.y, panel.width, panel.height, style.panelBackgroundColor)
            out += RenderCommand.DrawRect(panel.x, panel.y, panel.width, 1, style.panelBorderColor)
            out += RenderCommand.DrawRect(panel.x, panel.y + panel.height - 1, panel.width, 1, style.panelBorderColor)
            out += RenderCommand.DrawRect(panel.x, panel.y, 1, panel.height, style.panelBorderColor)
            out += RenderCommand.DrawRect(panel.x + panel.width - 1, panel.y, 1, panel.height, style.panelBorderColor)

            val clipX = panel.x + 1
            val clipY = panel.y + 1
            val clipW = (panel.width - 2).coerceAtLeast(1)
            val clipH = (panel.height - 2).coerceAtLeast(1)
            out += RenderCommand.PushClip(clipX, clipY, clipW, clipH)

            val fontHeight = measureContext.fontHeight(fontId, fontSize)
            measurement.snapshots.indices.forEach { index ->
                val rowRect = entryRect(level, index) ?: return@forEach
                if (rowRect.y + rowRect.height < clipY || rowRect.y > clipY + clipH) {
                    return@forEach
                }
                val snapshot = measurement.snapshots[index]
                if (snapshot.kind == ContextMenuMeasurementCache.KIND_SEPARATOR) {
                    val separatorY = rowRect.y + (rowRect.height / 2)
                    val separatorX = rowRect.x + style.separatorInsetX
                    val separatorW = (rowRect.width - style.separatorInsetX * 2).coerceAtLeast(1)
                    out += RenderCommand.DrawRect(separatorX, separatorY, separatorW, 1, style.separatorColor)
                    return@forEach
                }

                val itemRect = Rect(
                    rowRect.x,
                    rowRect.y,
                    rowRect.width,
                    rowRect.height
                )
                val isHovered = index == level.hoveredIndex
                val isSelected = index == level.selectedIndex
                if (isHovered) {
                    out += RenderCommand.DrawRect(itemRect.x, itemRect.y, itemRect.width, itemRect.height, style.itemHoverBackgroundColor)
                } else if (isSelected) {
                    out += RenderCommand.DrawRect(itemRect.x, itemRect.y, itemRect.width, itemRect.height, style.itemSelectedBackgroundColor)
                }

                val textY = itemRect.y + ((itemRect.height - fontHeight).coerceAtLeast(0) / 2)
                val baseX = itemRect.x + style.rowPaddingX
                val indicatorX = baseX
                val labelX = indicatorX + measurement.indicatorWidth + style.contentSpacing
                val textColor = if (snapshot.enabled) style.itemTextColor else style.disabledTextColor

                val indicatorText = when {
                    snapshot.checked -> ContextMenuGlyphs.CHECK_MARK
                    !snapshot.icon.isNullOrEmpty() -> snapshot.icon
                    else -> null
                }
                if (!indicatorText.isNullOrEmpty()) {
                    val indicatorColor = if (snapshot.checked) style.checkMarkColor else textColor
                    out += RenderCommand.DrawText(
                        text = indicatorText,
                        x = indicatorX,
                        y = textY,
                        color = indicatorColor,
                        fontId = fontId,
                        fontSize = fontSize
                    )
                }

                out += RenderCommand.DrawText(
                    text = snapshot.label,
                    x = labelX,
                    y = textY,
                    color = textColor,
                    fontId = fontId,
                    fontSize = fontSize
                )

                val hintText = when {
                    snapshot.kind == ContextMenuMeasurementCache.KIND_SUBMENU && snapshot.hint.isNullOrEmpty() -> ContextMenuGlyphs.SUBMENU_ARROW
                    else -> snapshot.hint
                }
                if (!hintText.isNullOrEmpty()) {
                    val hintWidth = measureContext.measureText(hintText, fontId, fontSize)
                    val hintX = itemRect.x + itemRect.width - style.rowPaddingX - hintWidth
                    val hintColor = when {
                        !snapshot.enabled -> style.disabledTextColor
                        snapshot.kind == ContextMenuMeasurementCache.KIND_SUBMENU && snapshot.hint.isNullOrEmpty() ->
                            style.submenuArrowColor

                        else -> style.hintTextColor
                    }
                    out += RenderCommand.DrawText(
                        text = hintText,
                        x = hintX,
                        y = textY,
                        color = hintColor,
                        fontId = fontId,
                        fontSize = fontSize
                    )
                }
            }

            out += RenderCommand.PopClip
        }
        out += RenderCommand.PopClip
    }

    fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean {
        if (!isOpen()) return false
        lastPointerX = mouseX
        lastPointerY = mouseY
        processTimers()
        ensureLayout()

        val hit = hitTest(mouseX, mouseY)
        if (hit == null) {
            if (levels.size > 1) {
                scheduleTrim(fromLevel = 1, delayMs = style.submenuCloseDelayMs)
            }
            return true
        }

        val hitLevel = levels[hit.levelIndex]
        if (hitLevel.hoveredIndex != hit.entryIndex) {
            hitLevel.hoveredIndex = hit.entryIndex
        }
        if (hit.entryIndex != -1) {
            hitLevel.selectedIndex = hit.entryIndex
        }

        val snapshot = hitLevel.measurement?.snapshots?.getOrNull(hit.entryIndex)
        if (snapshot != null && snapshot.kind == ContextMenuMeasurementCache.KIND_SUBMENU && snapshot.enabled) {
            scheduleSubmenuOpen(hit.levelIndex, hit.entryIndex)
        } else {
            scheduleTrim(fromLevel = hit.levelIndex + 1, delayMs = style.submenuCloseDelayMs)
        }

        if (hit.levelIndex >= 1) {
            pendingTrim = null
        }
        return true
    }

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        if (!isOpen()) return false
        lastPointerX = mouseX
        lastPointerY = mouseY
        processTimers()
        ensureLayout()
        val hit = hitTest(mouseX, mouseY)
        if (hit == null) {
            closeAll()
            return true
        }
        if (hit.entryIndex == -1) {
            return true
        }
        if (button == MouseButton.LEFT || button == MouseButton.RIGHT) {
            activate(hit.levelIndex, hit.entryIndex)
            return true
        }
        return true
    }

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        if (!isOpen()) return false
        lastPointerX = mouseX
        lastPointerY = mouseY
        return when (button) {
            MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE -> true
        }
    }

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        if (!isOpen()) return false
        lastPointerX = mouseX
        lastPointerY = mouseY
        processTimers()
        ensureLayout()

        val hit = hitTest(mouseX, mouseY)
        val levelIndex = hit?.levelIndex ?: return true
        val level = levels[levelIndex]
        val maxScroll = maxScroll(level)
        if (maxScroll <= 0) {
            return true
        }
        val measurement = level.measurement ?: return true
        val rows = style.wheelStepRows.coerceAtLeast(1)
        val step = rows * measurement.rowHeight
        val direction = if (delta > 0) -1 else 1
        level.scrollOffset = (level.scrollOffset + direction * step).coerceIn(0, maxScroll)
        layoutDirty = true
        return true
    }

    fun handleKeyDown(keyCode: Int): Boolean {
        if (!isOpen()) return false
        processTimers()
        ensureLayout()

        val deepest = levels.lastIndex
        val level = levels.getOrNull(deepest) ?: return false
        when (keyCode) {
            KeyCodes.ESCAPE -> {
                if (levels.size > 1) {
                    popLevel()
                } else {
                    closeAll()
                }
                return true
            }

            KeyCodes.LEFT -> {
                if (levels.size > 1) {
                    popLevel()
                    return true
                }
                return false
            }

            KeyCodes.RIGHT -> {
                val index = normalizedSelection(level)
                if (index >= 0) {
                    val snapshot = level.measurement?.snapshots?.getOrNull(index)
                    if (snapshot != null &&
                        snapshot.kind == ContextMenuMeasurementCache.KIND_SUBMENU &&
                        snapshot.enabled
                    ) {
                        openSubmenu(deepest, index)
                        return true
                    }
                }
                return false
            }

            KeyCodes.UP -> {
                moveSelection(deepest, -1)
                return true
            }

            KeyCodes.DOWN -> {
                moveSelection(deepest, 1)
                return true
            }

            KeyCodes.ENTER -> {
                val index = normalizedSelection(level)
                if (index >= 0) {
                    activate(deepest, index)
                    return true
                }
                return false
            }
        }
        return false
    }

    private fun ensureLayout() {
        val ctx = lastMeasureContext ?: return
        if (!isOpen()) return
        if (!layoutDirty) return

        var index = 0
        while (index < levels.size) {
            val level = levels[index]
            val measurement = measurementCache.measure(
                menuToken = level.token,
                entries = level.entries,
                style = style,
                fontId = level.fontId ?: style.fontId,
                fontSize = level.fontSize ?: style.fontSize,
                ctx = ctx,
                dpiScale = viewportScale
            )
            level.measurement = measurement
            val panelWidth = measurement.panelWidth + style.panelPaddingX * 2
            val maxAvailableHeight = (viewportHeight - style.viewportPadding * 2).coerceAtLeast(1)
            val desiredHeight = measurement.totalContentHeight + style.panelPaddingY * 2
            val panelHeight = desiredHeight.coerceAtMost(maxAvailableHeight)

            if (level.placementMode == PLACEMENT_SUBMENU) {
                val parent = levels.getOrNull(level.parentLevelIndex)
                if (parent == null) {
                    trimLevels(index)
                    break
                }
                val parentEntryRect = entryRect(parent, level.parentEntryIndex)
                if (parentEntryRect == null) {
                    trimLevels(index)
                    break
                }
                level.anchorRect = parentEntryRect
            }

            val preferredRect = when (level.placementMode) {
                PLACEMENT_CURSOR -> {
                    Rect(level.anchorRect.x, level.anchorRect.y, panelWidth, panelHeight)
                }

                PLACEMENT_ANCHORED -> {
                    Rect(level.anchorRect.x, level.anchorRect.y + level.anchorRect.height, panelWidth, panelHeight)
                }

                else -> {
                    Rect(level.anchorRect.x + level.anchorRect.width, level.anchorRect.y, panelWidth, panelHeight)
                }
            }
            val flipCandidateX = if (level.placementMode == PLACEMENT_SUBMENU) {
                level.anchorRect.x - panelWidth
            } else {
                null
            }
            val placement = PopupPlacement.resolve(
                PopupPlacementRequest(
                    preferredRect = preferredRect,
                    popupSize = Size(panelWidth, panelHeight),
                    viewport = Rect(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1)),
                    padding = style.viewportPadding,
                    horizontalFlipX = flipCandidateX
                )
            )
            level.panelRect = placement.rect
            val maxScroll = maxScroll(level)
            if (level.scrollOffset > maxScroll) {
                level.scrollOffset = maxScroll
            }
            if (level.scrollOffset < 0) {
                level.scrollOffset = 0
            }
            index += 1
        }

        layoutDirty = false
    }

    private fun moveSelection(levelIndex: Int, direction: Int) {
        val level = levels.getOrNull(levelIndex) ?: return
        val measurement = level.measurement ?: return
        if (measurement.snapshots.isEmpty()) return

        val start = if (level.selectedIndex >= 0) level.selectedIndex else {
            if (direction >= 0) -1 else measurement.snapshots.size
        }
        var index = start
        repeat(measurement.snapshots.size) {
            index += direction
            if (index < 0) {
                index = measurement.snapshots.size - 1
            } else if (index >= measurement.snapshots.size) {
                index = 0
            }
            val snapshot = measurement.snapshots[index]
            if (snapshot.kind != ContextMenuMeasurementCache.KIND_SEPARATOR && snapshot.enabled) {
                level.selectedIndex = index
                level.hoveredIndex = index
                ensureEntryVisible(level, index)
                return
            }
        }
    }

    private fun normalizedSelection(level: OpenLevel): Int {
        val measurement = level.measurement ?: return -1
        val selected = level.selectedIndex
        if (selected in measurement.snapshots.indices) {
            val snapshot = measurement.snapshots[selected]
            if (snapshot.kind != ContextMenuMeasurementCache.KIND_SEPARATOR && snapshot.enabled) {
                return selected
            }
        }
        measurement.snapshots.indices.forEach { index ->
            val snapshot = measurement.snapshots[index]
            if (snapshot.kind != ContextMenuMeasurementCache.KIND_SEPARATOR && snapshot.enabled) {
                level.selectedIndex = index
                level.hoveredIndex = index
                ensureEntryVisible(level, index)
                return index
            }
        }
        return -1
    }

    private fun activate(levelIndex: Int, entryIndex: Int) {
        val level = levels.getOrNull(levelIndex) ?: return
        val entry = level.entries.getOrNull(entryIndex) ?: return
        val snapshot = level.measurement?.snapshots?.getOrNull(entryIndex) ?: return
        if (!snapshot.enabled || snapshot.kind == ContextMenuMeasurementCache.KIND_SEPARATOR) {
            return
        }
        when (entry) {
            is MenuEntry.Item -> {
                entry.onClick?.invoke()
                if (entry.closeOnAction) {
                    closeAll()
                } else {
                    trimLevels(levelIndex + 1)
                }
            }

            is MenuEntry.Submenu -> {
                openSubmenu(levelIndex, entryIndex)
            }

            is MenuEntry.Separator -> Unit
        }
    }

    private fun openSubmenu(parentLevelIndex: Int, parentEntryIndex: Int) {
        val parent = levels.getOrNull(parentLevelIndex) ?: return
        val parentEntry = parent.entries.getOrNull(parentEntryIndex) as? MenuEntry.Submenu ?: return
        if (!parentEntry.enabledProvider.invoke()) return
        if (parentEntry.entries.isEmpty()) {
            trimLevels(parentLevelIndex + 1)
            return
        }
        if (levels.size > parentLevelIndex + 1) {
            val opened = levels[parentLevelIndex + 1]
            if (opened.parentLevelIndex == parentLevelIndex && opened.parentEntryIndex == parentEntryIndex) {
                return
            }
            trimLevels(parentLevelIndex + 1)
        }
        levels += OpenLevel(
            token = parentEntry.token,
            entries = parentEntry.entries,
            placementMode = PLACEMENT_SUBMENU,
            fontId = parent.fontId,
            fontSize = parent.fontSize,
            anchorRect = parent.anchorRect,
            parentLevelIndex = parentLevelIndex,
            parentEntryIndex = parentEntryIndex
        )
        pendingOpen = null
        pendingTrim = null
        layoutDirty = true
        ensureLayout()
    }

    private fun popLevel() {
        if (levels.isEmpty()) return
        levels.removeAt(levels.lastIndex)
        pendingOpen = null
        pendingTrim = null
        layoutDirty = true
        if (levels.isEmpty()) {
            closeAll()
        }
    }

    private fun trimLevels(fromLevel: Int) {
        if (fromLevel < 0) {
            closeAll()
            return
        }
        if (fromLevel >= levels.size) return
        while (levels.size > fromLevel) {
            levels.removeAt(levels.lastIndex)
        }
        pendingOpen = pendingOpen?.takeIf { it.parentLevel < fromLevel }
        pendingTrim = pendingTrim?.takeIf { it.fromLevel < fromLevel }
        layoutDirty = true
        if (levels.isEmpty()) {
            closeAll()
        }
    }

    private fun scheduleSubmenuOpen(parentLevel: Int, parentEntryIndex: Int) {
        val existingChild = levels.getOrNull(parentLevel + 1)
        if (existingChild != null &&
            existingChild.parentLevelIndex == parentLevel &&
            existingChild.parentEntryIndex == parentEntryIndex
        ) {
            pendingOpen = null
            return
        }
        val due = clock.nowMs() + style.hoverOpenDelayMs
        pendingOpen = PendingOpen(parentLevel, parentEntryIndex, due)
    }

    private fun scheduleTrim(fromLevel: Int, delayMs: Long) {
        if (fromLevel >= levels.size) {
            pendingTrim = null
            return
        }
        val due = clock.nowMs() + delayMs
        val current = pendingTrim
        pendingTrim = if (current == null || fromLevel < current.fromLevel || due < current.dueMs) {
            PendingTrim(fromLevel, due)
        } else {
            current
        }
    }

    private fun processTimers() {
        if (!isOpen()) return
        val now = clock.nowMs()
        val open = pendingOpen
        if (open != null && now >= open.dueMs) {
            pendingOpen = null
            val level = levels.getOrNull(open.parentLevel)
            if (level != null && level.hoveredIndex == open.parentEntryIndex) {
                openSubmenu(open.parentLevel, open.parentEntryIndex)
            }
        }

        val trim = pendingTrim
        if (trim != null && now >= trim.dueMs) {
            pendingTrim = null
            trimLevels(trim.fromLevel)
        }
    }

    private data class Hit(
        val levelIndex: Int,
        val entryIndex: Int
    )

    private fun hitTest(mouseX: Int, mouseY: Int): Hit? {
        if (!isOpen()) return null
        for (levelIndex in levels.indices.reversed()) {
            val level = levels[levelIndex]
            if (!level.panelRect.contains(mouseX, mouseY)) continue
            val entryIndex = entryAt(level, mouseX, mouseY)
            return Hit(levelIndex = levelIndex, entryIndex = entryIndex)
        }
        return null
    }

    private fun entryAt(level: OpenLevel, mouseX: Int, mouseY: Int): Int {
        val panel = level.panelRect
        val measurement = level.measurement ?: return -1
        val contentX = panel.x + style.panelPaddingX
        val contentY = panel.y + style.panelPaddingY
        val contentW = panel.width - style.panelPaddingX * 2
        val contentH = panel.height - style.panelPaddingY * 2
        if (contentW <= 0 || contentH <= 0) return -1
        if (mouseX < contentX || mouseX >= contentX + contentW) return -1
        if (mouseY < contentY || mouseY >= contentY + contentH) return -1
        val localY = mouseY - contentY + level.scrollOffset
        measurement.entryOffsets.indices.forEach { index ->
            val top = measurement.entryOffsets[index]
            val bottom = top + measurement.entryHeights[index]
            if (localY >= top && localY < bottom) {
                return index
            }
        }
        return -1
    }

    private fun entryRect(level: OpenLevel, entryIndex: Int): Rect? {
        val measurement = level.measurement ?: return null
        if (entryIndex !in measurement.entryOffsets.indices) return null
        val rowX = level.panelRect.x + style.panelPaddingX
        val rowY = level.panelRect.y + style.panelPaddingY + measurement.entryOffsets[entryIndex] - level.scrollOffset
        val rowW = (level.panelRect.width - style.panelPaddingX * 2).coerceAtLeast(1)
        val rowH = measurement.entryHeights[entryIndex]
        return Rect(rowX, rowY, rowW, rowH)
    }

    private fun maxScroll(level: OpenLevel): Int {
        val measurement = level.measurement ?: return 0
        val visible = (level.panelRect.height - style.panelPaddingY * 2).coerceAtLeast(1)
        return (measurement.totalContentHeight - visible).coerceAtLeast(0)
    }

    private fun ensureEntryVisible(level: OpenLevel, entryIndex: Int) {
        val measurement = level.measurement ?: return
        if (entryIndex !in measurement.entryOffsets.indices) return
        val top = measurement.entryOffsets[entryIndex]
        val bottom = top + measurement.entryHeights[entryIndex]
        val viewportTop = level.scrollOffset
        val viewportBottom = viewportTop + (level.panelRect.height - style.panelPaddingY * 2).coerceAtLeast(1)
        when {
            top < viewportTop -> level.scrollOffset = top
            bottom > viewportBottom -> level.scrollOffset = (bottom - (viewportBottom - viewportTop)).coerceAtLeast(0)
        }
        val maxScroll = maxScroll(level)
        if (level.scrollOffset > maxScroll) {
            level.scrollOffset = maxScroll
        }
    }

    companion object {
        private const val PLACEMENT_CURSOR: Int = 1
        private const val PLACEMENT_ANCHORED: Int = 2
        private const val PLACEMENT_SUBMENU: Int = 3
    }
}

