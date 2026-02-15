package org.dreamfinity.dsgl.mc1710

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.gui.GuiScreen
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.RangeInputNode
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
    private var dragCaptureTarget: DOMNode? = null
    private var dragCaptureKey: Any? = null
    private var dragCaptureClass: Class<out DOMNode>? = null
    private var dragCaptureFocusKey: Any? = null
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
        if (dragCaptureTarget != null && hasFocusChangedSinceCapture()) {
            releaseDragCapture()
        }
        if (dx != 0 || dy != 0) {
            val moveEvent = MouseMoveEvent(mouseX, mouseY, prevX, prevY)
            moveEvent.target = dragCaptureTarget ?: hoverTarget
            EventBus.post(moveEvent)
        }
        lastMoveX = mouseX
        lastMoveY = mouseY
        adapter.paint(commands)
        flushPendingCleanup()
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        super.keyTyped(typedChar, keyCode)
        window.onKeyTyped(typedChar, keyCode)
    }

    override fun onGuiClosed() {
        FocusManager.clearFocus()
        flushPendingCleanup()
        domTree?.root?.let { root ->
            EventBus.run { root.clearListenersDeep() }
        }
        hoverChain.clear()
        hoverTarget = null
        releaseDragCapture()
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
            domTree?.root?.let { root ->
                FocusManager.retainFocus(root)
                restoreDragCapture(root)
            }
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
        updateSize(force = false)
        rebuildIfNeeded()
        domTree?.let { tree ->
            if (needsLayout) {
                tree.render(adapter, lastWidth, lastHeight)
                needsLayout = false
            }
        }

        val mouseX = Mouse.getEventX() * width / mc.displayWidth
        val mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1
        val dWheel = Mouse.getDWheel()
        val mouseButton = Mouse.getEventButton()

        refreshHoverTarget(mouseX, mouseY)

        if (mouseButton > 2) return

        if (Mouse.getEventButtonState()) {
            eventButton = mouseButton
            lastMouseEvent = net.minecraft.client.Minecraft.getSystemTime()
            mapButton(mouseButton)?.let { mappedButton ->
                val event = MouseDownEvent(mouseX, mouseY, mappedButton)
                event.target = hoverTarget
                EventBus.post(event)
                if (mappedButton == MouseButton.LEFT) {
                    val captureTarget = resolveDragCaptureTarget(event.target ?: hoverTarget)
                    if (captureTarget != null) {
                        setDragCapture(captureTarget)
                    } else if (dragCaptureTarget != null) {
                        releaseDragCapture()
                    }
                }
            }
        } else if (mouseButton != -1) {
            val releaseTarget = dragCaptureTarget ?: hoverTarget
            val hadDragCapture = dragCaptureTarget != null
            eventButton = -1
            mapButton(mouseButton)?.let {
                val upEvent = MouseUpEvent(mouseX, mouseY, it)
                upEvent.target = releaseTarget
                EventBus.post(upEvent)
                if (!hadDragCapture) {
                    val clickEvent = MouseClickEvent(mouseX, mouseY, it)
                    clickEvent.target = hoverTarget
                    EventBus.post(clickEvent)
                }
            }
            releaseDragCapture()
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
                    dragEvent.target = dragCaptureTarget ?: hoverTarget
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

    private fun setDragCapture(target: DOMNode) {
        dragCaptureTarget = target
        dragCaptureKey = target.key
        dragCaptureClass = target.javaClass
        dragCaptureFocusKey = FocusManager.focusedNode()?.key
    }

    private fun releaseDragCapture() {
        RangeInputNode.clearActiveDrag()
        dragCaptureTarget = null
        dragCaptureKey = null
        dragCaptureClass = null
        dragCaptureFocusKey = null
    }

    private fun resolveDragCaptureTarget(start: DOMNode?): DOMNode? {
        var current = start
        while (current != null) {
            if (current is RangeInputNode) return current
            current = current.parent
        }
        return null
    }

    private fun restoreDragCapture(root: DOMNode) {
        if (dragCaptureTarget == null) return
        val key = dragCaptureKey
        val cls = dragCaptureClass
        if (key == null || cls == null) {
            releaseDragCapture()
            return
        }

        val restored = findByKeyAndClass(root, key, cls)
        if (restored != null) {
            dragCaptureTarget = restored
        } else {
            releaseDragCapture()
        }
    }

    private fun findByKeyAndClass(
        node: DOMNode,
        key: Any,
        cls: Class<out DOMNode>
    ): DOMNode? {
        if (node.key == key && node.javaClass == cls) return node
        node.children.forEach { child ->
            val found = findByKeyAndClass(child, key, cls)
            if (found != null) return found
        }
        return null
    }

    private fun hasFocusChangedSinceCapture(): Boolean {
        if (dragCaptureFocusKey == null) return false
        val currentFocusKey = FocusManager.focusedNode()?.key
        return currentFocusKey != dragCaptureFocusKey
    }

    private fun refreshHoverTarget(mouseX: Int, mouseY: Int) {
        val tree = domTree ?: return
        if (needsLayout) {
            tree.render(adapter, lastWidth, lastHeight)
            needsLayout = false
        }
        val chain = collectHoverChain(tree.root, mouseX, mouseY)
        hoverTarget = chain.lastOrNull()
    }
}
