package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.UsedInteractionGeometryResolver
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.overlay.DomainPortalServices
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectEntry
import org.dreamfinity.dsgl.core.select.SelectModel
import org.dreamfinity.dsgl.core.select.SelectOpenRequest

class SelectNode(
    model: SelectModel,
    controlled: Boolean = false,
    value: String? = null,
    defaultValue: String? = null,
    closeOnSelect: Boolean = true,
    ownerScope: OverlayOwnerScope = OverlayOwnerScope.Application,
    key: Any? = null,
) : DOMNode(key) {
    override val styleType: String = "select"
    override val focusable: Boolean = true

    private val ownerToken: Any
        get() = key ?: this

    var model: SelectModel = model
        set(value) {
            field = value
            reconcileSelection()
            markRenderCommandsDirty()
        }
    var controlled: Boolean = controlled
        set(value) {
            field = value
            reconcileSelection()
            markRenderCommandsDirty()
        }
    var controlledValue: String? = value
        set(value) {
            if (field == value) return
            field = value
            reconcileSelection()
            markRenderCommandsDirty()
        }
    var defaultValue: String? = defaultValue
        set(value) {
            field = value
            if (!controlled && uncontrolledValue == null && value != null && optionExists(value)) {
                uncontrolledValue = value
            }
            reconcileSelection()
            markRenderCommandsDirty()
        }
    var closeOnSelect: Boolean = closeOnSelect
    var ownerScope: OverlayOwnerScope = ownerScope
    var textColor: Int = DsglColors.TEXT
    var placeholderColor: Int = 0xFF8A8A8A.toInt()
    var backgroundColor: Int = 0xFF2E2E33.toInt()
    var disabledTextColor: Int = 0xFF8E8E8E.toInt()
    var minContentWidth: Int = 92
    var arrowGlyph: String =
        DomainPortalServices.applicationSelectEngine
            .currentStyle()
            .arrowGlyph
    var arrowSpacing: Int = 8

    private var uncontrolledValue: String? = defaultValue

    init {
        this.padding = Insets(3, 6, 3, 6)
        reconcileSelection()
        EventBus.run {
            this@SelectNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
                if (this@SelectNode.styleDisabled) return@addEventListener
                if (event.mouseButton != MouseButton.LEFT) return@addEventListener
                if (!this@SelectNode.containsGlobalPoint(event.mouseX, event.mouseY)) return@addEventListener
                FocusManager.requestFocus(this@SelectNode)
                if (DomainPortalServices.isSelectOpenFor(ownerToken)) {
                    DomainPortalServices.closeSelect(ownerToken)
                } else {
                    openPopup()
                }
                event.cancelled = true
            }
            this@SelectNode.addEventListener(Events.KEYDOWN) { event: KeyboardKeyDownEvent ->
                if (this@SelectNode.styleDisabled) return@addEventListener
                if (!FocusManager.isFocused(this@SelectNode)) return@addEventListener
                if (DomainPortalServices.isSelectOpenFor(ownerToken)) return@addEventListener
                when (event.keyCode) {
                    KeyCodes.ENTER, KeyCodes.SPACE -> {
                        openPopup()
                        event.cancelled = true
                    }

                    KeyCodes.DOWN -> {
                        openPopup()
                        DomainPortalServices.selectEngineFor(ownerScope).moveHighlight(ownerToken, 1)
                        event.cancelled = true
                    }

                    KeyCodes.UP -> {
                        openPopup()
                        DomainPortalServices.selectEngineFor(ownerScope).moveHighlight(ownerToken, -1)
                        event.cancelled = true
                    }
                }
            }
            this@SelectNode.addEventListener(Events.BLUR) { _: FocusLoseEvent ->
                if (DomainPortalServices.isSelectOpenFor(ownerToken)) {
                    DomainPortalServices.closeSelect(ownerToken)
                }
            }
        }
    }

    internal override fun measureForLayout(ctx: UiMeasureContext, availableOuterWidth: Int?): Size =
        measureWithConstraint(ctx, availableOuterWidth)

    override fun measure(ctx: UiMeasureContext): Size = measureWithConstraint(ctx, null)

    private fun measureWithConstraint(ctx: UiMeasureContext, availableOuterWidth: Int?): Size {
        val lineHeight = resolveFontSize(ctx)
        val displayText = selectedLabelOrPlaceholder()
        val displayWidth = if (displayText.isEmpty()) 0 else measureText(ctx, displayText)
        val arrowWidth = if (arrowGlyph.isEmpty()) 0 else measureText(ctx, arrowGlyph)
        val contentLimit = resolvedContentLimit(availableOuterWidth)
        val naturalWidth = width ?: maxOf(minContentWidth, displayWidth + arrowSpacing + arrowWidth)
        val contentWidth = contentLimit?.let { minOf(it, naturalWidth) } ?: naturalWidth
        val contentHeight = height ?: lineHeight
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    private fun resolvedContentLimit(availableOuterWidth: Int?): Int? {
        val explicit = width
        val extras = margin.horizontal + padding.horizontal + border.horizontal
        val constrainedByParent = availableOuterWidth?.let { (it - extras).coerceAtLeast(0) }
        return when {
            explicit != null && constrainedByParent != null -> minOf(explicit, constrainedByParent)
            explicit != null -> explicit
            else -> constrainedByParent
        }
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (styleDisabled && DomainPortalServices.isSelectOpenFor(ownerToken)) {
            DomainPortalServices.closeSelect(ownerToken)
        }
        syncPopup()
        val isFocused = FocusManager.isFocused(this) && !styleDisabled
        val textValue = selectedLabelOrPlaceholder()
        val drawColor =
            when {
                styleDisabled -> disabledTextColor
                selectedOptionId() == null -> placeholderColor
                else -> textColor
            }
        val arrowWidth = if (arrowGlyph.isEmpty()) 0 else measureText(ctx, arrowGlyph)
        val lineHeight = resolveFontSize(ctx)
        out += RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, backgroundColor)
        addBackgroundImageCommand(out)
        addBorderCommands(out)

        val innerX = contentX()
        val innerY = contentY()
        val innerWidth = contentWidth()
        val innerHeight = contentHeight()
        val textY = innerY + (innerHeight - lineHeight) / 2
        val arrowX = innerX + innerWidth - arrowWidth
        val textClipWidth = (innerWidth - arrowWidth - arrowSpacing).coerceAtLeast(1)

        out += RenderCommand.PushClip(innerX, innerY, textClipWidth, innerHeight.coerceAtLeast(1))
        if (textValue.isNotEmpty()) {
            out +=
                drawTextCommand(
                    ctx,
                    text = textValue,
                    x = innerX,
                    y = textY,
                    color = drawColor,
                )
        }
        out += RenderCommand.PopClip

        if (arrowGlyph.isNotEmpty()) {
            val arrowColor = if (styleDisabled) disabledTextColor else textColor
            out +=
                drawTextCommand(
                    ctx,
                    text = arrowGlyph,
                    x = arrowX,
                    y = textY,
                    color = arrowColor,
                )
        }
    }

    override fun volatileRenderCommandsSignature(nowMs: Long): Long {
        var hash = 1L
        hash = 31L * hash + selectedLabelOrPlaceholder().hashCode()
        hash = 31L * hash + (selectedOptionId()?.hashCode() ?: 0)
        hash = 31L * hash + if (DomainPortalServices.isSelectOpenFor(ownerToken)) 1L else 0L
        return hash
    }

    internal fun syncFrom(template: SelectNode) {
        model = template.model
        controlled = template.controlled
        controlledValue = template.controlledValue
        defaultValue = template.defaultValue
        closeOnSelect = template.closeOnSelect
        ownerScope = template.ownerScope
        textColor = template.textColor
        placeholderColor = template.placeholderColor
        backgroundColor = template.backgroundColor
        disabledTextColor = template.disabledTextColor
        minContentWidth = template.minContentWidth
        arrowGlyph = template.arrowGlyph
        arrowSpacing = template.arrowSpacing
        if (!controlled && template.uncontrolledValue != null) {
            uncontrolledValue = template.uncontrolledValue
        }
        reconcileSelection()
    }

    override fun defaultBackgroundColor(): Int = backgroundColor

    override fun applyBackgroundColor(value: Int?) {
        if (value != null) {
            backgroundColor = value
        }
    }

    override fun defaultForegroundColor(): Int = textColor

    override fun applyForegroundColor(value: Int) {
        textColor = value
    }

    private fun openPopup() {
        if (!hasEnabledOption()) return
        DomainPortalServices.openSelect(openRequest())
        setOpenState(true)
    }

    private fun syncPopup() {
        val open = DomainPortalServices.isSelectOpenFor(ownerToken)
        setOpenState(open)
        if (open) {
            DomainPortalServices.selectEngineFor(ownerScope).sync(openRequest())
        }
    }

    private fun openRequest(): SelectOpenRequest {
        val geometry = UsedInteractionGeometryResolver.resolveNodeGeometry(this)
        val anchorRect = geometry.visibleBorderRect ?: geometry.usedBorderRect
        return SelectOpenRequest(
            owner = ownerToken,
            modelToken = model.token,
            entries = model.entries,
            selectedId = selectedOptionId(),
            anchorRect = anchorRect,
            closeOnSelect = closeOnSelect,
            onSelect = { selected -> applySelection(selected) },
            onClose = { setOpenState(false) },
            fontId = fontId,
            fontSize = fontSize,
            ownerScope = ownerScope,
        )
    }

    private fun applySelection(optionId: String) {
        val previous = selectedOptionId()
        if (!controlled) {
            uncontrolledValue = optionId
        }
        if (previous == optionId) return
        postInput(this, optionId, optionId)
        postChange(this, optionId, optionId)
        markRenderCommandsDirty()
    }

    private fun selectedOptionId(): String? {
        val candidate = if (controlled) controlledValue else uncontrolledValue
        if (candidate == null) return null
        return if (optionExists(candidate)) candidate else null
    }

    private fun selectedLabelOrPlaceholder(): String {
        val selectedId = selectedOptionId()
        if (selectedId != null) {
            val option = findOption(selectedId)
            if (option != null) {
                return option.labelProvider.invoke()
            }
        }
        return model.placeholderProvider
            ?.invoke()
            .orEmpty()
    }

    private fun reconcileSelection() {
        val current = if (controlled) controlledValue else uncontrolledValue
        if (!current.isNullOrEmpty() && optionExists(current)) {
            return
        }
        if (controlled) {
            return
        }
        uncontrolledValue =
            when {
                !defaultValue.isNullOrEmpty() && optionExists(defaultValue!!) -> defaultValue
                else -> firstEnabledOptionId()
            }
    }

    private fun hasEnabledOption(): Boolean = firstEnabledOptionId() != null

    private fun firstEnabledOptionId(): String? = firstEnabledOptionId(model.entries)

    private fun firstEnabledOptionId(entries: List<SelectEntry>): String? {
        entries.forEach { entry ->
            when (entry) {
                is SelectEntry.Option -> if (entry.enabledProvider.invoke()) return entry.id
                is SelectEntry.Group -> {
                    val nested = firstEnabledOptionId(entry.entries)
                    if (nested != null) return nested
                }

                is SelectEntry.Separator -> Unit
            }
        }
        return null
    }

    private fun optionExists(optionId: String): Boolean = findOption(optionId) != null

    private fun findOption(optionId: String): SelectEntry.Option? = findOption(model.entries, optionId)

    private fun findOption(entries: List<SelectEntry>, optionId: String): SelectEntry.Option? {
        entries.forEach { entry ->
            when (entry) {
                is SelectEntry.Option -> if (entry.id == optionId) return entry
                is SelectEntry.Group -> {
                    val nested = findOption(entry.entries, optionId)
                    if (nested != null) return nested
                }

                is SelectEntry.Separator -> Unit
            }
        }
        return null
    }
}
