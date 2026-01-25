package org.dreamfinity.dsgl.mc1710

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.gui.GuiScreen
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.host.DsglWindowHost
import org.dreamfinity.dsgl.core.host.Viewport
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.time.Instant
import java.time.ZoneId

/**
 * Minecraft 1.7.10 host that owns UI lifecycle and boilerplate.
 *
 * Subclass or instantiate with a [DsglWindow] and open it via
 * `Minecraft.getMinecraft().displayGuiScreen(...)`.
 */
@SideOnly(Side.CLIENT)
abstract class DsglScreenHost(
    private val windowFactory: () -> DsglWindow,
    var rendersCount: Long = 0
) : GuiScreen(), DsglWindowHost {
    constructor(window: DsglWindow) : this({ window })

    override lateinit var window: DsglWindow
    private lateinit var adapter: Mc1710UiAdapter
    private var domTree: DomTree? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0
    private var needsRender: Boolean = true
    private var needsLayout: Boolean = true
    private var lastMouseEvent: Long = 0
    private var eventButton: Int = -1
    private var lastMouseX: Int = 0
    private var lastMouseY: Int = 0
    private var lastMoveX: Int = Int.MIN_VALUE
    private var lastMoveY: Int = Int.MIN_VALUE
    private val pressedKeys: MutableSet<Int> = HashSet()
    private val hoverChain: MutableList<DOMNode> = mutableListOf()
    private var hoverTarget: DOMNode? = null
    private var pendingCleanupRoot: DOMNode? = null

    override fun initGui() {
        adapter = Mc1710UiAdapter(mc)
        window = windowFactory()
        window.attachHost(this)
        window.markOpened(Instant.now(), ZoneId.systemDefault())
        needsRender = true
        needsLayout = true
        window.onOpen()
        updateSize(force = true)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (!::adapter.isInitialized) return
        updateSize(force = false)
        rebuildIfNeeded()
        val tree = domTree ?: return
        if (needsLayout) {
            tree.render(adapter, lastWidth, lastHeight)
            needsLayout = false
        }
        val commands = tree.paint(adapter)
        val prevX = if (lastMoveX == Int.MIN_VALUE) mouseX else lastMoveX
        val prevY = if (lastMoveY == Int.MIN_VALUE) mouseY else lastMoveY
        val dx = mouseX - prevX
        val dy = mouseY - prevY
        updateHover(tree.root, hoverChain, mouseX, mouseY, dx, dy)
        hoverTarget = hoverChain.lastOrNull()
        if (dx != 0 || dy != 0) {
            val moveEvent = MouseMoveEvent(mouseX, mouseY, prevX, prevY)
            moveEvent.target = hoverTarget
            EventBus.post(moveEvent)
        }
        lastMoveX = mouseX
        lastMoveY = mouseY
        adapter.paint(commands)
        flushPendingCleanup()
        super.drawScreen(mouseX, mouseY, partialTicks)
        println("Re-renders: ${rendersCount}, re-paints: ${adapter.paintsCount}")
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        super.keyTyped(typedChar, keyCode)
        window.onKeyTyped(typedChar, keyCode)
    }

    override fun onGuiClosed() {
        flushPendingCleanup()
        domTree?.root?.let { root ->
            EventBus.run { root.clearListenersDeep() }
        }
        hoverChain.clear()
        hoverTarget = null
        FocusManager.clearFocus()
        window.onClose()
        super.onGuiClosed()
    }

    override fun doesGuiPauseGame(): Boolean = false

    override fun requestRebuild(reason: String?) {
        needsRender = true
    }

    override fun requestRedraw() {
    }

    override fun getViewport(): Viewport {
        val scale = adapter.scaledResolution().scaleFactor.toFloat()
        return Viewport(lastWidth, lastHeight, scale)
    }

    private fun updateSize(force: Boolean) {
        val scaled = adapter.scaledResolution()
        val width = scaled.scaledWidth
        val height = scaled.scaledHeight
        if (force || width != lastWidth || height != lastHeight) {
            lastWidth = width
            lastHeight = height
            needsLayout = true
            needsRender = true
            window.onResize(width, height)
        }
    }

    private fun rebuildIfNeeded() {
        if (needsRender || domTree == null) {
            rendersCount++
            domTree?.root?.let { root -> pendingCleanupRoot = root }
            domTree = window.render()
            needsRender = false
            needsLayout = true
            domTree?.root?.let { root -> FocusManager.retainFocus(root) }
        }
    }

    override fun handleKeyboardInput() {
        super.handleKeyboardInput()
        val keyCode = Keyboard.getEventKey()
        val keyChar = Keyboard.getEventCharacter()
        if (Keyboard.getEventKeyState()) {
            if (pressedKeys.add(keyCode)) {
                EventBus.post(KeyboardKeyDownEvent(keyChar, keyCode))
            }
        } else {
            if (pressedKeys.remove(keyCode)) {
                EventBus.post(KeyboardKeyUpEvent(keyChar, keyCode))
            }
        }

        mc.dispatchKeypresses()
    }

    override fun handleMouseInput() {
        val mouseX = Mouse.getEventX() * width / mc.displayWidth
        val mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1
        val dWheel = Mouse.getDWheel()
        val mouseButton = Mouse.getEventButton()

        if (mouseButton > 2) return

        if (Mouse.getEventButtonState()) {
            eventButton = mouseButton
            lastMouseEvent = net.minecraft.client.Minecraft.getSystemTime()
            mapButton(mouseButton)?.let {
                val event = MouseDownEvent(mouseX, mouseY, it)
                event.target = hoverTarget
                EventBus.post(event)
            }
        } else if (mouseButton != -1) {
            eventButton = -1
            mapButton(mouseButton)?.let {
                val upEvent = MouseUpEvent(mouseX, mouseY, it)
                upEvent.target = hoverTarget
                EventBus.post(upEvent)
                val clickEvent = MouseClickEvent(mouseX, mouseY, it)
                clickEvent.target = hoverTarget
                EventBus.post(clickEvent)
            }
        } else if (eventButton != -1 && lastMouseEvent > 0L) {
            mapButton(eventButton)?.let {
                val dx = mouseX - lastMouseX
                val dy = mouseY - lastMouseY
                if (dx != 0 || dy != 0) {
                    val dragEvent = MouseDragEvent(
                        lastMouseX,
                        lastMouseY,
                        dx,
                        dy,
                        it
                    )
                    dragEvent.target = hoverTarget
                    EventBus.post(dragEvent)
                }
            }
        }

        if (dWheel != 0) {
            val wheelEvent = MouseWheelEvent(mouseX, mouseY, dWheel)
            wheelEvent.target = hoverTarget
            EventBus.post(wheelEvent)
        }

        lastMouseX = mouseX
        lastMouseY = mouseY
    }

    private fun mapButton(button: Int): MouseButton? {
        return when (button) {
            0 -> MouseButton.LEFT
            1 -> MouseButton.RIGHT
            2 -> MouseButton.MIDDLE
            else -> null
        }
    }

    private fun flushPendingCleanup() {
        pendingCleanupRoot?.let { root ->
            EventBus.run { root.clearListenersDeep() }
            pendingCleanupRoot = null
        }
    }
}
