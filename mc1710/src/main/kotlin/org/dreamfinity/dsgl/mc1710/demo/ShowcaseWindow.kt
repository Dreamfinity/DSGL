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
import org.dreamfinity.dsgl.core.dom.elements.InputOption
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyInput
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.ui
import org.dreamfinity.dsgl.mc1710.McItemStackRef
import org.dreamfinity.dsgl.mc1710.demo.sections.renderFocusRebuildSection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderInputsGallerySection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderInteractionsSection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderLayoutStyleSection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderMcFeaturesSection
import org.dreamfinity.dsgl.mc1710.demo.sections.renderOverviewSection
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
import javax.imageio.ImageIO
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
                        DemoSection.values().forEach { section ->
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
                            DemoSection.INPUTS -> renderInputsGallerySection(this@ShowcaseWindow, contentWidth - 10, bodyHeight - 30)
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

            if (selectedSection == DemoSection.LAYOUT_STYLE && stackOverlayEnabled) {
                div(
                    ComponentProps(
                        key = "layout.stack.overlay",
                        width = 172,
                        backgroundColor = 0xCC5A3131.toInt(),
                        onMouseClick = { event ->
                            overlayClicks += 1
                            logHook("overlay.onMouseClick", event, "overlayClicks=$overlayClicks")
                        },
                        style = {
                            margin(28, 0, 0, 122)
                            padding(4)
                            border(1, 0xFF8D4848.toInt())
                        }
                    )
                ) {
                    dynamicText(
                        DynamicTextProps {
                            "Stack demo overlay (clicks=$overlayClicks)"
                        }.apply { color = DsglColors.WHITE }
                    )
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

    internal fun itemRotYLong(): Long = itemRotY.roundToLong().coerceIn(0L, 360L)

    internal fun itemRotXLong(): Long = itemRotX.roundToLong().coerceIn(-89L, 89L)

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

    private fun normalizeAngle(value: Double): Double {
        var normalized = value % 360.0
        if (normalized < 0.0) normalized += 360.0
        return normalized
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
                File(dataDir, "dsgl_cache/demo.local/assets/showcase_http.png"),
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
}
