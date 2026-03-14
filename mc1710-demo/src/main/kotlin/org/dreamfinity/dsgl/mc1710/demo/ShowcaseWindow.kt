package org.dreamfinity.dsgl.mc1710.demo

import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.animation.keyframes
import org.dreamfinity.dsgl.core.colorpicker.*
import org.dreamfinity.dsgl.core.components.modal.ModalSpec
import org.dreamfinity.dsgl.core.components.modal.modalHost
import org.dreamfinity.dsgl.core.dnd.*
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.debug.LayoutDebug
import org.dreamfinity.dsgl.core.dom.elements.InputOption
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.ref.ElementHandle
import org.dreamfinity.dsgl.core.ref.RefTarget
import org.dreamfinity.dsgl.core.ref.useRef
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.ui
import org.dreamfinity.dsgl.mc1710.McItemStackRef
import org.dreamfinity.dsgl.mc1710.demo.sections.*
import org.dreamfinity.dsgl.mc1710.demo.support.*
import org.dreamfinity.dsgl.mc1710.text.MsdfRuntimeDebugSettings
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToLong

class ShowcaseWindow : DsglWindow() {
    data class DndDemoItem(
        val id: String,
        val label: String,
        val stack: McItemStackRef
    )

    internal data class ContextMenuDemoFile(
        val id: String,
        val parentId: String?,
        val name: String,
        val sizeKb: Int,
        val isDirectory: Boolean,
        val locked: Boolean,
        val updatedAtOrder: Long
    )

    internal data class ContextMenuBreadcrumb(
        val id: String,
        val label: String
    )

    internal enum class DndLaneIndicator {
        NONE,
        BEFORE,
        AFTER
    }

    private companion object {
        const val CONTEXT_MENU_ROOT_ID: String = "fs.root"
        const val CONTEXT_MENU_DOUBLE_CLICK_MS: Long = 320L
    }

