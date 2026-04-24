package org.dreamfinity.dsgl.mcForge1710.demo

import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.animation.keyframes
import org.dreamfinity.dsgl.core.components.modal.ModalSpec
import org.dreamfinity.dsgl.core.components.modal.modalHost
import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.JustifyContent
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.mcForge1710.McItemStackRef
import org.dreamfinity.dsgl.mcForge1710.demo.sections.McFeaturesSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.animationsSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.colorPickerSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.contextMenuSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.cssCascadeCombinatorsSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.displaySection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.dragNDropSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.focusRebuildSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.hooksSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.inputEventsSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.inputsGallerySection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.inspectorSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.interactionsSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.layoutDebugSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.layoutStyleSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.mcFeaturesSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.modalsSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.msdfFontsSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.overflowScrollSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.overviewSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.positionedLayoutSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.stylesheetsSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.textEditingSection
import org.dreamfinity.dsgl.mcForge1710.demo.sections.textWrapSection
import org.dreamfinity.dsgl.mcForge1710.demo.support.CapabilityChecklistCatalog
import org.dreamfinity.dsgl.mcForge1710.demo.support.CapabilityId
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_ACCENT
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_BG
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_OK
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_SURFACE
import org.dreamfinity.dsgl.mcForge1710.demo.support.DemoSection
import org.dreamfinity.dsgl.mcForge1710.demo.support.EventLogEntry
import org.dreamfinity.dsgl.mcForge1710.demo.support.formatEventLine
import org.dreamfinity.dsgl.mcForge1710.demo.support.renderChecklistPanel
import org.dreamfinity.dsgl.mcForge1710.demo.support.renderEventInspectorPanel
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class ShowcaseWindow : DsglWindow() {
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
    internal var demoModals by state(emptyList<ModalSpec>())
    internal var mediaReady by state(false)

    internal val resourceImageSource: String = "minecraft:textures/gui/options_background.png"
    internal val fileImageSource: String = "file://demo/local_showcase.png"
    internal val httpImageSource: String = "https://demo.local/assets/showcase_http.png"
    internal val flatItemRef = McItemStackRef(ItemStack(Items.diamond_sword, 1, 0))
    internal val blockItemRef = McItemStackRef(ItemStack(Item.getItemFromBlock(Blocks.stone), 1, 0))
    internal var clippingScrollDemoText by state(buildClippingScrollDemoText())

    internal val implementedCapabilities: Set<CapabilityId>
        get() = CapabilityChecklistCatalog.implementedByAllSections()

    override val rebuildOnResize: Boolean
        get() = true

    override fun onOpen() {
        prepareDemoMedia()
        prepareDemoStylesheet()
        prepareCascadeStylesheet()
        registerAnimationKeyframes()
        appendInfo("Showcase opened")
    }

    override fun onResize(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    override fun render(): DomTree {
        renderPasses += 1

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
                        {
                            style = {
                                color = DEMO_MUTED
                                padding = 4.px
                            }
                        },
                    )

                    div({
                        key = "showcase.body"
                        style = {
                            gap = 4.px
                            display = Display.Flex
                            flexDirection = FlexDirection.Row
                            justifyContent = JustifyContent.SpaceBetween
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
                                border {
                                    width = 1.px
                                    color = DsglColors.BORDER
                                }
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
                                flexGrow = 2.0f
                                flexDirection = FlexDirection.Column
                                gap = 4.px
                                backgroundColor = DEMO_SURFACE
                                color = DsglColors.TEXT
                                border {
                                    width = 1.px
                                    color = DsglColors.BORDER
                                }
                            }
                        }) {
                            text(selectedSection.title, { style = { color = DsglColors.WHITE } })
                            text(selectedSection.subtitle, { style = { color = DEMO_MUTED } })
                            when (selectedSection) {
                                DemoSection.OVERVIEW ->
                                    overviewSection(
                                        implementedCapabilities = implementedCapabilities,
                                        onManualInvalidate = ::requestManualInvalidate,
                                        onInfo = ::appendInfo,
                                    )

                                DemoSection.INSPECTOR ->
                                    inspectorSection(
                                        onInfo = ::appendInfo,
                                    )

                                DemoSection.LAYOUT_STYLE ->
                                    layoutStyleSection(
                                        onInfo = ::appendInfo,
                                        onLogHook = { hookName, event, note -> logHook(hookName, event, note) },
                                    )

                                DemoSection.LAYOUT_DEBUG ->
                                    layoutDebugSection(
                                        onClearLogs = ::clearEventLogs,
                                        onInfo = ::appendInfo,
                                    )

                                DemoSection.POSITIONED_LAYOUT ->
                                    positionedLayoutSection(
                                        viewportWidthPx = viewportWidthPx,
                                    )

                                DemoSection.OVERFLOW_SCROLL ->
                                    overflowScrollSection(
                                        onInfo = ::appendInfo,
                                    )

                                DemoSection.DISPLAY ->
                                    displaySection(
                                        onInfo = ::appendInfo,
                                        onLogHook = { hookName, event, note -> logHook(hookName, event, note) },
                                    )

                                DemoSection.TEXT_WRAP ->
                                    textWrapSection(
                                        onInfo = ::appendInfo,
                                    )

                                DemoSection.MSDF_FONTS ->
                                    msdfFontsSection(
                                        onInfo = ::appendInfo,
                                    )

                                DemoSection.ANIMATIONS ->
                                    animationsSection(
                                        onInfo = ::appendInfo,
                                    )

                                DemoSection.MODALS ->
                                    modalsSection(
                                        modals = demoModals,
                                        onPushModal = ::pushModal,
                                        onRemoveModal = ::removeModal,
                                        onPopTopModal = ::popTopModal,
                                        onClearModals = { demoModals = emptyList() },
                                        onInfo = ::appendInfo,
                                    )

                                DemoSection.CONTEXT_MENU ->
                                    contextMenuSection(
                                        onInfo = ::appendInfo,
                                    )

                                DemoSection.STYLESHEETS ->
                                    stylesheetsSection(
                                        onLogHook = { hookName, event, note -> logHook(hookName, event, note) },
                                        onInfo = ::appendInfo,
                                        loadStylesheetText = { loadStylesheetEditorFromFile("styles section load") },
                                        saveStylesheetText = { content ->
                                            saveStylesheetEditorToFile(
                                                content,
                                                "styles section save",
                                            )
                                        },
                                        onReloadStylesheets = {
                                            reloadStylesheetsProgrammatically(
                                                "styles section button",
                                            )
                                        },
                                    )

                                DemoSection.CSS_CASCADE ->
                                    cssCascadeCombinatorsSection(
                                        onLogHook = { hookName, event, note -> logHook(hookName, event, note) },
                                    )

                                DemoSection.INPUTS ->
                                    inputsGallerySection(
                                        clippingScrollDemoText = clippingScrollDemoText,
                                        onClippingScrollDemoTextChange = { clippingScrollDemoText = it },
                                    )

                                DemoSection.INPUT_EVENTS ->
                                    inputEventsSection(
                                        onLogHook = { hookName, event, note -> logHook(hookName, event, note) },
                                    )

                                DemoSection.COLOR_PICKER -> colorPickerSection()

                                DemoSection.TEXT_EDITING ->
                                    textEditingSection(
                                        onLogHook = { hookName, event, note -> logHook(hookName, event, note) },
                                    )

                                DemoSection.REFS ->
                                    hooksSection(
                                        onInfo = ::appendInfo,
                                        onLogHook = { hookName, event, note -> logHook(hookName, event, note) },
                                    )

                                DemoSection.DRAG_DROP ->
                                    dragNDropSection(
                                        onInfo = ::appendInfo,
                                        onClearLogs = ::clearEventLogs,
                                        onLogHook = { hookName, event, note -> logHook(hookName, event, note) },
                                    )

                                DemoSection.INTERACTIONS ->
                                    interactionsSection(
                                        onInfo = ::appendInfo,
                                        onLogHook = { hookName, event, note -> logHook(hookName, event, note) },
                                    )

                                DemoSection.FOCUS_REBUILD ->
                                    focusRebuildSection(
                                        renderPasses = renderPasses,
                                        onManualInvalidate = ::requestManualInvalidate,
                                        onInfo = ::appendInfo,
                                        onLogHook = { hookName, event, note -> logHook(hookName, event, note) },
                                    )

                                DemoSection.MC_FEATURES ->
                                    mcFeaturesSection(
                                        props =
                                            McFeaturesSection(
                                                viewportWidthPx = viewportWidthPx,
                                                viewportHeightPx = viewportHeightPx,
                                                mediaReady = mediaReady,
                                                resourceImageSource = resourceImageSource,
                                                fileImageSource = fileImageSource,
                                                httpImageSource = httpImageSource,
                                                flatItemRef = flatItemRef,
                                                blockItemRef = blockItemRef,
                                                clippingScrollDemoText = clippingScrollDemoText,
                                                onClippingScrollDemoTextChange = { clippingScrollDemoText = it },
                                                currentGuiScale = ::currentGuiScale,
                                                guiScaleLabel = ::guiScaleLabel,
                                                setGuiScale = ::setGuiScale,
                                                cycleGuiScale = ::cycleGuiScale,
                                                onLogHook = { hookName, event, note -> logHook(hookName, event, note) },
                                            ),
                                    )
                            }
                        }

                        div({
                            key = "showcase.side"
                            style = {
                                gap = 4.px
                                display = Display.Flex
                                flexDirection = FlexDirection.Column
                                width = 15.vw
                            }
                        }) {
                            renderEventInspectorPanel(
                                eventLogs = eventLogs,
                                maxEventLogs = maxEventLogs,
                                visibleEventLines = visibleEventLines,
                                onClearLogs = ::clearEventLogs,
                            )
                            renderChecklistPanel(
                                implementedCapabilities = implementedCapabilities,
                                checklistPage = checklistPage,
                                checklistPageSize = checklistPageSize,
                                onSetChecklistPage = { checklistPage = it },
                                onMoveChecklistPage = ::moveChecklistPage,
                            )
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

    internal fun requestManualInvalidate(_reason: String) {
        invalidate()
    }

    internal fun logHook(
        hookName: String,
        event: Event,
        note: String? = null,
        color: Int = DsglColors.TEXT,
    ) {
        val line = formatEventLine(hookName, event, note)
        appendLog(line, color)
    }

    internal fun appendInfo(message: String) {
        appendLog(message, DEMO_OK)
    }

    internal fun currentGuiScale(): Int =
        Minecraft
            .getMinecraft()
            .gameSettings.guiScale
            .coerceIn(0, 4)

    internal fun guiScaleLabel(value: Int = currentGuiScale()): String =
        when (value.coerceIn(0, 4)) {
            0 -> "Auto"
            1 -> "1x"
            2 -> "2x"
            3 -> "3x"
            else -> "4x"
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

    internal fun reloadStylesheetsProgrammatically(source: String) {
        StyleEngine.forceReloadStylesheets()
        requestManualInvalidate("stylesheets reload")
        appendInfo("Stylesheets reloaded by $source")
    }

    internal fun loadStylesheetEditorFromFile(source: String): String {
        try {
            val file = demoStylesheetFile()
            if (!file.exists()) {
                prepareDemoStylesheet()
            }
            val content = file.readText()
            appendInfo("Stylesheet loaded by $source")
            return content
        } catch (ex: java.io.IOException) {
            appendLog("Stylesheet load failed: ${ex.javaClass.simpleName}", 0xFFFF9A66.toInt())
            throw ex
        }
    }

    internal fun saveStylesheetEditorToFile(content: String, source: String) {
        try {
            val file = demoStylesheetFile()
            file.parentFile?.mkdirs()
            file.writeText(content)
            appendInfo("Stylesheet saved by $source")
        } catch (ex: java.io.IOException) {
            appendLog("Stylesheet save failed: ${ex.javaClass.simpleName}", 0xFFFF9A66.toInt())
            throw ex
        }
    }

    private fun selectSection(section: DemoSection) {
        if (selectedSection == section) return
        selectedSection = section
        appendInfo("Section: ${section.title}")
    }

    private fun appendLog(line: String, color: Int) {
        logSequence += 1
        val entry = EventLogEntry(logSequence, line, color)
        eventLogs = (listOf(entry) + eventLogs).take(maxEventLogs)
    }

    private fun prepareDemoMedia() {
        try {
            val dataDir = Minecraft.getMinecraft().mcDataDir
            writeDemoImage(
                File(dataDir, "dsgl/demo/local_showcase.png"),
                0xFF3B71A5.toInt(),
                0xFFF7B25B.toInt(),
            )
            writeDemoImage(
                File(dataDir, "dsgl/cache/downloads/demo.local/assets/showcase_http.png"),
                0xFF2D8757.toInt(),
                0xFFC8E66B.toInt(),
            )
            writeDemoFolderIcon(File(dataDir, "dsgl/demo/folder.png"))
            writeDemoDocumentIcon(File(dataDir, "dsgl/demo/document.png"))
            mediaReady = true
            appendInfo("Prepared local file:// and cached http image assets")
        } catch (ex: java.io.IOException) {
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
                    """.trimIndent(),
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
                        """.trimIndent(),
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
                        """.trimIndent(),
                    )
                    appendInfo("Patched demo stylesheet with select styles")
                    created = true
                }
            }
            if (created) {
                StyleEngine.forceReloadStylesheets()
            }
        } catch (ex: java.io.IOException) {
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
                transform {
                    rotate(180f)
                    scale(1.08f)
                }
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
                """.trimIndent(),
            )
            StyleEngine.forceReloadStylesheets()
        } catch (ex: java.io.IOException) {
            appendLog("Cascade stylesheet prep failed: ${ex.javaClass.simpleName}", 0xFFFF9A66.toInt())
        }
    }

    private fun demoStylesheetFile(): File {
        val dataDir = Minecraft.getMinecraft().mcDataDir
        return File(dataDir, "dsgl/styles/showcase_styles.dss")
    }

    private fun cascadeStylesheetFile(): File {
        val dataDir = Minecraft.getMinecraft().mcDataDir
        return File(dataDir, "dsgl/styles/showcase_cascade.dss")
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
