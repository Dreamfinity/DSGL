package org.dreamfinity.dsgl.mc1710.demo

import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import org.dreamfinity.dsgl.core.*
import org.dreamfinity.dsgl.core.components.modal.ModalSpec
import org.dreamfinity.dsgl.core.components.modal.modalHost
import org.dreamfinity.dsgl.core.dnd.*
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.InputOption
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.ref.ElementHandle
import org.dreamfinity.dsgl.core.ref.RefTarget
import org.dreamfinity.dsgl.core.ref.useRef
import org.dreamfinity.dsgl.core.dom.debug.LayoutDebug
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.mc1710.McItemStackRef
import org.dreamfinity.dsgl.mc1710.demo.sections.*
import org.dreamfinity.dsgl.mc1710.demo.support.*
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToLong

class ShowcaseWindow : DsglWindow() {
    data class DndDemoItem(
        val id: String,
        val label: String,
        val stack: McItemStackRef
    )

    internal enum class DndLaneIndicator {
        NONE,
        BEFORE,
        AFTER
    }

    private var viewportWidth: Int = 320
    private var viewportHeight: Int = 240

    internal var selectedSection by state(DemoSection.OVERVIEW)
    internal var checklistPage by state(0)
    internal val checklistPageSize: Int = 8

    internal val maxEventLogs: Int = 24
    internal val visibleEventLines: Int = 7
    internal var eventLogs by state(emptyList<EventLogEntry>())
    private var logSequence: Int = 0

    internal var renderPasses: Int = 0
    internal var lastManualReason: String = "none"
    internal var manualInvalidateCount: Int = 0
    internal var autoRebuildCounter by state(0)
    internal var overlayClicks by state(0)

    internal var styleUseMargin by state(true)
    internal var styleUsePadding by state(true)
    internal var styleUseBorder by state(true)
    internal var styleLargeGap by state(false)
    internal var styleFixedSize by state(false)
    internal var layoutDebugStrict by state(LayoutDebug.strictBounds)
    internal var layoutDebugDraw by state(LayoutDebug.drawBounds)
    internal var layoutDebugWrapWidth by state(148L)
    internal var displayBlockLargeGap by state(false)
    internal var displayInlineWidth by state(132L)
    internal var displayShowHidden by state(true)
    internal var displayFlexJustifyIndex by state(0)
    internal var displayFlexAlignIndex by state(0)
    internal var displayGridColumns by state(3L)
    internal var displayGridLargeGap by state(false)
    internal var displayNoneClicks by state(0)
    internal var textWrapNoWrap by state(false)
    internal var textWrapWidth by state(176L)
    internal var modalBackgroundCounter by state(0)
    internal var modalPromptValue by state("hello")
    internal var demoModals by state(emptyList<ModalSpec>())
    internal var stackOverlayEnabled by state(true)
    internal var layoutOverlayX by state(8)
    internal var layoutOverlayY by state(92)
    internal var layoutOverlayDragging: Boolean = false
    private var layoutOverlayDragAnchorX: Int = 0
    private var layoutOverlayDragAnchorY: Int = 0
    private var layoutOverlayDragMoved: Boolean = false
    internal var stylesheetReloadCount by state(0)
    internal var stylesheetDemoTextValue by state("")
    internal var stylesheetDemoClickCount by state(0)
    internal var stylesheetEditorValue by state("")
    internal var stylesheetEditorStatus by state("not loaded")

    internal var mouseEnterCount by state(0)
    internal var mouseLeaveCount by state(0)
    internal var mouseOverCount by state(0)
    internal var mouseMoveCount by state(0)
    internal var mouseDownCount by state(0)
    internal var mouseUpCount by state(0)
    internal var mouseClickCount by state(0)
    internal var mouseDragCount by state(0)
    internal var mouseWheelCount by state(0)
    internal var keyDownCount by state(0)
    internal var keyUpCount by state(0)
    internal var keyPressedCount by state(0)
    internal var keyReleasedCount by state(0)
    internal var enterActionCount by state(0)
    internal var cancellationEnabled by state(true)
    internal var cancellationParentHits by state(0)
    internal var cancellationChildHits by state(0)
    private var mouseOverSamples: Int = 0
    private var mouseMoveSamples: Int = 0
    private var interactionZoneInside: Boolean = false

    internal var focusStableValue by state("")
    internal var focusUnstableValue by state("")
    internal var focusStableEnterRebuilds by state(0)
    internal var focusKeyVersion by state(0)

    internal var itemRotY by state(160.0)
    internal var itemRotX by state(-11.0)
    internal var mediaReady by state(false)