    private var viewportWidth: Int = 320
    private var viewportHeight: Int = 240
    internal val viewportWidthPx: Int
        get() = viewportWidth
    internal val viewportHeightPx: Int
        get() = viewportHeight

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
    internal var msdfFontIndex by state(0)
    internal var msdfOpacityPercent by state(100L)
    internal var msdfFontSizePx by state(9L)
    internal var msdfWrapWidth by state(220L)
    internal var msdfColorIndex by state(0)
    internal var msdfParseMinecraftFormatting by state(true)
    internal var msdfShowBaselineGuides by state(MsdfRuntimeDebugSettings.decorationGuidesEnabled)
    internal var animationsToggle by state(false)
    internal var animationsHover by state(false)
    internal var animationsPaused by state(false)
    internal var animationsDurationMs by state(1400L)
    internal var animationsUseInfinite by state(true)
    internal var animationsEasingIndex by state(0)
    internal var animationsDirectionIndex by state(0)
    internal var animationsFillModeIndex by state(0)
    internal var animationsBezierX1 by state(17L)
    internal var animationsBezierY1 by state(67L)
    internal var animationsBezierX2 by state(83L)
    internal var animationsBezierY2 by state(67L)
    internal var modalBackgroundCounter by state(0)
    internal var modalPromptValue by state("hello")
    internal var demoModals by state(emptyList<ModalSpec>())
    internal var contextMenuLastAction by state("none")
    internal var contextMenuLastTarget by state("none")
    internal var contextMenuActionCount by state(0)
    internal var contextMenuOpenAnchored by state(false)
    internal var contextMenuShowCursorDebug by state(false)
    internal var contextMenuClipboardHasData by state(false)
    internal var contextMenuClipboardEntryName by state("clipboard.txt")
    internal var contextMenuClipboardEntryId by state<String?>(null)
    internal var contextMenuSortMode by state("Name")
    internal var contextMenuCursorX by state(-1)
    internal var contextMenuCursorY by state(-1)
    internal var contextMenuCursorOwner by state("none")
    internal var contextMenuCursorLocalX by state(0)
    internal var contextMenuCursorLocalY by state(0)
    internal var contextMenuPinned by state(false)
    internal var contextMenuFileSelection by state("README.md")
    internal var contextMenuCurrentDirectoryId by state(CONTEXT_MENU_ROOT_ID)
    internal var contextMenuBackHistory by state(emptyList<String>())
    internal var contextMenuForwardHistory by state(emptyList<String>())
    internal var contextMenuRenameTargetId by state<String?>(null)
    internal var contextMenuRenameDraft by state("")
    internal var contextMenuDragHoverDirectoryId by state<String?>(null)
    private var contextMenuLastClickEntryId: String? = null
    private var contextMenuLastClickMs: Long = 0L
    internal var contextMenuFiles by state(defaultContextMenuFiles())
    private var contextMenuFileSequence by state(100L)
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
    internal var cascadeParentDark by state(false)
    internal var cascadeRuleAEnabled by state(true)
    internal var cascadeAdjacentSourceEnabled by state(true)
    internal var cascadeAdjacentSwapOrder by state(false)
    internal var cascadeGeneralWarningIndex by state(1L)
    internal var cascadeGeneralInsertExtra by state(false)
    internal var cascadeMixedSpacerEnabled by state(false)

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
    internal var selectBasicValue by state<String?>(null)
    internal var selectManyValue by state<String?>("item-05")
    internal var selectDisabledValue by state<String?>("locked")
    internal var selectDynamicValue by state<String?>("alpha")
    internal var selectDynamicAlt by state(false)
    internal var toggleBasicValue by state(false)
    internal var toggleSecondaryValue by state(true)
    internal var colorInlineValue by state(RgbaColor(0.28f, 0.52f, 0.88f, 1f))
    internal var colorInlineMode by state(ColorFormatMode.HEX)
    internal var colorPopupValue by state(RgbaColor(0.82f, 0.31f, 0.41f, 0.9f))
    internal var colorPopupSecondValue by state(RgbaColor(0.29f, 0.73f, 0.46f, 1f))
    internal var colorSharedA by state(RgbaColor(0.91f, 0.73f, 0.19f, 1f))
    internal var colorSharedB by state(RgbaColor(0.45f, 0.41f, 0.96f, 0.8f))
    internal var colorSharedTarget by state("A")
    internal var colorPickerLastCommit by state("none")
    internal var colorPickerAlphaEnabled by state(true)
    private val sharedColorPickerManager: ColorPickerPopupManager = ColorPickerPopupManager()
    internal var sharedRangeValue by state(35L)
    internal var clippingScrollDemoText by state(buildClippingScrollDemoText())
    internal var overflowDemoOverflowX by state(Overflow.Auto)
    internal var overflowDemoOverflowY by state(Overflow.Auto)
    internal var overflowDemoViewportWidth by state(118L)
    internal var overflowDemoViewportHeight by state(76L)
    internal var overflowDemoContentWidth by state(132L)
    internal var overflowDemoContentHeight by state(126L)
    internal var overflowDemoVisibleClicks by state(0)
    internal var overflowDemoEdgeClicks by state(0)
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
    internal var inspectorBehindClickCounter by state(0)
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
        prepareCascadeStylesheet()
        registerAnimationKeyframes()
        loadStylesheetEditorFromFile("window open")
        DndSystem.setSmoothingFactor(dndSmoothFactor)
        MsdfRuntimeDebugSettings.decorationGuidesEnabled = msdfShowBaselineGuides
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
            modalHost(modals = demoModals, modalKey = "showcase.modalHost") {
                div({
                    key = "showcase.root"
                    style = {
                        display = Display.Flex
                        flexDirection = FlexDirection.Column
                        width = 100.vw
                        height = 100.vh
                        padding = 4.px
                        gap = 4.px
                        backgroundColor = DEMO_BG
                    }
                }) {
                    text("DSGL Showcase Window", { style = { color = DsglColors.WHITE } })
                    text(
                        "renderPasses=$renderPasses section=${selectedSection.title} viewport=${viewportWidth}x$viewportHeight",
                        { style = { color = DEMO_MUTED } }
                    )

                    div({
                        key = "showcase.body"
                        style = {
                            gap = 4.px
                            display = Display.Flex
                            flexDirection = FlexDirection.Row
                        }
                    }) {
                        div({
                            key = "showcase.nav"
                            style = {
                                display = Display.Flex
                                flexDirection = FlexDirection.Column
                                gap = 4.px
                                backgroundColor = DEMO_SURFACE
                                color = DsglColors.TEXT
                                border(1.px, DsglColors.BORDER)
                            }

                        }) {
                            text("Sections", { style = { color = DsglColors.WHITE } })
                            DemoSection.entries.forEach { section ->
                                button(section.title, {
                                    key = "nav.${section.name.lowercase()}"
                                    style = {
                                        backgroundColor =
                                            if (selectedSection == section) DEMO_ACCENT else DsglColors.BUTTON
                                    }
                                    onMouseClick = { selectSection(section) }
                                })
                            }
                        }

                        div({
                            key = "showcase.content"
                            style = {
                                display = Display.Flex
                                flexDirection = FlexDirection.Column
                                gap = 4.px
                                backgroundColor = DEMO_SURFACE
                                color = DsglColors.TEXT
                                border(1.px, DsglColors.BORDER)
                            }
                        }) {
                            text(selectedSection.title, { style = { color = DsglColors.WHITE } })
                            text(selectedSection.subtitle, { style = { color = DEMO_MUTED } })
                            when (selectedSection) {
                                DemoSection.OVERVIEW -> overviewSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.INSPECTOR -> inspectorSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.LAYOUT_STYLE -> layoutStyleSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.LAYOUT_DEBUG -> layoutDebugSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.OVERFLOW_SCROLL -> overflowScrollSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.DISPLAY -> displaySection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.TEXT_WRAP -> textWrapSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.MSDF_FONTS -> msdfFontsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.ANIMATIONS -> animationsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.MODALS -> modalsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.CONTEXT_MENU -> contextMenuSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.STYLESHEETS -> stylesheetsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.CSS_CASCADE -> cssCascadeCombinatorsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.INPUTS -> inputsGallerySection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.INPUT_EVENTS -> inputEventsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.COLOR_PICKER -> colorPickerSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.TEXT_EDITING -> textEditingSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.REFS -> refsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30,
                                    refsInputHandle,
                                    refsPanelHandle
                                )

                                DemoSection.DRAG_DROP -> dragNDropSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.INTERACTIONS -> interactionsSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.FOCUS_REBUILD -> focusRebuildSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )

                                DemoSection.MC_FEATURES -> mcFeaturesSection(
                                    this@ShowcaseWindow,
                                    contentWidth - 10,
                                    bodyHeight - 30
                                )
                            }
                        }

                        div({
                            key = "showcase.side"
                            style = {
                                gap = 4.px
                                display = Display.Flex
                                flexDirection = FlexDirection.Column
                            }
                        }) {
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

    internal fun recordContextMenuAction(target: String, action: String) {
        contextMenuLastTarget = target
        contextMenuLastAction = action
        contextMenuActionCount += 1
        appendInfo("Context menu [$target]: $action")
    }

    internal fun recordContextMenuCursor(
        owner: String,
        mouseX: Int,
        mouseY: Int,
        localX: Int,
        localY: Int
    ) {
        contextMenuCursorOwner = owner
        contextMenuCursorX = mouseX
        contextMenuCursorY = mouseY
        contextMenuCursorLocalX = localX
        contextMenuCursorLocalY = localY
    }

    internal fun contextMenuVisibleFiles(): List<ContextMenuDemoFile> {
        return sortedChildrenForDirectory(contextMenuCurrentDirectoryId)
    }

    internal fun contextMenuCurrentPath(): String {
        val byId = contextMenuFiles.associateBy { it.id }
        val path = ArrayDeque<String>()
        var currentId: String? = contextMenuCurrentDirectoryId
        while (currentId != null) {
            val entry = byId[currentId] ?: break
            if (entry.id != CONTEXT_MENU_ROOT_ID) {
                path.addFirst(entry.name)
            }
            currentId = entry.parentId
        }
        return if (path.isEmpty()) "/" else "/" + path.joinToString("/")
    }

    internal fun contextMenuBreadcrumbs(): List<ContextMenuBreadcrumb> {
        val byId = contextMenuFiles.associateBy { it.id }
        val breadcrumbs = ArrayDeque<ContextMenuBreadcrumb>()
        var currentId: String? = contextMenuCurrentDirectoryId
        while (currentId != null) {
            val entry = byId[currentId] ?: break
            val label = if (entry.id == CONTEXT_MENU_ROOT_ID) "Workspace" else entry.name
            breadcrumbs.addFirst(ContextMenuBreadcrumb(entry.id, label))
            currentId = entry.parentId
        }
        return breadcrumbs.toList()
    }

    internal fun contextMenuCanGoBack(): Boolean = contextMenuBackHistory.isNotEmpty()

    internal fun contextMenuCanGoForward(): Boolean = contextMenuForwardHistory.isNotEmpty()

    internal fun contextMenuCanGoUp(): Boolean {
        val current = contextMenuEntryById(contextMenuCurrentDirectoryId) ?: return false
        return current.parentId != null
    }

    internal fun contextMenuNavigateBack() {
        if (!contextMenuCanGoBack()) return
        val previous = contextMenuBackHistory.last()
        contextMenuBackHistory = contextMenuBackHistory.dropLast(1)
        contextMenuForwardHistory = listOf(contextMenuCurrentDirectoryId) + contextMenuForwardHistory
        contextMenuCurrentDirectoryId = previous
        contextMenuRenameTargetId = null
        contextMenuDragHoverDirectoryId = null
        recordContextMenuAction("navigator", "back to ${contextMenuCurrentPath()}")
    }

    internal fun contextMenuNavigateForward() {
        val target = contextMenuForwardHistory.firstOrNull() ?: return
        contextMenuForwardHistory = contextMenuForwardHistory.drop(1)
        contextMenuBackHistory = contextMenuBackHistory + contextMenuCurrentDirectoryId
        contextMenuCurrentDirectoryId = target
        contextMenuRenameTargetId = null
        contextMenuDragHoverDirectoryId = null
        recordContextMenuAction("navigator", "forward to ${contextMenuCurrentPath()}")
    }

    internal fun contextMenuNavigateUp() {
        val current = contextMenuEntryById(contextMenuCurrentDirectoryId) ?: return
        val parentId = current.parentId ?: return
        contextMenuOpenDirectory(parentId, pushHistory = true)
    }

    internal fun contextMenuHandleEntryClick(file: ContextMenuDemoFile) {
        contextMenuFileSelection = file.name
        if (!file.isDirectory) return
        val now = System.currentTimeMillis()
        val isDoubleClick =
            contextMenuLastClickEntryId == file.id && (now - contextMenuLastClickMs) <= CONTEXT_MENU_DOUBLE_CLICK_MS
        contextMenuLastClickEntryId = file.id
        contextMenuLastClickMs = now
        if (isDoubleClick) {
            contextMenuOpenDirectory(file.id, pushHistory = true)
        }
    }

    internal fun contextMenuOpenDirectory(directoryId: String, pushHistory: Boolean) {
        val directory = contextMenuFiles.firstOrNull { it.id == directoryId && it.isDirectory } ?: return
        if (pushHistory && directory.id != contextMenuCurrentDirectoryId) {
            contextMenuBackHistory = contextMenuBackHistory + contextMenuCurrentDirectoryId
            contextMenuForwardHistory = emptyList()
        }
        contextMenuCurrentDirectoryId = directory.id
        contextMenuRenameTargetId = null
        contextMenuDragHoverDirectoryId = null
        recordContextMenuAction("navigator", "open ${contextMenuCurrentPath()}")
    }

    internal fun contextMenuSetSortMode(mode: String) {
        contextMenuSortMode = mode
        recordContextMenuAction("background", "sort by ${mode.lowercase()}")
    }

    internal fun contextMenuCreateFolder(parentId: String = contextMenuCurrentDirectoryId) {
        val parent = contextMenuFiles.firstOrNull { it.id == parentId && it.isDirectory } ?: return
        val name = uniqueContextMenuName(parent.id, "New Folder")
        val created = ContextMenuDemoFile(
            id = nextContextMenuEntryId(),
            parentId = parent.id,
            name = name,
            sizeKb = 0,
            isDirectory = true,
            locked = false,
            updatedAtOrder = nextContextMenuFileOrder()
        )
        contextMenuFiles = contextMenuFiles + created
        contextMenuFileSelection = created.name
        recordContextMenuAction("background", "new folder $name")
    }

    internal fun contextMenuCreateFile(parentId: String = contextMenuCurrentDirectoryId) {
        val parent = contextMenuFiles.firstOrNull { it.id == parentId && it.isDirectory } ?: return
        if (parent.id != contextMenuCurrentDirectoryId) {
            contextMenuOpenDirectory(parent.id, pushHistory = true)
        }
        val name = uniqueContextMenuName(parent.id, "new-file.txt")
        val created = ContextMenuDemoFile(
            id = nextContextMenuEntryId(),
            parentId = parent.id,
            name = name,
            sizeKb = 1,
            isDirectory = false,
            locked = false,
            updatedAtOrder = nextContextMenuFileOrder()
        )
        contextMenuFiles = contextMenuFiles + created
        contextMenuFileSelection = created.name
        contextMenuRenameTargetId = created.id
        contextMenuRenameDraft = created.name
        recordContextMenuAction("background", "new file $name")
    }

    internal fun contextMenuPasteIntoWorkspace() {
        if (!contextMenuClipboardHasData) return
        val sourceId = contextMenuClipboardEntryId ?: return
        val source = contextMenuEntryById(sourceId) ?: return
        contextMenuDuplicateFile(source, targetParentId = contextMenuCurrentDirectoryId)
        recordContextMenuAction("background", "paste ${source.name}")
    }

    internal fun contextMenuRefreshWorkspace() {
        contextMenuFiles = contextMenuFiles.map { file ->
            file.copy(updatedAtOrder = file.updatedAtOrder + 1L)
        }
        recordContextMenuAction("background", "refresh")
    }

    internal fun contextMenuOpenFile(file: ContextMenuDemoFile) {
        if (file.isDirectory) {
            contextMenuOpenDirectory(file.id, pushHistory = true)
            return
        }
        contextMenuFileSelection = file.name
        recordContextMenuAction(file.name, "open")
    }

    internal fun contextMenuBeginRename(file: ContextMenuDemoFile) {
        if (file.locked) return
        contextMenuRenameTargetId = file.id
        contextMenuRenameDraft = file.name
        contextMenuFileSelection = file.name
    }

    internal fun contextMenuApplyRename() {
        val targetId = contextMenuRenameTargetId ?: return
        val target = contextMenuEntryById(targetId) ?: run {
            contextMenuRenameTargetId = null
            return
        }
        if (target.locked) {
            contextMenuRenameTargetId = null
            return
        }
        val draft = contextMenuRenameDraft.trim()
        if (draft.isEmpty()) return
        val resolved = if (draft == target.name) {
            draft
        } else {
            uniqueContextMenuName(target.parentId ?: CONTEXT_MENU_ROOT_ID, draft)
        }
        contextMenuFiles = contextMenuFiles.map { current ->
            if (current.id == target.id) {
                current.copy(
                    name = resolved,
                    updatedAtOrder = nextContextMenuFileOrder()
                )
            } else {
                current
            }
        }
        contextMenuFileSelection = resolved
        contextMenuRenameTargetId = null
        contextMenuRenameDraft = ""
        recordContextMenuAction(target.name, "rename to $resolved")
    }

    internal fun contextMenuCancelRename() {
        contextMenuRenameTargetId = null
        contextMenuRenameDraft = ""
    }

    internal fun contextMenuDuplicateFile(
        file: ContextMenuDemoFile,
        targetParentId: String = file.parentId ?: contextMenuCurrentDirectoryId
    ) {
        val targetParent = contextMenuFiles.firstOrNull { it.id == targetParentId && it.isDirectory } ?: return
        val byId = contextMenuFiles.associateBy { it.id }
        val descendants = collectSubtree(file.id, byId)
        val idRemap = linkedMapOf<String, String>()
        val rootCopyId = nextContextMenuEntryId()
        idRemap[file.id] = rootCopyId
        val rootName = uniqueContextMenuName(targetParent.id, file.name, "copy")
        val copies = ArrayList<ContextMenuDemoFile>(descendants.size)
        copies += file.copy(
            id = rootCopyId,
            parentId = targetParent.id,
            name = rootName,
            locked = false,
            updatedAtOrder = nextContextMenuFileOrder()
        )
        descendants.drop(1).forEach { child ->
            val parentCopyId = idRemap[child.parentId] ?: return@forEach
            val childCopyId = nextContextMenuEntryId()
            idRemap[child.id] = childCopyId
            copies += child.copy(
                id = childCopyId,
                parentId = parentCopyId,
                locked = false,
                updatedAtOrder = nextContextMenuFileOrder()
            )
        }
        contextMenuFiles = contextMenuFiles + copies
        contextMenuFileSelection = rootName
        recordContextMenuAction(file.name, "duplicate as $rootName")
    }

    internal fun contextMenuCopyFile(file: ContextMenuDemoFile) {
        contextMenuClipboardHasData = true
        contextMenuClipboardEntryName = file.name
        contextMenuClipboardEntryId = file.id
        contextMenuFileSelection = file.name
        recordContextMenuAction(file.name, "copied to clipboard")
    }

    internal fun contextMenuMoveFile(file: ContextMenuDemoFile, destinationDirectoryId: String) {
        if (!contextMenuCanDropIntoDirectory(file.id, destinationDirectoryId)) return
        val destination = contextMenuEntryById(destinationDirectoryId) ?: return
        val resolvedName = uniqueContextMenuName(destination.id, file.name)
        contextMenuFiles = contextMenuFiles.map { current ->
            if (current.id == file.id) {
                current.copy(
                    parentId = destination.id,
                    name = resolvedName,
                    updatedAtOrder = nextContextMenuFileOrder()
                )
            } else {
                current
            }
        }
        contextMenuFileSelection = resolvedName
        contextMenuDragHoverDirectoryId = null
        recordContextMenuAction(file.name, "move to ${destination.name}")
    }

    internal fun contextMenuCanDropIntoDirectory(entryId: String, destinationDirectoryId: String): Boolean {
        val entry = contextMenuEntryById(entryId) ?: return false
        val destination = contextMenuEntryById(destinationDirectoryId) ?: return false
        if (!destination.isDirectory) return false
        if (entry.id == destination.id) return false
        if (entry.parentId == destination.id) return false
        if (isDescendantDirectory(ancestorId = entry.id, candidateId = destination.id)) return false
        return true
    }

    internal fun contextMenuDeleteFile(file: ContextMenuDemoFile) {
        if (file.locked) return
        val subtreeIds = collectSubtreeIds(file.id)
        contextMenuFiles = contextMenuFiles.filterNot { subtreeIds.contains(it.id) }
        if (contextMenuCurrentDirectoryId == file.id || contextMenuCurrentDirectoryId in subtreeIds) {
            contextMenuCurrentDirectoryId = CONTEXT_MENU_ROOT_ID
            contextMenuBackHistory = emptyList()
            contextMenuForwardHistory = emptyList()
        } else {
            contextMenuBackHistory = contextMenuBackHistory.filterNot { subtreeIds.contains(it) }
            contextMenuForwardHistory = contextMenuForwardHistory.filterNot { subtreeIds.contains(it) }
        }
        if (contextMenuClipboardEntryId in subtreeIds) {
            contextMenuClipboardHasData = false
            contextMenuClipboardEntryId = null
        }
        contextMenuRenameTargetId = contextMenuRenameTargetId?.takeUnless { subtreeIds.contains(it) }
        if (contextMenuSelectedFileMissing()) {
            contextMenuFileSelection = contextMenuVisibleFiles().firstOrNull()?.name ?: "none"
        }
        recordContextMenuAction(file.name, "delete")
    }

    internal fun contextMenuSelectedFileMissing(): Boolean {
        if (contextMenuFileSelection == "none") return false
        return contextMenuVisibleFiles().none { it.name == contextMenuFileSelection }
    }

    internal fun contextMenuEntryById(entryId: String?): ContextMenuDemoFile? {
        if (entryId == null) return null
        return contextMenuFiles.firstOrNull { it.id == entryId }
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

    internal fun currentGuiScale(): Int {
        return Minecraft.getMinecraft().gameSettings.guiScale.coerceIn(0, 4)
    }

    internal fun guiScaleLabel(value: Int = currentGuiScale()): String {
        return when (value.coerceIn(0, 4)) {
            0 -> "Auto"
            1 -> "1x"
            2 -> "2x"
            3 -> "3x"
            else -> "4x"
        }
    }

    internal fun setGuiScale(value: Int) {
        val mc = Minecraft.getMinecraft()
        val normalized = value.coerceIn(0, 4)
        if (mc.gameSettings.guiScale == normalized) return
        mc.gameSettings.guiScale = normalized
        mc.gameSettings.saveOptions()
        appendInfo("guiScale -> ${guiScaleLabel(normalized)}")
        requestManualInvalidate("guiScale change")
    }

    internal fun cycleGuiScale(step: Int) {
        val current = currentGuiScale()
        val next = (current + step).coerceIn(0, 4)
        setGuiScale(next)
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
            writeDemoFolderIcon(File(dataDir, "dsgl/demo/folder.png"))
            writeDemoDocumentIcon(File(dataDir, "dsgl/demo/document.png"))
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
                      border-width: 1px;
                      border-color: #000000;
                      padding: 3px 6px;
                    }

                    select {
                      background-color: #2B3744;
                      border-color: #5A6D80;
                      border-width: 1px;
                      color: #EAF2FD;
                      padding: 3px 6px;
                    }

                    select:hover {
                      background-color: #324456;
                    }

                    select:focus {
                      border-color: #7CB6FF;
                    }

                    select:open {
                      border-color: #9BD3FF;
                      background-color: #35506B;
                    }

                    select:disabled {
                      background-color: #2B2B2B;
                      border-color: #555555;
                      color: #8E8E8E;
                    }
                    
                    .style-card {
                      margin: 2px 0px 0px 0px;
                      background-color: #2A3440;
                      border-color: #5E6A77;
                      border-width: 1px;
                      padding: 4px;
                    }
                    
                    .accent {
                      background-color: #3F5A70;
                    }
                    
                    button.primary {
                      background-color: var(--primary);
                      color: var(--fg);
                    }
                    
                    #dangerAction {
                      background-color: var(--danger);
                      color: #FFFFFFFF;
                    }
                    
                    #hoverActiveTarget:hover {
                      background-color: #365F7D;
                    }
                    
                    #hoverActiveTarget:active {
                      background-color: #274356;
                    }
                    
