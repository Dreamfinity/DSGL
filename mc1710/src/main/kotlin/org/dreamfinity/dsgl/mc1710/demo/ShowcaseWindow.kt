package org.dreamfinity.dsgl.mc1710.demo

import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.DynamicTextProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.InputOption
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyInput
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseDragEvent
import org.dreamfinity.dsgl.core.event.MouseUpEvent
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.ui
import org.dreamfinity.dsgl.mc1710.McItemStackRef
import org.dreamfinity.dsgl.mc1710.demo.sections.renderFocusRebuildSection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderInputsGallerySection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderInputEventsSection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderInteractionsSection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderLayoutStyleSection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderMcFeaturesSection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderOverviewSection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderStylesheetsSection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderTextEditingSection
import org.dreamfinity.dsgl.mc1710.demo.support.CapabilityChecklistCatalog
import org.dreamfinity.dsgl.mc1710.demo.support.CapabilityId
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_BG
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_OK
import org.dreamfinity.dsgl.mc1710.demo.support.DemoSection
import org.dreamfinity.dsgl.mc1710.demo.support.EventLogEntry
import org.dreamfinity.dsgl.mc1710.demo.support.formatEventLine
import org.dreamfinity.dsgl.mc1710.demo.support.navButtonProps
import org.dreamfinity.dsgl.mc1710.demo.support.panelProps
import org.dreamfinity.dsgl.mc1710.demo.support.renderChecklistPanel
import org.dreamfinity.dsgl.mc1710.demo.support.renderEventInspectorPanel
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToLong

class ShowcaseWindow : DsglWindow() {
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

    internal val implementedCapabilities: Set<CapabilityId>
        get() = CapabilityChecklistCatalog.implementedByAllSections()
    internal val openedAtForDemo
        get() = openedAt
    internal val timeZoneForDemo
        get() = timeZoneId

    override val rebuildOnResize: Boolean
        get() = true

    override fun onOpen() {
        prepareDemoMedia()
        prepareDemoStylesheet()
        loadStylesheetEditorFromFile("window open")
        appendInfo("Showcase opened")
    }

    override fun onResize(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    override fun render(): DomTree {
        renderPasses += 1

        val navWidth = 106
        val sidebarWidth = 158
        val bodyHeight = (viewportHeight - 34).coerceAtLeast(170)
        val contentWidth = (viewportWidth - navWidth - sidebarWidth - 18).coerceAtLeast(160)
        val inspectorHeight = (bodyHeight * 56) / 100
        val checklistHeight = (bodyHeight - inspectorHeight - 4).coerceAtLeast(72)

        return ui {
            column(
                ComponentProps(
                    key = "showcase.root",
                    padding = 4,
                    gap = 4,
                    backgroundColor = DEMO_BG
                )
            ) {
                text(TextProps("DSGL Showcase Window").apply { color = DsglColors.WHITE })
                dynamicText(
                    DynamicTextProps {
                        "renderPasses=$renderPasses section=${selectedSection.title} viewport=${viewportWidth}x$viewportHeight"
                    }.apply { color = DEMO_MUTED }
                )

                row(ComponentProps(key = "showcase.body", gap = 4)) {
                    div(
                        panelProps(
                            key = "showcase.nav",
                            width = navWidth,
                            height = bodyHeight
                        )
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
                            DemoSection.OVERVIEW -> renderOverviewSection(this@ShowcaseWindow, contentWidth - 10, bodyHeight - 30)
                            DemoSection.LAYOUT_STYLE -> renderLayoutStyleSection(this@ShowcaseWindow, contentWidth - 10, bodyHeight - 30)
                            DemoSection.STYLESHEETS -> renderStylesheetsSection(this@ShowcaseWindow, contentWidth - 10, bodyHeight - 30)
                            DemoSection.INPUTS -> renderInputsGallerySection(this@ShowcaseWindow, contentWidth - 10, bodyHeight - 30)
                            DemoSection.INPUT_EVENTS -> renderInputEventsSection(this@ShowcaseWindow, contentWidth - 10, bodyHeight - 30)
                            DemoSection.TEXT_EDITING -> renderTextEditingSection(this@ShowcaseWindow, contentWidth - 10, bodyHeight - 30)
                            DemoSection.INTERACTIONS -> renderInteractionsSection(this@ShowcaseWindow, contentWidth - 10, bodyHeight - 30)
                            DemoSection.FOCUS_REBUILD -> renderFocusRebuildSection(this@ShowcaseWindow, contentWidth - 10, bodyHeight - 30)
                            DemoSection.MC_FEATURES -> renderMcFeaturesSection(this@ShowcaseWindow, contentWidth - 10, bodyHeight - 30)
                        }
                    }

                    column(
                        ComponentProps(
                            key = "showcase.side",
                            width = sidebarWidth,
                            height = bodyHeight,
                            gap = 4
                        )
                    ) {
                        renderEventInspectorPanel(this@ShowcaseWindow, sidebarWidth, inspectorHeight)
                        renderChecklistPanel(this@ShowcaseWindow, sidebarWidth, checklistHeight)
                    }
                }
            }

        }
    }

    internal fun clearEventLogs() {
        eventLogs = emptyList()
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
        layoutOverlayDragAnchorX = (event.mouseX - overlayNode.bounds.x).coerceIn(0, overlayNode.bounds.width.coerceAtLeast(1))
        layoutOverlayDragAnchorY = (event.mouseY - overlayNode.bounds.y).coerceIn(0, overlayNode.bounds.height.coerceAtLeast(1))
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

    private fun selectSection(section: DemoSection) {
        if (selectedSection == section) return
        selectedSection = section
        interactionZoneInside = false
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
