package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.collectHoverChain
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DemoSection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PositionedLayoutStickyDemoIntegrationTests {
    private val width = 1024
    private val height = 720

    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `positioned layout section wires one cohesive sticky demo surface`() {
        val fixture = renderFixture()
        val root = fixture.tree.root

        assertNotNull(findByKey(root, "positioned.sticky.surface"))
        assertNotNull(findByKey(root, "positioned.sticky.vertical.group"))
        assertNotNull(findByKey(root, "positioned.sticky.horizontal.group"))
        assertNotNull(findByKey(root, "positioned.sticky.xy.group"))
        assertNotNull(findByKey(root, "positioned.sticky.inactive.group"))
        assertNotNull(findByKey(root, "positioned.sticky.clamp.group"))
    }

    @Test
    fun `sticky showcase examples keep vertical horizontal and combined behavior live`() {
        val fixture = renderFixture()
        scrollMainSectionToSticky(fixture)

        val topScroller = requireContainer(fixture.tree.root, "positioned.sticky.vertical.top.scroller")
        val topTarget = requireNode(fixture.tree.root, "positioned.sticky.vertical.top.target")
        topScroller.setScrollOffsets(0, 80)

        val leftScroller = requireContainer(fixture.tree.root, "positioned.sticky.horizontal.left.scroller")
        val leftTarget = requireNode(fixture.tree.root, "positioned.sticky.horizontal.left.target")
        leftScroller.setScrollOffsets(90, 0)

        val xyScroller = requireContainer(fixture.tree.root, "positioned.sticky.xy.scroller")
        val xyTarget = requireNode(fixture.tree.root, "positioned.sticky.xy.target")
        xyScroller.setScrollOffsets(70, 60)

        fixture.tree.render(ctx, width, height)

        val topViewport = topScroller.scrollContainerState().viewportRect
        assertEquals(topViewport.y, transformedRect(topTarget).y)

        val leftViewport = leftScroller.scrollContainerState().viewportRect
        assertEquals(leftViewport.x, transformedRect(leftTarget).x)

        val xyViewport = xyScroller.scrollContainerState().viewportRect
        val xyRect = transformedRect(xyTarget)
        assertEquals(xyViewport.x, xyRect.x)
        assertEquals(xyViewport.y, xyRect.y)
    }

    @Test
    fun `sticky showcase interaction path resolves sticky final geometry`() {
        val fixture = renderFixture()
        scrollMainSectionToSticky(fixture)

        val topScroller = requireContainer(fixture.tree.root, "positioned.sticky.vertical.top.scroller")
        val topTarget = requireNode(fixture.tree.root, "positioned.sticky.vertical.top.target")
        val xyTarget = requireNode(fixture.tree.root, "positioned.sticky.xy.target")
        val topRect = transformedRect(topTarget)
        val topViewport = topScroller.scrollContainerState().viewportRect
        assertTrue(
            intersects(topRect, topViewport),
            "Sticky top target must remain visible in its scroller viewport; targetRect=$topRect viewport=$topViewport"
        )

        val topPoint = findPointInsideTarget(fixture.tree.root, topTarget, topRect)
        val topCenterX = topRect.x + topRect.width / 2
        val topCenterY = topRect.y + topRect.height / 2
        val topCenterWinner = hoverWinnerKey(fixture.tree.root, topCenterX, topCenterY)
        assertNotNull(
            topPoint,
            "Expected a hover-resolvable point inside sticky top target. " +
                "targetRect=$topRect viewport=$topViewport centerWinner=$topCenterWinner"
        )
        assertEquals(topTarget, collectHoverChain(fixture.tree.root, topPoint.first, topPoint.second).lastOrNull())

        val xyPoint = findPointInsideTarget(fixture.tree.root, xyTarget, transformedRect(xyTarget))
        assertNotNull(xyPoint, "Expected a hover-resolvable point inside sticky combined target")
        assertEquals(xyTarget, collectHoverChain(fixture.tree.root, xyPoint.first, xyPoint.second).lastOrNull())
    }

    @Test
    fun `sticky showcase inspector uses same pick and highlight geometry`() {
        val fixture = renderFixture()
        scrollMainSectionToSticky(fixture)

        val xyTarget = requireNode(fixture.tree.root, "positioned.sticky.xy.target")
        val xyRect = transformedRect(xyTarget)
        val xyPoint = findPointInsideTarget(fixture.tree.root, xyTarget, xyRect)
        assertNotNull(xyPoint, "Expected a point that resolves to sticky x+y target before inspector probe")

        val expectedPicked = collectHoverChain(fixture.tree.root, xyPoint.first, xyPoint.second).lastOrNull()
        assertNotNull(expectedPicked)

        val inspector = InspectorController().also { it.toggle() }
        inspector.onLayoutCommitted(fixture.tree.root, 1L)
        invokeInspectorInternalByName(
            inspector,
            "onNativeDomExpandedPanelRect",
            Rect(700, 30, 280, 260),
            width,
            height
        )
        inspector.onCursorMoved(xyPoint.first, xyPoint.second)
        invokeInspectorInternalByName(inspector, "buildDomSnapshot", width, height)

        assertEquals(expectedPicked.key?.toString(), inspector.hoveredKey)

        val highlightRect = inspectorHoveredBorderRect(inspector)
        assertEquals(transformedRect(expectedPicked), highlightRect)
    }

    private data class Fixture(
        val window: ShowcaseWindow,
        val tree: DomTree
    )

    private fun renderFixture(): Fixture {
        val window = ShowcaseWindow()
        window.onResize(width, height)
        window.selectedSection = DemoSection.POSITIONED_LAYOUT
        val tree = window.render()
        tree.render(ctx, width, height)
        return Fixture(window = window, tree = tree)
    }

    private fun scrollMainSectionToSticky(fixture: Fixture) {
        val sectionScroller = requireContainer(fixture.tree.root, "section.positionedLayout")
        val stickySurface = requireNode(fixture.tree.root, "positioned.sticky.surface")
        val controls = findByKey(fixture.tree.root, "positioned.controls")
        val viewport = sectionScroller.scrollContainerState().viewportRect
        val stickyRect = transformedRect(stickySurface)
        val controlsHeight = controls?.let { transformedRect(it).height } ?: 0
        val desiredStickySurfaceTopY = viewport.y + controlsHeight + 8
        val targetScrollY = (stickyRect.y - desiredStickySurfaceTopY).coerceAtLeast(0)
        sectionScroller.setScrollOffsets(0, targetScrollY)
        fixture.tree.render(ctx, width, height)
    }

    private fun requireContainer(root: DOMNode, key: String): ContainerNode {
        return requireNode(root, key) as? ContainerNode
            ?: error("Expected container with key '$key'")
    }

    private fun requireNode(root: DOMNode, key: String): DOMNode {
        return findByKey(root, key) ?: error("Node with key '$key' not found")
    }

    private fun findByKey(root: DOMNode, key: String): DOMNode? {
        if (root.key?.toString() == key) return root
        root.children.forEach { child ->
            val match = findByKey(child, key)
            if (match != null) return match
        }
        return null
    }

    private fun transformedRect(node: DOMNode): Rect {
        val world = node.worldTransformMatrix()
        val b = node.bounds
        val p1 = world.transform(b.x.toFloat(), b.y.toFloat())
        val p2 = world.transform((b.x + b.width).toFloat(), b.y.toFloat())
        val p3 = world.transform(b.x.toFloat(), (b.y + b.height).toFloat())
        val p4 = world.transform((b.x + b.width).toFloat(), (b.y + b.height).toFloat())
        val minX = minOf(p1.first, p2.first, p3.first, p4.first)
        val maxX = maxOf(p1.first, p2.first, p3.first, p4.first)
        val minY = minOf(p1.second, p2.second, p3.second, p4.second)
        val maxY = maxOf(p1.second, p2.second, p3.second, p4.second)
        val x = floor(minX.toDouble()).toInt()
        val y = floor(minY.toDouble()).toInt()
        val w = ceil((maxX - minX).toDouble()).toInt().coerceAtLeast(0)
        val h = ceil((maxY - minY).toDouble()).toInt().coerceAtLeast(0)
        return Rect(x, y, w, h)
    }

    private fun findPointInsideTarget(root: DOMNode, target: DOMNode, rect: Rect): Pair<Int, Int>? {
        if (rect.width <= 0 || rect.height <= 0) return null
        val stepX = max(1, rect.width / 8)
        val stepY = max(1, rect.height / 8)
        var y = rect.y + 1
        while (y < rect.y + rect.height - 1) {
            var x = rect.x + 1
            while (x < rect.x + rect.width - 1) {
                if (collectHoverChain(root, x, y).lastOrNull() === target) {
                    return x to y
                }
                x += stepX
            }
            y += stepY
        }
        val centerX = rect.x + rect.width / 2
        val centerY = rect.y + rect.height / 2
        return if (collectHoverChain(root, centerX, centerY).lastOrNull() === target) {
            centerX to centerY
        } else {
            null
        }
    }

    private fun hoverWinnerKey(root: DOMNode, x: Int, y: Int): String? {
        return collectHoverChain(root, x, y).lastOrNull()?.key?.toString()
    }

    private fun intersects(a: Rect, b: Rect): Boolean {
        val noOverlapX = a.x + a.width <= b.x || b.x + b.width <= a.x
        val noOverlapY = a.y + a.height <= b.y || b.y + b.height <= a.y
        return !noOverlapX && !noOverlapY
    }

    private fun inspectorHoveredBorderRect(inspector: InspectorController): Rect? {
        val debugHoveredHighlight = findMethodByNameAndArity(inspector.javaClass, "debugHoveredHighlight", 0)
        debugHoveredHighlight.isAccessible = true
        val snapshot = debugHoveredHighlight.invoke(inspector) ?: return null
        val borderRectField = findField(snapshot.javaClass, "borderRect")
        borderRectField.isAccessible = true
        return borderRectField.get(snapshot) as? Rect
    }

    private fun invokeInspectorInternalByName(
        inspector: InspectorController,
        methodName: String,
        vararg args: Any?
    ): Any? {
        val method = findMethodByNameAndArity(inspector.javaClass, methodName, args.size)
        method.isAccessible = true
        return method.invoke(inspector, *args)
    }

    private fun findField(clazz: Class<*>, fieldName: String): java.lang.reflect.Field {
        var current: Class<*>? = clazz
        while (current != null) {
            val field = current.declaredFields.firstOrNull { it.name == fieldName }
            if (field != null) return field
            current = current.superclass
        }
        error("Field '$fieldName' not found on ${clazz.name}")
    }

    private fun findMethod(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>>
    ): java.lang.reflect.Method {
        var current: Class<*>? = clazz
        while (current != null) {
            val method = current.declaredMethods.firstOrNull {
                it.name == methodName && it.parameterTypes.contentEquals(parameterTypes)
            }
            if (method != null) return method
            current = current.superclass
        }
        error("Method '$methodName' not found on ${clazz.name}")
    }

    private fun findMethodByNameAndArity(
        clazz: Class<*>,
        methodName: String,
        arity: Int
    ): java.lang.reflect.Method {
        var current: Class<*>? = clazz
        while (current != null) {
            val method = current.declaredMethods.firstOrNull {
                (it.name == methodName || it.name.startsWith("$methodName$")) && it.parameterCount == arity
            }
            if (method != null) return method
            current = current.superclass
        }
        error("Method '$methodName/$arity' not found on ${clazz.name}")
    }
}