    internal val resourceImageSource: String = "minecraft:textures/gui/options_background.png"
    internal val fileImageSource: String = "file://demo/local_showcase.png"
    internal val httpImageSource: String = "https://demo.local/assets/showcase_http.png"
    internal val flatItemRef = McItemStackRef(ItemStack(Items.diamond_sword, 1, 0))
    internal val blockItemRef = McItemStackRef(ItemStack(Item.getItemFromBlock(Blocks.stone), 1, 0))
    internal val checkboxOptions = listOf(
        InputOption("alpha", "Alpha"),
        InputOption("beta", "Beta"),
        InputOption("gamma", "Gamma")
    )
    internal val radioOptions = listOf(
        InputOption("north", "North"),
        InputOption("center", "Center"),
        InputOption("south", "South")
    )
    internal var inputEventTextValue by state("")
    internal var inputEventTextareaValue by state("Multiline event sample")
    internal var inputEventCheckboxValue by state(setOf("alpha"))
    internal var inputEventRadioValue by state<String?>("center")
    internal var inputEventRangeValue by state(35L)
    internal var inputEventLogEntries by state(emptyList<String>())
    private val inputEventLogLimit = 8
    private val inputEventTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    internal var sharedRangeValue by state(35L)
    internal var clippingScrollDemoText by state(buildClippingScrollDemoText())
    internal var textEditingSingleValue by state("Edit this line")
    internal var textEditingPasswordValue by state("secret42")
    internal var textEditingAreaValue by state(
        "Line 1: drag-select me\nLine 2: use Shift+Arrows\nLine 3: Ctrl/Cmd+C/V/X"
    )
    internal var textEditingSawSelectionDrag by state(false)
    internal var textEditingSawShiftSelection by state(false)
    internal var textEditingSawClipboardShortcut by state(false)
    internal var textEditingSawFocus by state(false)
    internal var refsInputValue by state("Ref demo input")
    internal var refsRebuildCount by state(0)
    internal var refsCallbackMounted by state(true)
    internal var refsCallbackAttachCount by state(0)
    internal var refsCallbackDetachCount by state(0)
    internal var refsCallbackLast by state("none")
    internal var dndItems by state(
        defaultDndItems()
    )
    internal var dndHoverZone by state("none")
    internal var dndLastAction by state("none")
    internal var dndTransferTypes by state("-")
    internal var dndDropEffect by state("none")
    internal var dndActiveItem by state("none")
    internal var dndDragTickCount by state(0)
    internal var dndGhostEnabled by state(true)
    internal var dndHideSourceWhileDragging by state(false)
    internal var dndSmoothFactor by state(26.0)
    internal var dndReorderHoverTargetId by state<String?>(null)
    internal var dndReorderHoverInsertAfter by state(false)
    internal var dndReorderHoverLaneAppend by state(false)
    internal var dndDebugOverId by state("none")
    internal var dndDebugOverContainerId by state("none")
    internal var dndDebugCandidatesCount by state(0)
    internal var dndDebugInsertPosition by state("none")
    internal var dndDebugExcludesActiveCard by state(true)
    internal var dndBoxes by state(
        linkedMapOf(
            "box-a" to emptyList<DndDemoItem>(),
            "box-b" to emptyList<DndDemoItem>(),
            "box-c" to emptyList<DndDemoItem>()
        )
    )

    internal val implementedCapabilities: Set<CapabilityId>
        get() = CapabilityChecklistCatalog.implementedByAllSections()
    internal val openedAtForDemo
        get() = openedAt
    internal val timeZoneForDemo
        get() = timeZoneId

    override val rebuildOnResize: Boolean
        get() = true

    internal val refsCallbackRef: RefTarget<ElementHandle> = RefTarget { handle ->
        if (handle == null) {
            refsCallbackDetachCount += 1
            refsCallbackLast = "detach"
            appendInfo("Refs callback detached")
            return@RefTarget
        }
        refsCallbackAttachCount += 1
        refsCallbackLast = "attach key=${handle.key}"
        appendInfo("Refs callback attached key=${handle.key}")
    }

    override fun onOpen() {
        prepareDemoMedia()
        prepareDemoStylesheet()
        loadStylesheetEditorFromFile("window open")
        DndSystem.setSmoothingFactor(dndSmoothFactor)
        LayoutDebug.strictBounds = layoutDebugStrict
        LayoutDebug.drawBounds = layoutDebugDraw
        appendInfo("Showcase opened")
    }