                    #focusInput:focus {
                      border-color: var(--accent);
                      border-width: 2px;
                    }
                    
                    #disabledTarget:disabled {
                      background-color: #444444;
                      color: #999999;
                    }
                    
                    .vars-demo {
                      background-color: #213348;
                      border-color: var(--accent);
                    }

                    .units-demo {
                      padding: 8px;
                      gap: 6px;
                      background-color: #1E2C3A;
                      border-color: #5E6A77;
                      border-width: 1px;
                    }

                    .units-vw-chip {
                      width: 20vw;
                      background-color: #355674;
                      border-color: #7CB6FF;
                      border-width: 1px;
                      padding: 2px 4px;
                    }

                    .units-playground {
                      width: 100%;
                      background-color: #17222D;
                      border-color: #4E5E6E;
                      border-width: 1px;
                      padding: 4px;
                    }

                    .units-percent-box {
                      width: 50%;
                      height: 40%;
                      margin: 8% 5%;
                      background-color: #3D6B52;
                      border-color: #8DD0A6;
                      border-width: 1px;
                      padding: 1em;
                    }

                    .units-em-text {
                      font-size: 1.25em;
                      margin: 1em 0;
                      color: #E6F0FF;
                    }

                    .units-vh-bar {
                      width: 40%;
                      height: 8vh;
                      background-color: #5A3F77;
                      border-color: #9B83C5;
                      border-width: 1px;
                      padding: 2px 4px;
                    }
                    """.trimIndent()
                )
                appendInfo("Created demo stylesheet: ${stylesheetFile.name}")
                created = true
            } else {
                val existing = stylesheetFile.readText()
                if (!existing.contains(".units-demo")) {
                    stylesheetFile.appendText(
                        """

                        .units-demo {
                          padding: 8px;
                          gap: 6px;
                          background-color: #1E2C3A;
                          border-color: #5E6A77;
                          border-width: 1px;
                        }

                        .units-vw-chip {
                          width: 20vw;
                          background-color: #355674;
                          border-color: #7CB6FF;
                          border-width: 1px;
                          padding: 2px 4px;
                        }

                        .units-playground {
                          width: 100%;
                          background-color: #17222D;
                          border-color: #4E5E6E;
                          border-width: 1px;
                          padding: 4px;
                        }

                        .units-percent-box {
                          width: 50%;
                          height: 40%;
                          margin: 8% 5%;
                          background-color: #3D6B52;
                          border-color: #8DD0A6;
                          border-width: 1px;
                          padding: 1em;
                        }

                        .units-em-text {
                          font-size: 1.25em;
                          margin: 1em 0;
                          color: #E6F0FF;
                        }

                        .units-vh-bar {
                          width: 40%;
                          height: 8vh;
                          background-color: #5A3F77;
                          border-color: #9B83C5;
                          border-width: 1px;
                          padding: 2px 4px;
                        }
                        """.trimIndent()
                    )
                    appendInfo("Patched demo stylesheet with CSS units section")
                    created = true
                }
                if (!existing.contains("select:open")) {
                    stylesheetFile.appendText(
                        """

                        select {
                          background-color: #2B3744;
                          border-color: #5A6D80;
                          border-width: 1px;
                          color: #EAF2FD;
                          padding: 3px 6px;
                        }

                        select:hover {
                          background-color: #324456;
                        }

                        select:focus {
                          border-color: #7CB6FF;
                        }

                        select:open {
                          border-color: #9BD3FF;
                          background-color: #35506B;
                        }

                        select:disabled {
                          background-color: #2B2B2B;
                          border-color: #555555;
                          color: #8E8E8E;
                        }
                        """.trimIndent()
                    )
                    appendInfo("Patched demo stylesheet with select styles")
                    created = true
                }
            }
            if (created) {
                StyleEngine.forceReloadStylesheets()
            }
        } catch (ex: Exception) {
            appendLog("Stylesheet prep failed: ${ex.javaClass.simpleName}", 0xFFFF9A66.toInt())
        }
    }

    private fun registerAnimationKeyframes() {
        keyframes("showcase.spinFade") {
            at(0f) {
                transform { rotate(0f) }
                opacity = 0.35f
                color = 0xFFFF6B6B.toInt()
            }
            at(50f) {
                transform { rotate(180f); scale(1.08f) }
                opacity = 1f
                color = 0xFF6BCB77.toInt()
            }
            at(100f) {
                transform { rotate(360f) }
                opacity = 0.35f
                color = 0xFF4D96FF.toInt()
            }
        }
    }

    private fun prepareCascadeStylesheet() {
        try {
            val file = cascadeStylesheetFile()
            file.parentFile?.mkdirs()
            file.writeText(
                """
                .cascade-demo-root {
                  background-color: #25303B;
                  border-color: #5A6877;
                  border-width: 1px;
                  padding: 4px;
                }
                
                .cascade-demo-root.dark {
                  color: #FFE4C7;
                }
                
                .cascade-demo-root.light {
                  color: #D7E8FF;
                }
                
                .cascade-demo-root .panel {
                  background-color: #1E2935;
                  border-width: 1px;
                  border-color: #516071;
                  padding: 3px;
                }
                
                .cascade-demo-root .panel .item {
                  color: #7EC8FF;
                }
                
                .cascade-demo-root .panel > .item {
                  color: #9BE66F;
                }
                
                .cascade-demo-root .btn {
                  background-color: #4A5568;
                  color: #FFFFFFFF;
                  border-color: #1F2937;
                  border-width: 1px;
                }
                
                .cascade-demo-root #primary.btn {
                  background-color: #2B6CB0;
                }
                
                .cascade-demo-root .order-target {
                  color: #F56565;
                }
                
                .cascade-demo-root .order-target {
                  color: #48BB78;
                }
                
                .cascade-demo-root .important-target {
                  color: #DD6B20 !important;
                }
                
                .cascade-demo-root .important-target {
                  color: #3182CE;
                }
                
                .cascade-demo-root.rule-a .toggle-target {
                  color: #D69E2E;
                }
                
                .cascade-demo-root.rule-b .toggle-target {
                  color: #63B3ED;
                }

                .cascade-sibling-adj {
                  background-color: #1E2731;
                  border-color: #45576B;
                  border-width: 1px;
                  padding: 3px;
                }

                .cascade-sibling-adj .adj-item {
                  background-color: #2D3A47;
                  border-color: #53667A;
                  border-width: 1px;
                  padding: 2px 4px;
                }

                .cascade-sibling-adj .adj-source {
                  color: #FFDE9E;
                }

                .cascade-sibling-adj .adj-source + .adj-target {
                  color: #7DFFB0;
                  border-color: #7DFFB0;
                }

                .cascade-sibling-general {
                  background-color: #1B2530;
                  border-color: #495D73;
                  border-width: 1px;
                  padding: 3px;
                }

                .cascade-sibling-general .warning {
                  color: #FF9B9B;
                }

                .cascade-sibling-general .warning ~ .gen-target {
                  color: #6EC8FF;
                }

                .cascade-mixed {
                  background-color: #202A34;
                  border-color: #516679;
                  border-width: 1px;
                  padding: 3px;
                }

                .cascade-mixed > .header {
                  color: #D8E6F5;
                }

                .cascade-mixed > .header + .body .title {
                  color: #F6D66F;
                }
                """.trimIndent()
            )
            StyleEngine.forceReloadStylesheets()
        } catch (ex: Exception) {
            appendLog("Cascade stylesheet prep failed: ${ex.javaClass.simpleName}", 0xFFFF9A66.toInt())
        }
    }

    private fun demoStylesheetFile(): File {
        val dataDir = Minecraft.getMinecraft().mcDataDir
        return File(dataDir, "dsgl/styles/showcase_styles.dss")
    }

    internal fun openSharedColorPicker(mouseX: Int, mouseY: Int, target: String) {
        colorSharedTarget = target
        val current = if (target == "A") colorSharedA else colorSharedB
        sharedColorPickerManager.open(
            anchorRect = Rect(mouseX, mouseY, 1, 1),
            title = "Shared Picker [$target]",
            state = ColorPickerState(
                color = current,
                previous = current,
                mode = colorInlineMode,
                alphaEnabled = colorPickerAlphaEnabled,
                closeOnSelect = false
            ),
            closeOnOutsideClick = false,
            onPreview = { color ->
                if (colorSharedTarget == "A") {
                    colorSharedA = color
                } else {
                    colorSharedB = color
                }
            },
            onChange = { color ->
                if (colorSharedTarget == "A") {
                    colorSharedA = color
                } else {
                    colorSharedB = color
                }
            },
            onCommit = { color ->
                if (colorSharedTarget == "A") {
                    colorSharedA = color
                } else {
                    colorSharedB = color
                }
                colorPickerLastCommit = ColorTextCodec.format(color, ColorFormatMode.HEX, includeAlpha = true)
            }
        )
    }

    internal fun colorLabel(color: RgbaColor): String {
        return ColorTextCodec.format(color, ColorFormatMode.HEX, includeAlpha = true)
    }

    private fun cascadeStylesheetFile(): File {
        val dataDir = Minecraft.getMinecraft().mcDataDir
        return File(dataDir, "dsgl/styles/showcase_cascade.dss")
    }

    private fun nextContextMenuFileOrder(): Long {
        contextMenuFileSequence += 1L
        return contextMenuFileSequence
    }

    private fun nextContextMenuEntryId(): String {
        contextMenuFileSequence += 1L
        return "fs.${contextMenuFileSequence}"
    }

    private fun uniqueContextMenuName(parentId: String, baseName: String, variant: String = "new"): String {
        val existing = contextMenuFiles
            .asSequence()
            .filter { it.parentId == parentId }
            .map { it.name }
            .toHashSet()
        if (!existing.contains(baseName)) {
            return baseName
        }
        val (stem, extension) = splitContextMenuName(baseName)
        fun candidate(index: Int): String {
            return when (variant) {
                "copy" -> if (index == 1) "$stem copy$extension" else "$stem copy $index$extension"
                "renamed" -> if (index == 1) "$stem (renamed)$extension" else "$stem (renamed $index)$extension"
                else -> "$stem $index$extension"
            }
        }

        var index = 1
        var next = candidate(index)
        while (existing.contains(next)) {
            index += 1
            next = candidate(index)
        }
        return next
    }

    private fun sortedChildrenForDirectory(directoryId: String): List<ContextMenuDemoFile> {
        val children = contextMenuFiles.filter { it.parentId == directoryId }
        val comparator = when (contextMenuSortMode) {
            "Date" -> compareByDescending<ContextMenuDemoFile> { it.updatedAtOrder }.thenBy { it.name.lowercase() }
            "Size" -> compareByDescending<ContextMenuDemoFile> { it.sizeKb }.thenBy { it.name.lowercase() }
            else -> compareBy<ContextMenuDemoFile> { it.name.lowercase() }
        }
        return children.sortedWith(
            compareBy<ContextMenuDemoFile> { !it.isDirectory }.then(comparator)
        )
    }

    private fun collectSubtree(
        rootId: String,
        byId: Map<String, ContextMenuDemoFile> = contextMenuFiles.associateBy { it.id }
    ): List<ContextMenuDemoFile> {
        val root = byId[rootId] ?: return emptyList()
        val queue = ArrayDeque<String>()
        val orderedIds = ArrayList<String>()
        queue += root.id
        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            orderedIds += currentId
            byId.values
                .filter { it.parentId == currentId }
                .sortedBy { it.name.lowercase() }
                .forEach { queue += it.id }
        }
        return orderedIds.mapNotNull { byId[it] }
    }

    private fun collectSubtreeIds(rootId: String): Set<String> {
        return collectSubtree(rootId).map { it.id }.toSet()
    }

    private fun isDescendantDirectory(ancestorId: String, candidateId: String): Boolean {
        if (ancestorId == candidateId) return true
        val byId = contextMenuFiles.associateBy { it.id }
        var current = byId[candidateId]
        while (current != null) {
            if (current.parentId == ancestorId) return true
            current = current.parentId?.let { byId[it] }
        }
        return false
    }

    private fun splitContextMenuName(name: String): Pair<String, String> {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0 && dotIndex < name.length - 1) {
            name.substring(0, dotIndex) to name.substring(dotIndex)
        } else {
            name to ""
        }
    }

    private fun defaultContextMenuFiles(): List<ContextMenuDemoFile> {
        return listOf(
            ContextMenuDemoFile(
                id = CONTEXT_MENU_ROOT_ID,
                parentId = null,
                name = "Workspace",
                sizeKb = 0,
                isDirectory = true,
                locked = true,
                updatedAtOrder = 1L
            ),
            ContextMenuDemoFile(
                id = "fs.docs",
                parentId = CONTEXT_MENU_ROOT_ID,
                name = "Documents",
                sizeKb = 0,
                isDirectory = true,
                locked = false,
                updatedAtOrder = 2L
            ),
            ContextMenuDemoFile(
                id = "fs.downloads",
                parentId = CONTEXT_MENU_ROOT_ID,
                name = "Downloads",
                sizeKb = 0,
                isDirectory = true,
                locked = false,
                updatedAtOrder = 3L
            ),
            ContextMenuDemoFile(
                id = "fs.projects",
                parentId = CONTEXT_MENU_ROOT_ID,
                name = "Projects",
                sizeKb = 0,
                isDirectory = true,
                locked = false,
                updatedAtOrder = 4L
            ),
            ContextMenuDemoFile(
                id = "fs.readme",
                parentId = CONTEXT_MENU_ROOT_ID,
                name = "README.md",
                sizeKb = 4,
                isDirectory = false,
                locked = true,
                updatedAtOrder = 5L
            ),
            ContextMenuDemoFile(
                id = "fs.build",
                parentId = CONTEXT_MENU_ROOT_ID,
                name = "build.gradle.kts",
                sizeKb = 3,
                isDirectory = false,
                locked = false,
                updatedAtOrder = 6L
            ),
            ContextMenuDemoFile(
                id = "fs.mods",
                parentId = CONTEXT_MENU_ROOT_ID,
                name = "mods.toml",
                sizeKb = 1,
                isDirectory = false,
                locked = false,
                updatedAtOrder = 7L
            ),
            ContextMenuDemoFile(
                id = "fs.atlas",
                parentId = CONTEXT_MENU_ROOT_ID,
                name = "TexturesAtlas.kt",
                sizeKb = 19,
                isDirectory = false,
                locked = false,
                updatedAtOrder = 8L
            ),
            ContextMenuDemoFile(
                id = "fs.notes",
                parentId = CONTEXT_MENU_ROOT_ID,
                name = "notes.txt",
                sizeKb = 2,
                isDirectory = false,
                locked = false,
                updatedAtOrder = 9L
            ),
            ContextMenuDemoFile(
                id = "fs.roadmap",
                parentId = CONTEXT_MENU_ROOT_ID,
                name = "roadmap.md",
                sizeKb = 6,
                isDirectory = false,
                locked = false,
                updatedAtOrder = 10L
            ),
            ContextMenuDemoFile(
                id = "fs.docs.spec",
                parentId = "fs.docs",
                name = "spec.md",
                sizeKb = 5,
                isDirectory = false,
                locked = false,
                updatedAtOrder = 11L
            ),
            ContextMenuDemoFile(
                id = "fs.docs.todos",
                parentId = "fs.docs",
                name = "todos.txt",
                sizeKb = 2,
                isDirectory = false,
                locked = false,
                updatedAtOrder = 12L
            ),
            ContextMenuDemoFile(
                id = "fs.projects.clientA",
                parentId = "fs.projects",
                name = "Client A",
                sizeKb = 0,
                isDirectory = true,
                locked = false,
                updatedAtOrder = 13L
            ),
            ContextMenuDemoFile(
                id = "fs.projects.archive",
                parentId = "fs.projects",
                name = "Archive",
                sizeKb = 0,
                isDirectory = true,
                locked = false,
                updatedAtOrder = 14L
            ),
            ContextMenuDemoFile(
                id = "fs.archive.2025",
                parentId = "fs.projects.archive",
                name = "2025",
                sizeKb = 0,
                isDirectory = true,
                locked = false,
                updatedAtOrder = 15L
            ),
            ContextMenuDemoFile(
                id = "fs.archive.2026",
                parentId = "fs.projects.archive",
                name = "2026",
                sizeKb = 0,
                isDirectory = true,
                locked = false,
                updatedAtOrder = 16L
            ),
            ContextMenuDemoFile(
                id = "fs.archive.2026.q1",
                parentId = "fs.archive.2026",
                name = "Q1-report.md",
                sizeKb = 7,
                isDirectory = false,
                locked = false,
                updatedAtOrder = 17L
            ),
            ContextMenuDemoFile(
                id = "fs.downloads.asset",
                parentId = "fs.downloads",
                name = "texture-pack.zip",
                sizeKb = 24,
                isDirectory = false,
                locked = false,
                updatedAtOrder = 18L
            )
        )
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

    private fun writeDemoFolderIcon(file: File) {
        file.parentFile?.mkdirs()
        val image = BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB)
        val bg = 0xFFE0B25A.toInt()
        val fg = 0xFFC7923D.toInt()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                image.setRGB(x, y, 0x00000000)
            }
        }
        for (y in 5 until 15) {
            for (x in 1 until 17) {
                image.setRGB(x, y, bg)
            }
        }
        for (y in 3 until 7) {
            for (x in 3 until 9) {
                image.setRGB(x, y, fg)
            }
        }
        for (x in 1 until 17) {
            image.setRGB(x, 5, fg)
            image.setRGB(x, 14, fg)
        }
        for (y in 5 until 15) {
            image.setRGB(1, y, fg)
            image.setRGB(16, y, fg)
        }
        ImageIO.write(image, "png", file)
    }

    private fun writeDemoDocumentIcon(file: File) {
        file.parentFile?.mkdirs()
        val image = BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB)
        val bg = 0xFFD7E6F8.toInt()
        val fg = 0xFF94AAC4.toInt()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                image.setRGB(x, y, 0x00000000)
            }
        }
        for (y in 2 until 16) {
            for (x in 3 until 14) {
                image.setRGB(x, y, bg)
            }
        }
        for (x in 3 until 14) {
            image.setRGB(x, 2, fg)
            image.setRGB(x, 15, fg)
        }
        for (y in 2 until 16) {
            image.setRGB(3, y, fg)
            image.setRGB(13, y, fg)
        }
        for (line in 0..4) {
            val y = 5 + line * 2
            for (x in 5 until 12) {
                image.setRGB(x, y, fg)
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