    override fun onResize(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    override fun render(): DomTree {
        renderPasses += 1
        useDragDropMonitor(
            DragDropMonitorCallbacks(
                onDragMove = { active, over ->
                    dndActiveItem = active.id ?: active.sourceKey?.toString() ?: "none"
                    dndDebugOverContainerId = if (over == null) "none" else "target"
                    dndDebugOverId = over?.toString() ?: "none"
                },
                onDragOver = { active, over ->
                    dndDropEffect = active.dropEffect.name.lowercase()
                    dndDebugOverId = over?.toString() ?: "none"
                },
                onDragEnd = { _, _, effect ->
                    dndDropEffect = effect.name.lowercase()
                    dndDebugOverId = "none"
                    dndDebugOverContainerId = "none"
                },
                onDragCancel = {
                    dndDebugOverId = "none"
                    dndDebugOverContainerId = "none"
                }
            )
        )
        val refsInputHandle = useRef<ElementHandle>()
        val refsPanelHandle = useRef<ElementHandle>()

        val navWidth = 106
        val sidebarWidth = 158
        val bodyHeight = (viewportHeight - 34).coerceAtLeast(170)
        val contentWidth = (viewportWidth - navWidth - sidebarWidth - 18).coerceAtLeast(160)
        val inspectorHeight = (bodyHeight * 56) / 100
        val checklistHeight = (bodyHeight - inspectorHeight - 4).coerceAtLeast(72)

        return ui {
            modalHost(modals = demoModals, key = "showcase.modalHost") {
                div(
                    ComponentProps(
                        key = "showcase.root",
                        padding = 4,
                        gap = 4,
                        backgroundColor = DEMO_BG
                    ).asFlexColumn()
                ) {
                    text(TextProps("DSGL Showcase Window").apply { color = DsglColors.WHITE })
                    text(
                        TextProps {
                            "renderPasses=$renderPasses section=${selectedSection.title} viewport=${viewportWidth}x$viewportHeight"
                        }.apply { color = DEMO_MUTED }
                    )

                    div(ComponentProps(key = "showcase.body", gap = 4).asFlexRow()) {
                        div(
                            panelProps(
                                key = "showcase.nav",
                                width = navWidth,
                                height = bodyHeight
                            ).asFlexColumn()
                        ) {
                            text(TextProps("Sections").apply { color = DsglColors.WHITE })
                            DemoSection.entries.forEach { section ->
                                button(
                                    navButtonProps(
                                        key = "nav.${section.name.lowercase()}",
                                        title = section.title,
                                        selected = selectedSection == section
                                    ) {
                                        selectSection(section)
                                    }
                                )
                            }
                        }

                        div(
                            panelProps(
                                key = "showcase.content",
                                width = contentWidth,
                                height = bodyHeight
                            )
                        ) {
                            text(TextProps(selectedSection.title).apply { color = DsglColors.WHITE })
                            text(TextProps(selectedSection.subtitle).apply { color = DEMO_MUTED })
                            when (selectedSection) {
                                DemoSection.OVERVIEW -> renderOverviewSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.LAYOUT_STYLE -> renderLayoutStyleSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.LAYOUT_DEBUG -> renderLayoutDebugSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.DISPLAY -> renderDisplaySection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.TEXT_WRAP -> renderTextWrapSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.MODALS -> renderModalsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.STYLESHEETS -> renderStylesheetsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.INPUTS -> renderInputsGallerySection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.INPUT_EVENTS -> renderInputEventsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.TEXT_EDITING -> renderTextEditingSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.REFS -> renderRefsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30,
                                    refsInputHandle,
                                    refsPanelHandle
                                )

                                DemoSection.DRAG_DROP -> renderDragDropSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.INTERACTIONS -> renderInteractionsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.FOCUS_REBUILD -> renderFocusRebuildSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.MC_FEATURES -> renderMcFeaturesSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )
                            }
                        }

                        div(
                            ComponentProps(
                                key = "showcase.side",
                                width = sidebarWidth,
                                height = bodyHeight,
                                gap = 4
                            ).asFlexColumn()
                        ) {
                            renderEventInspectorPanel(this@ShowcaseWindow, sidebarWidth, inspectorHeight)
                            renderChecklistPanel(this@ShowcaseWindow, sidebarWidth, checklistHeight)
                        }
                    }
                }
            }
        }
    }

    internal fun clearEventLogs() {
        eventLogs = emptyList()
    }

    internal fun pushModal(spec: ModalSpec) {
        if (demoModals.any { it.key == spec.key }) return
        demoModals = demoModals + spec
        appendInfo("Modal pushed: ${spec.key}")
    }

    internal fun removeModal(key: String) {
        val before = demoModals.size
        demoModals = demoModals.filterNot { it.key == key }
        if (demoModals.size != before) {
            appendInfo("Modal removed: $key")
        }
    }

    internal fun popTopModal() {
        if (demoModals.isEmpty()) return
        val removed = demoModals.last()
        demoModals = demoModals.dropLast(1)
        appendInfo("Modal popped: ${removed.key}")
    }

    internal fun moveChecklistPage(delta: Int) {
        val required = CapabilityChecklistCatalog.required.size
        val pageCount = ((required + checklistPageSize - 1) / checklistPageSize).coerceAtLeast(1)
        checklistPage = (checklistPage + delta).coerceIn(0, pageCount - 1)
    }

    internal fun requestManualInvalidate(reason: String) {
        manualInvalidateCount += 1
        lastManualReason = reason
        invalidate()
    }

    internal fun bumpAutoRebuildCounter() {
        autoRebuildCounter += 1
    }

    internal fun bumpFocusVersion() {
        focusKeyVersion += 1
    }

    internal fun applyTextMutation(
        current: String,
        event: KeyboardKeyDownEvent,
        allowedChars: String? = null,
        maxLength: Int? = null
    ): String {
        if (event.keyCode == KeyCodes.BACKSPACE) {
            if (current.isEmpty()) return current
            return current.dropLast(1)
        }

        var ch = event.keyChar
        if (ch < ' ' || ch.code == 127) return current
        ch = KeyInput.applyShift(ch, KeyModifiers.shiftDown)
        if (allowedChars != null && !allowedChars.contains(ch)) return current
        val next = current + ch
        if (maxLength != null && next.length > maxLength) return current
        return next
    }

    internal fun logHook(hookName: String, event: Event, note: String? = null, color: Int = DsglColors.TEXT) {
        val line = formatEventLine(hookName, event, note)
        appendLog(line, color)
    }

    internal fun appendInfo(message: String) {
        appendLog(message, DEMO_OK)
    }

    internal fun sampledMouseOverEvent(): Boolean {
        mouseOverSamples += 1
        return mouseOverSamples % 5 == 0
    }

    internal fun sampledMouseMoveEvent(): Boolean {
        mouseMoveSamples += 1
        return mouseMoveSamples % 6 == 0
    }

    internal fun markInteractionZoneEntered(): Boolean {
        if (interactionZoneInside) return false
        interactionZoneInside = true
        return true
    }

    internal fun markInteractionZoneLeft(): Boolean {
        if (!interactionZoneInside) return false
        interactionZoneInside = false
        return true
    }

    internal fun adjustItemRotation(deltaY: Double = 0.0, deltaX: Double = 0.0) {
        itemRotY = normalizeAngle(itemRotY + deltaY)
        itemRotX = (itemRotX + deltaX).coerceIn(-89.0, 89.0)
    }

    internal fun setItemRotationFromSlider(isY: Boolean, deltaPixels: Int) {
        if (isY) {
            adjustItemRotation(deltaY = deltaPixels.toDouble())
        } else {
            adjustItemRotation(deltaX = deltaPixels.toDouble() / 2.0)
        }
    }

    internal fun reloadStylesheetsProgrammatically(source: String) {
        StyleEngine.forceReloadStylesheets()
        stylesheetReloadCount += 1
        requestManualInvalidate("stylesheets reload")
        appendInfo("Stylesheets reloaded by $source (#$stylesheetReloadCount)")
        stylesheetEditorStatus = "reloaded #$stylesheetReloadCount"
    }

    internal fun loadStylesheetEditorFromFile(source: String) {
        try {
            val file = demoStylesheetFile()
            if (!file.exists()) {
                prepareDemoStylesheet()
            }
            stylesheetEditorValue = file.readText()
            stylesheetEditorStatus = "loaded by $source"
            appendInfo("Stylesheet loaded by $source")
        } catch (ex: Exception) {
            stylesheetEditorStatus = "load failed: ${ex.javaClass.simpleName}"
            appendLog("Stylesheet load failed: ${ex.javaClass.simpleName}", 0xFFFF9A66.toInt())
        }
    }

    internal fun saveStylesheetEditorToFile(source: String) {
        try {
            val file = demoStylesheetFile()
            file.parentFile?.mkdirs()
            file.writeText(stylesheetEditorValue)
            stylesheetEditorStatus = "saved by $source"
            appendInfo("Stylesheet saved by $source")
        } catch (ex: Exception) {
            stylesheetEditorStatus = "save failed: ${ex.javaClass.simpleName}"
            appendLog("Stylesheet save failed: ${ex.javaClass.simpleName}", 0xFFFF9A66.toInt())
        }
    }

    internal fun itemRotYLong(): Long = itemRotY.roundToLong().coerceIn(0L, 360L)

    internal fun itemRotXLong(): Long = itemRotX.roundToLong().coerceIn(-89L, 89L)

    internal fun beginLayoutOverlayDrag(event: MouseDownEvent) {
        if (event.mouseButton != MouseButton.LEFT) return
        val overlayNode = findNodeInPath(event.target, "layout.stack.overlay") ?: return
        layoutOverlayDragging = true
        layoutOverlayDragAnchorX =
            (event.mouseX - overlayNode.bounds.x).coerceIn(0, overlayNode.bounds.width.coerceAtLeast(1))
        layoutOverlayDragAnchorY =
            (event.mouseY - overlayNode.bounds.y).coerceIn(0, overlayNode.bounds.height.coerceAtLeast(1))
        layoutOverlayDragMoved = false
    }

    internal fun updateLayoutOverlayDrag(event: MouseDragEvent, maxX: Int, maxY: Int) {
        if (!layoutOverlayDragging) return
        val currentX = event.lastMouseX + event.dx
        val currentY = event.lastMouseY + event.dy
        val stackNode = findNodeInPath(event.target, "section.layoutStyle.stack") ?: return
        val nextX = (currentX - stackNode.bounds.x - layoutOverlayDragAnchorX).coerceIn(0, maxX)
        val nextY = (currentY - stackNode.bounds.y - layoutOverlayDragAnchorY).coerceIn(0, maxY)
        if (nextX != layoutOverlayX) {
            if (abs(nextX - layoutOverlayX) > 0) {
                layoutOverlayDragMoved = true
            }
            layoutOverlayX = nextX
        }
        if (nextY != layoutOverlayY) {
            if (abs(nextY - layoutOverlayY) > 0) {
                layoutOverlayDragMoved = true
            }
            layoutOverlayY = nextY
        }
    }

    internal fun finishLayoutOverlayDrag(event: MouseUpEvent) {
        if (!layoutOverlayDragging) return
        if (event.mouseButton == MouseButton.LEFT && !layoutOverlayDragMoved) {
            overlayClicks += 1
            logHook("overlay.onMouseClick", event, "overlayClicks=$overlayClicks")
        }
        layoutOverlayDragging = false
        layoutOverlayDragMoved = false
    }

    internal fun recordInputEvent(control: String, phase: String, value: String, event: Event) {
        val time = LocalTime.now().format(inputEventTimeFormatter)
        val line = "$time $control.$phase value=$value"
        inputEventLogEntries = (listOf(line) + inputEventLogEntries).take(inputEventLogLimit)
        logHook("inputEvents.$control.$phase", event, "value=$value")
    }

    internal fun clearInputEventLog() {
        inputEventLogEntries = emptyList()
    }

    internal fun parseCheckboxSelection(parsedValue: Any?): Set<String> {
        val parsedSet = parsedValue as? Set<*>
        if (parsedSet != null) {
            return parsedSet.mapNotNull { it as? String }.toSet()
        }
        val parsedString = parsedValue as? String
        if (!parsedString.isNullOrBlank()) {
            return parsedString
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
        return emptySet()
    }

    internal fun checkboxValueString(): String {
        return inputEventCheckboxValue.toList().sorted().joinToString(",")
    }

    internal fun resetDndItems(source: String) {
        dndItems = defaultDndItems()
        dndLastAction = "reset list"
        dndHoverZone = "none"
        dndActiveItem = "none"
        dndDropEffect = "none"
        dndTransferTypes = "-"
        dndDragTickCount = 0
        clearLaneReorderHover()
        dndDebugOverId = "none"
        dndDebugOverContainerId = "none"
        dndDebugCandidatesCount = 0
        dndDebugInsertPosition = "none"
        dndDebugExcludesActiveCard = true
        dndBoxes = linkedMapOf(
            "box-a" to emptyList(),
            "box-b" to emptyList(),
            "box-c" to emptyList()
        )
        appendInfo("DnD demo list reset by $source")
    }

    internal fun updateDndSmoothing(delta: Double) {
        dndSmoothFactor = (dndSmoothFactor + delta).coerceIn(0.0, 96.0)
        DndSystem.setSmoothingFactor(dndSmoothFactor)
        appendInfo("DnD smoothing k=${"%.1f".format(dndSmoothFactor)}")
    }

    internal fun handleDndStart(item: DndDemoItem, event: DragStartEvent) {
        val sourceBounds = event.target?.bounds
        val offsetX = if (sourceBounds != null) {
            (event.mouseX - sourceBounds.x).coerceIn(0, sourceBounds.width.coerceAtLeast(1))
        } else {
            0
        }
        val offsetY = if (sourceBounds != null) {
            (event.mouseY - sourceBounds.y).coerceIn(0, sourceBounds.height.coerceAtLeast(1))
        } else {
            0
        }
        event.dataTransfer.setData("text/plain", item.label)
        event.dataTransfer.setData("application/x-dsgl-item-id", item.id)
        event.dataTransfer.effectAllowed = EffectAllowed.COPY_MOVE
        event.dataTransfer.dropEffect = DropEffect.MOVE
        if (!dndGhostEnabled) {
            event.dataTransfer.hideGhost()
        }
        val sourceKey = event.target?.key?.toString()
        if (!sourceKey.isNullOrBlank()) {
            event.dataTransfer.setDragImage(sourceKey, offsetX, offsetY)
        }
        dndActiveItem = item.label
        dndTransferTypes = event.dataTransfer.types.sorted().joinToString(",").ifBlank { "-" }
        dndDropEffect = event.dataTransfer.dropEffect.name.lowercase()
        dndLastAction = "dragstart ${item.label}"
        clearLaneReorderHover()
        dndDebugOverId = "none"
        dndDebugOverContainerId = "none"
        dndDebugCandidatesCount = 0
        dndDebugInsertPosition = "none"
        dndDebugExcludesActiveCard = true
        val mode = event.target?.dragPreviewMode?.name?.lowercase() ?: "unknown"
        logHook("dnd.onDragStart", event, "item=${item.id} mode=$mode")
    }

    internal fun handleDndDrag(event: DragEvent) {
        dndDragTickCount += 1
        dndTransferTypes = event.dataTransfer.types.sorted().joinToString(",").ifBlank { "-" }
        dndDropEffect = event.dataTransfer.dropEffect.name.lowercase()
        if (dndDragTickCount % 5 == 0) {
            logHook("dnd.onDrag", event, "tick=$dndDragTickCount")
        }
    }

    internal fun handleDndLaneOver(event: DragOverEvent) {
        val laneNode = event.target
        val draggedId = event.dataTransfer.getData("application/x-dsgl-item-id")
        val proposed = resolveLaneIntentFromMouse(laneNode, event.mouseY, draggedId)
        val intent = stabilizeLaneIntent(laneNode, event.mouseY, draggedId, proposed)
        dndReorderHoverLaneAppend = intent.append
        dndReorderHoverTargetId = intent.targetId
        dndReorderHoverInsertAfter = intent.insertAfter
        dndDebugOverContainerId = "lane"
        dndDebugOverId = intent.targetId ?: "append"
        dndDebugCandidatesCount = laneCandidateCount(laneNode, draggedId)
        dndDebugInsertPosition = when {
            intent.append -> "append"
            intent.insertAfter -> "after"
            else -> "before"
        }
        dndDebugExcludesActiveCard = true
        event.acceptDrop(DropEffect.MOVE)
        dndDropEffect = event.dataTransfer.dropEffect.name.lowercase()
    }

    internal fun handleDndLaneDrop(event: DropEvent) {
        val draggedId = event.dataTransfer.getData("application/x-dsgl-item-id") ?: return
        val laneNode = event.target
        val proposed = resolveLaneIntentFromMouse(laneNode, event.mouseY, draggedId)
        val intent = stabilizeLaneIntent(laneNode, event.mouseY, draggedId, proposed)
        val targetId = intent.targetId
        val insertAfter = if (intent.append) null else intent.insertAfter
        val moved = commitLaneReorderDrop(
            draggedId = draggedId,
            targetId = targetId,
            insertAfter = insertAfter,
            dropOnLane = intent.append
        )
        if (moved) {
            appendInfo(
                "Lane drop: drag=$draggedId target=${targetId ?: "lane"} pos=${
                    if (intent.append) "append" else if (insertAfter == true) "after" else "before"
                }"
            )
        }
        clearLaneReorderHover()
        dndDropEffect = event.dataTransfer.dropEffect.name.lowercase()
        dndDebugOverId = "none"
        dndDebugOverContainerId = "none"
        dndDebugInsertPosition = "none"
        logHook(
            "dnd.reorder.lane.onDrop",
            event,
            "dragged=$draggedId target=${targetId ?: "lane"} append=${intent.append}"
        )
    }

    internal fun handleDndCardReorderOver(targetCardId: String, insertAfter: Boolean, event: DragOverEvent) {
        val draggedId = event.dataTransfer.getData("application/x-dsgl-item-id")
        if (draggedId != null && draggedId == targetCardId) {
            return
        }
        val laneNode = event.target?.parent
        dndReorderHoverTargetId = targetCardId
        dndReorderHoverInsertAfter = insertAfter
        dndReorderHoverLaneAppend = false
        dndDebugOverContainerId = "lane"
        dndDebugOverId = targetCardId
        dndDebugCandidatesCount = laneCandidateCount(laneNode, draggedId)
        dndDebugInsertPosition = if (insertAfter) "after" else "before"
        dndDebugExcludesActiveCard = true
        event.acceptDrop(DropEffect.MOVE)
        event.cancelled = true
        dndDropEffect = event.dataTransfer.dropEffect.name.lowercase()
    }

    internal fun handleDndCardReorderDrop(targetCardId: String, insertAfter: Boolean, event: DropEvent) {
        val draggedId = event.dataTransfer.getData("application/x-dsgl-item-id") ?: return
        if (draggedId == targetCardId) {
            return
        }
        val moved = commitLaneReorderDrop(
            draggedId = draggedId,
            targetId = targetCardId,
            insertAfter = insertAfter,
            dropOnLane = false
        )
        if (moved) {
            appendInfo(
                "Card drop: drag=$draggedId target=$targetCardId pos=${if (insertAfter) "after" else "before"}"
            )
        }
        clearLaneReorderHover()
        dndDebugOverId = "none"
        dndDebugOverContainerId = "none"
        dndDebugInsertPosition = "none"
        event.cancelled = true
        dndDropEffect = event.dataTransfer.dropEffect.name.lowercase()
        logHook(
            "dnd.reorder.card.onDrop",
            event,
            "dragged=$draggedId target=$targetCardId pos=${if (insertAfter) "after" else "before"}"
        )
    }

    internal fun handleDndBoxOver(boxId: String, event: DragOverEvent) {
        clearLaneReorderHover()
        dndHoverZone = boxId
        dndDebugOverId = boxId
        dndDebugOverContainerId = "box:$boxId"
        dndDebugInsertPosition = "drop"
        dndDebugCandidatesCount = 1
        dndDebugExcludesActiveCard = true
        event.acceptDrop(DropEffect.MOVE)
        dndDropEffect = event.dataTransfer.dropEffect.name.lowercase()
    }

    internal fun handleDndBoxDrop(boxId: String, event: DropEvent) {
        val draggedId = event.dataTransfer.getData("application/x-dsgl-item-id")
        if (draggedId == null) return
        val moved = moveCardToBox(draggedId, boxId)
        if (moved) {
            dndHoverZone = boxId
            dndLastAction = "moved $draggedId to $boxId"
        }
        dndDropEffect = event.dataTransfer.dropEffect.name.lowercase()
        logHook("dnd.$boxId.onDrop", event, "dragged=$draggedId")
    }

    internal fun handleDndEnd(event: DragEndEvent) {
        dndHoverZone = "none"
        clearLaneReorderHover()
        dndDropEffect = event.finalDropEffect.name.lowercase()
        dndLastAction = "dragend drop=${event.didDrop} effect=${event.finalDropEffect.name.lowercase()}"
        dndActiveItem = "none"
        dndDebugOverId = "none"
        dndDebugOverContainerId = "none"
        dndDebugCandidatesCount = 0
        dndDebugInsertPosition = "none"
        logHook("dnd.onDragEnd", event, "drop=${event.didDrop}")
    }

    internal fun resolveLanePreviewOrder(sourceKey: Any?): List<DndDemoItem> {
        if (sourceKey != null) {
            // Lane preview keeps a single insertion gap and avoids rendering a duplicate card while dragging from boxes.
        }
        return dndItems
    }

    internal fun laneIndicatorForCard(cardId: String, sourceKey: Any?): DndLaneIndicator {
        if (dndReorderHoverLaneAppend) return DndLaneIndicator.NONE
        if (dndReorderHoverTargetId != cardId) return DndLaneIndicator.NONE
        val draggedId = extractCardIdFromKey(sourceKey) ?: return DndLaneIndicator.NONE
        val wouldChange = wouldLaneReorderChange(
            draggedId = draggedId,
            targetId = cardId,
            insertAfter = dndReorderHoverInsertAfter,
            dropOnLane = false
        )
        if (!wouldChange) return DndLaneIndicator.NONE
        return if (dndReorderHoverInsertAfter) DndLaneIndicator.AFTER else DndLaneIndicator.BEFORE
    }

    internal fun isLaneAppendHighlighted(): Boolean {
        return dndReorderHoverLaneAppend
    }

    internal fun shouldShowLaneAppendGap(sourceKey: Any?): Boolean {
        if (!dndReorderHoverLaneAppend) return false
        val draggedId = extractCardIdFromKey(sourceKey) ?: return true
        if (!wouldLaneReorderChange(draggedId, targetId = null, insertAfter = null, dropOnLane = true)) {
            return false
        }
        val index = dndItems.indexOfFirst { it.id == draggedId }
        if (index < 0) return true
        return index != dndItems.lastIndex
    }

    internal fun clearLaneReorderHoverState() {
        clearLaneReorderHover()
    }

    internal fun bucketCards(boxId: String): List<DndDemoItem> {
        return dndBoxes[boxId] ?: emptyList()
    }

    private fun extractCardIdFromKey(sourceKey: Any?): String? {
        val key = sourceKey as? String ?: return null
        val marker = ".card."
        val markerIndex = key.indexOf(marker)
        if (markerIndex < 0) return null
        val value = key.substring(markerIndex + marker.length)
        return value.takeIf { it.isNotBlank() }
    }

    private fun moveCardToBox(cardId: String, boxId: String): Boolean {
        val extracted = extractCard(cardId) ?: return false
        val lane = extracted.second.toMutableList()
        val boxes = extracted.third
        val card = extracted.first

        val target = boxes.getOrPut(boxId) { mutableListOf() }
        target.add(card)
        dndItems = lane
        dndBoxes = boxes.mapValuesTo(linkedMapOf()) { (_, value) -> value.toList() }
        return true
    }

    private fun extractCard(
        cardId: String
    ): Triple<DndDemoItem, List<DndDemoItem>, LinkedHashMap<String, MutableList<DndDemoItem>>>? {
        val lane = dndItems.toMutableList()
        val boxes = linkedMapOf<String, MutableList<DndDemoItem>>().apply {
            dndBoxes.forEach { (key, list) ->
                this[key] = list.toMutableList()
            }
        }

        val laneIndex = lane.indexOfFirst { it.id == cardId }
        if (laneIndex >= 0) {
            val card = lane.removeAt(laneIndex)
            return Triple(card, lane, boxes)
        }

        boxes.forEach { (_, list) ->
            val boxIndex = list.indexOfFirst { it.id == cardId }
            if (boxIndex >= 0) {
                val card = list.removeAt(boxIndex)
                return Triple(card, lane, boxes)
            }
        }
        return null
    }

    private fun commitLaneReorderDrop(
        draggedId: String,
        targetId: String?,
        insertAfter: Boolean?,
        dropOnLane: Boolean
    ): Boolean {
        val draggedCard = findCardById(draggedId) ?: return false
        val fromBox = dndBoxes.any { (_, cards) -> cards.any { it.id == draggedId } }
        val laneWithDragged = if (dndItems.any { it.id == draggedId }) {
            dndItems
        } else {
            dndItems + draggedCard
        }
        val fromIndex = laneWithDragged.indexOfFirst { it.id == draggedId }
        val reordered = reorder(
            list = laneWithDragged,
            draggedId = draggedId,
            targetId = targetId,
            insertAfter = insertAfter,
            dropOnLane = dropOnLane
        )
        val toIndex = reordered.indexOfFirst { it.id == draggedId }
        val laneChanged = !sameOrderById(laneWithDragged, reordered)
        if (!laneChanged && !fromBox) {
            dndLastAction = "reorder noop drag=$draggedId"
            return false
        }

        dndItems = reordered
        dndBoxes = dndBoxes.mapValuesTo(linkedMapOf()) { (_, cards) ->
            cards.filterNot { it.id == draggedId }
        }
        dndLastAction = buildString {
            append("reorder drag=")
            append(draggedId)
            append(" target=")
            append(targetId ?: "lane")
            append(" pos=")
            append(
                when {
                    dropOnLane -> "append"
                    insertAfter == true -> "after"
                    else -> "before"
                }
            )
            append(" from=")
            append(fromIndex)
            append(" to=")
            append(toIndex)
            append(" order=")
            append(reordered.joinToString(">") { it.id })
        }
        return true
    }

    private fun reorder(
        list: List<DndDemoItem>,
        draggedId: String,
        targetId: String?,
        insertAfter: Boolean?,
        dropOnLane: Boolean
    ): List<DndDemoItem> {
        val fromIndex = list.indexOfFirst { it.id == draggedId }
        if (fromIndex < 0) return list
        if (!dropOnLane && targetId != null && targetId == draggedId) return list

        val mutable = list.toMutableList()
        val dragged = mutable.removeAt(fromIndex)
        val targetIndex = when {
            dropOnLane || targetId == null -> mutable.size
            else -> {
                val targetPos = mutable.indexOfFirst { it.id == targetId }
                if (targetPos < 0) {
                    mutable.size
                } else if (insertAfter == true) {
                    targetPos + 1
                } else {
                    targetPos
                }
            }
        }.coerceIn(0, mutable.size)

        mutable.add(targetIndex, dragged)
        return if (sameOrderById(list, mutable)) list else mutable
    }

    private fun findCardById(cardId: String): DndDemoItem? {
        dndItems.firstOrNull { it.id == cardId }?.let { return it }
        dndBoxes.values.forEach { cards ->
            cards.firstOrNull { it.id == cardId }?.let { return it }
        }
        return null
    }

    private fun sameOrderById(left: List<DndDemoItem>, right: List<DndDemoItem>): Boolean {
        if (left.size != right.size) return false
        left.indices.forEach { index ->
            if (left[index].id != right[index].id) return false
        }
        return true
    }

    private fun wouldLaneReorderChange(
        draggedId: String,
        targetId: String?,
        insertAfter: Boolean?,
        dropOnLane: Boolean
    ): Boolean {
        val draggedCard = findCardById(draggedId) ?: return false
        val laneWithDragged = if (dndItems.any { it.id == draggedId }) dndItems else dndItems + draggedCard
        val reordered = reorder(
            list = laneWithDragged,
            draggedId = draggedId,
            targetId = targetId,
            insertAfter = insertAfter,
            dropOnLane = dropOnLane
        )
        return !sameOrderById(laneWithDragged, reordered)
    }

    private fun clearLaneReorderHover() {
        dndReorderHoverTargetId = null
        dndReorderHoverInsertAfter = false
        dndReorderHoverLaneAppend = false
    }

    private data class LaneHoverIntent(
        val targetId: String?,
        val insertAfter: Boolean,
        val append: Boolean
    )

    private fun resolveLaneIntentFromMouse(
        laneNode: DOMNode?,
        mouseY: Int,
        excludedCardId: String? = null
    ): LaneHoverIntent {
        if (laneNode == null) {
            return LaneHoverIntent(targetId = null, insertAfter = false, append = true)
        }
        val cards = laneCards(laneNode, excludedCardId)

        if (cards.isEmpty()) {
            return LaneHoverIntent(targetId = null, insertAfter = false, append = true)
        }

        val lastCard = cards.last().second
        val appendThresholdY = lastCard.bounds.y + lastCard.bounds.height + 2
        if (mouseY >= appendThresholdY) {
            return LaneHoverIntent(targetId = null, insertAfter = false, append = true)
        }

        val target = cards.firstOrNull { (_, node) ->
            mouseY < node.bounds.y + node.bounds.height
        } ?: cards.last()
        val targetId = target.first
        val targetNode = target.second
        val splitY = targetNode.bounds.y + (targetNode.bounds.height / 2)
        val insertAfter = mouseY >= splitY
        return LaneHoverIntent(targetId = targetId, insertAfter = insertAfter, append = false)
    }

    private fun laneCandidateCount(laneNode: DOMNode?, excludedCardId: String?): Int {
        return laneCards(laneNode, excludedCardId).size
    }

    private fun stabilizeLaneIntent(
        laneNode: DOMNode?,
        mouseY: Int,
        excludedCardId: String?,
        proposed: LaneHoverIntent
    ): LaneHoverIntent {
        if (laneNode == null) return proposed
        if (proposed.append) return proposed

        val cards = laneCards(laneNode, excludedCardId)
        val currentTargetId = dndReorderHoverTargetId
        if (currentTargetId != null && currentTargetId != proposed.targetId) {
            val previousNode = cards.firstOrNull { (id, _) -> id == currentTargetId }?.second
            if (previousNode != null) {
                val insidePrevious = mouseY >= previousNode.bounds.y &&
                        mouseY < (previousNode.bounds.y + previousNode.bounds.height)
                if (insidePrevious) {
                    return LaneHoverIntent(
                        targetId = currentTargetId,
                        insertAfter = dndReorderHoverInsertAfter,
                        append = false
                    )
                }
            }
        }

        if (currentTargetId != null &&
            currentTargetId == proposed.targetId &&
            !dndReorderHoverLaneAppend &&
            proposed.targetId != null &&
            proposed.insertAfter != dndReorderHoverInsertAfter
        ) {
            val currentNode = cards.firstOrNull { (id, _) -> id == currentTargetId }?.second
            if (currentNode != null) {
                val splitY = currentNode.bounds.y + (currentNode.bounds.height / 2)
                if (abs(mouseY - splitY) <= 3) {
                    return LaneHoverIntent(
                        targetId = currentTargetId,
                        insertAfter = dndReorderHoverInsertAfter,
                        append = false
                    )
                }
            }
        }

        return proposed
    }

    private fun laneCards(laneNode: DOMNode?, excludedCardId: String?): List<Pair<String, DOMNode>> {
        if (laneNode == null) return emptyList()
        return laneNode.children
            .mapNotNull { child ->
                val id = extractCardIdFromKey(child.key) ?: return@mapNotNull null
                if (excludedCardId != null && id == excludedCardId) {
                    return@mapNotNull null
                }
                id to child
            }
            .sortedBy { (_, node) -> node.bounds.y }
    }

    private fun selectSection(section: DemoSection) {
        if (selectedSection == section) return
        selectedSection = section
        interactionZoneInside = false
        if (section != DemoSection.DRAG_DROP) {
            dndHoverZone = "none"
        }
        appendInfo("Section: ${section.title}")
    }

    private fun appendLog(line: String, color: Int) {
        logSequence += 1
        val entry = EventLogEntry(logSequence, line, color)
        eventLogs = (listOf(entry) + eventLogs).take(maxEventLogs)
    }

    private fun normalizeAngle(value: Double): Double {
        var normalized = value % 360.0
        if (normalized < 0.0) normalized += 360.0
        return normalized
    }

    private fun findNodeInPath(start: DOMNode?, key: Any): DOMNode? {
        var current = start
        while (current != null) {
            if (current.key == key) return current
            current = current.parent
        }
        return null
    }

    private fun prepareDemoMedia() {
        try {
            val dataDir = Minecraft.getMinecraft().mcDataDir
            writeDemoImage(
                File(dataDir, "dsgl/demo/local_showcase.png"),
                0xFF3B71A5.toInt(),
                0xFFF7B25B.toInt()
            )
            writeDemoImage(
                File(dataDir, "dsgl/cache/downloads/demo.local/assets/showcase_http.png"),
                0xFF2D8757.toInt(),
                0xFFC8E66B.toInt()
            )
            mediaReady = true
            appendInfo("Prepared local file:// and cached http image assets")
        } catch (ex: Exception) {
            mediaReady = false
            appendLog("Media prep failed: ${ex.javaClass.simpleName}", 0xFFFF9A66.toInt())
        }
    }

    private fun prepareDemoStylesheet() {
        try {
            val stylesheetFile = demoStylesheetFile()
            stylesheetFile.parentFile?.mkdirs()
            var created = false
            if (!stylesheetFile.exists()) {
                stylesheetFile.writeText(
                    """
                    :root {
                      --primary: #3E6B9E;
                      --accent: #7CB6FF;
                      --danger: #A34343;
                      --fg: #E9F1FF;
                    }
                    
                    button {
                      border-width: 1;
                      border-color: #000000;
                      padding: 3 6;
                    }
                    
                    .style-card {
                      margin: 2 0 0 0;
                      background-color: #2A3440;
                      border-color: #5E6A77;
                      border-width: 1;
                      padding: 4;
                    }
                    
                    .accent {
                      background-color: #3F5A70;
                    }
                    
                    button.primary {
                      background-color: var(--primary);
                      foreground-color: var(--fg);
                    }
                    
                    #dangerAction {
                      background-color: var(--danger);
                      foreground-color: #FFFFFFFF;
                    }
                    
                    #hoverActiveTarget:hover {
                      background-color: #365F7D;
                    }
                    
                    #hoverActiveTarget:active {
                      background-color: #274356;
                    }
                    
                    #focusInput:focus {
                      border-color: var(--accent);
                      border-width: 2;
                    }
                    
                    #disabledTarget:disabled {
                      background-color: #444444;
                      foreground-color: #999999;
                    }
                    
                    .vars-demo {
                      background-color: #213348;
                      border-color: var(--accent);
                    }
                    """.trimIndent()
                )
                appendInfo("Created demo stylesheet: ${stylesheetFile.name}")
                created = true
            }
            if (created) {
                StyleEngine.forceReloadStylesheets()
            }
        } catch (ex: Exception) {
            appendLog("Stylesheet prep failed: ${ex.javaClass.simpleName}", 0xFFFF9A66.toInt())
        }
    }

    private fun demoStylesheetFile(): File {
        val dataDir = Minecraft.getMinecraft().mcDataDir
        return File(dataDir, "dsgl/styles/showcase_styles.dss")
    }

    private fun defaultDndItems(): List<DndDemoItem> {
        return listOf(
            DndDemoItem("diamond", "Diamond", McItemStackRef(ItemStack(Items.diamond))),
            DndDemoItem("carrot", "Carrot", McItemStackRef(ItemStack(Items.carrot))),
            DndDemoItem("apple", "Apple", McItemStackRef(ItemStack(Items.apple))),
            DndDemoItem("bread", "Bread", McItemStackRef(ItemStack(Items.bread)))
        )
    }

    private fun writeDemoImage(file: File, colorA: Int, colorB: Int) {
        file.parentFile?.mkdirs()
        val image = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val useA = ((x / 5) + (y / 5)) % 2 == 0
                image.setRGB(x, y, if (useA) colorA else colorB)
            }
        }
        ImageIO.write(image, "png", file)
    }

    private fun buildClippingScrollDemoText(): String {
        val out = StringBuilder()
        for (line in 1..100) {
            out.append("Line ")
            out.append(line)
            out.append(" :: clipping+scroll demo")
            if (line < 100) out.append('\n')
        }
        return out.toString()
    }
}
