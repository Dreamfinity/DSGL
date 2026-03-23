package org.dreamfinity.dsgl.mc1710

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.collectHoverChain
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleDeclarations
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DsglScreenHostUsedGeometryTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @Test
    fun `app host hover and click target match core for absolute outside ancestor bounds`() {
        val fixture = createAbsoluteOutsideAncestorFixture()
        fixture.tree.render(ctx, width = 260, height = 140)
        val host = createHostWithTree(fixture.tree)

        refreshHoverTarget(host, 105, 10)

        val hostHover = hoverTarget(host)
        val hostClickTarget = resolveClickTarget(host)
        val coreHover = collectHoverChain(fixture.root, 105, 10).lastOrNull()

        assertSame(fixture.child, hostHover)
        assertSame(fixture.child, hostClickTarget)
        assertSame(coreHover, hostClickTarget)
    }

    @Test
    fun `app host context menu pointer-down target matches core ordering for overlap`() {
        val fixture = createPositionedOverlapFixture()
        fixture.tree.render(ctx, width = 220, height = 140)
        val host = createHostWithTree(fixture.tree)

        refreshHoverTarget(host, 10, 10)

        val pointerDownTarget = resolvePointerDownTarget(host)
        val coreHover = collectHoverChain(fixture.root, 10, 10).lastOrNull()

        assertSame(fixture.fixed, pointerDownTarget)
        assertSame(coreHover, pointerDownTarget)
    }

    @Test
    fun `core app-host and inspector agree on positioned overlap target`() {
        val fixture = createPositionedOverlapFixture()
        fixture.tree.render(ctx, width = 220, height = 140)
        val host = createHostWithTree(fixture.tree)
        val inspector = InspectorController().also { it.toggle() }
        inspector.onLayoutCommitted(fixture.root, 1L)

        refreshHoverTarget(host, 10, 10)
        inspector.onCursorMoved(10, 10)

        val coreHover = collectHoverChain(fixture.root, 10, 10).lastOrNull()
        val hostHover = hoverTarget(host)
        val inspectorHoverKey = inspector.hoveredKey

        assertSame(coreHover, hostHover)
        assertEquals(coreHover?.key?.toString(), inspectorHoverKey)
    }

    @Test
    fun `app host preserves fixed root clipping and non-fixed ancestor overflow clipping`() {
        val fixture = createClipSemanticsFixture()
        fixture.tree.render(ctx, width = 200, height = 120)
        val host = createHostWithTree(fixture.tree)

        refreshHoverTarget(host, 185, 25)
        assertSame(fixture.fixed, hoverTarget(host))
        assertSame(collectHoverChain(fixture.root, 185, 25).lastOrNull(), hoverTarget(host))

        refreshHoverTarget(host, 145, 95)
        assertSame(fixture.root, hoverTarget(host))
        assertSame(collectHoverChain(fixture.root, 145, 95).lastOrNull(), hoverTarget(host))

        refreshHoverTarget(host, 225, 28)
        assertNull(hoverTarget(host))
        assertEquals(
            collectHoverChain(fixture.root, 225, 28).lastOrNull(),
            hoverTarget(host)
        )
    }

    private fun createHostWithTree(tree: DomTree): DsglScreenHost {
        val host = object : DsglScreenHost(object : DsglWindow() {
            override fun render(): DomTree {
                return tree
            }
        }) {}
        setPrivateField(host, "domTree", tree)
        setPrivateField(host, "needsLayout", false)
        return host
    }

    private fun refreshHoverTarget(host: DsglScreenHost, mouseX: Int, mouseY: Int) {
        invokePrivate(
            target = host,
            methodName = "refreshHoverTarget",
            parameterTypes = arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
            args = arrayOf(mouseX, mouseY)
        )
    }

    private fun hoverTarget(host: DsglScreenHost): org.dreamfinity.dsgl.core.dom.DOMNode? {
        return getPrivateField(host, "hoverTarget") as? org.dreamfinity.dsgl.core.dom.DOMNode
    }

    private fun resolvePointerDownTarget(host: DsglScreenHost): org.dreamfinity.dsgl.core.dom.DOMNode? {
        return invokePrivate(
            target = host,
            methodName = "resolvePointerDownTarget",
            parameterTypes = emptyArray(),
            args = emptyArray()
        ) as? org.dreamfinity.dsgl.core.dom.DOMNode
    }

    private fun resolveClickTarget(host: DsglScreenHost): org.dreamfinity.dsgl.core.dom.DOMNode? {
        return invokePrivate(
            target = host,
            methodName = "resolveClickTarget",
            parameterTypes = emptyArray(),
            args = emptyArray()
        ) as? org.dreamfinity.dsgl.core.dom.DOMNode
    }

    private fun getPrivateField(target: Any, fieldName: String): Any? {
        val field = findField(target.javaClass, fieldName)
        field.isAccessible = true
        return field.get(target)
    }

    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val field = findField(target.javaClass, fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun invokePrivate(
        target: Any,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        args: Array<Any?>
    ): Any? {
        val method = findMethod(target.javaClass, methodName, parameterTypes)
        method.isAccessible = true
        return method.invoke(target, *args)
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

    private data class AbsoluteOutsideAncestorFixture(
        val tree: DomTree,
        val root: ContainerNode,
        val child: ButtonNode
    )

    private fun createAbsoluteOutsideAncestorFixture(): AbsoluteOutsideAncestorFixture {
        val root = ContainerNode(key = "abs-root")
        val ancestor = ContainerNode(key = "abs-ancestor").apply {
            width = 40
            height = 40
            inlineStyleDeclarations = styleDeclarations(StyleProperty.POSITION to "relative")
        }.applyParent(root)
        val child = ButtonNode("abs-child", key = "abs-child").apply {
            width = 36
            height = 16
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "absolute",
                StyleProperty.LEFT to "100px",
                StyleProperty.TOP to "5px"
            )
        }.applyParent(ancestor)
        return AbsoluteOutsideAncestorFixture(DomTree(root), root, child)
    }

    private data class PositionedOverlapFixture(
        val tree: DomTree,
        val root: ContainerNode,
        val fixed: ButtonNode
    )

    private fun createPositionedOverlapFixture(): PositionedOverlapFixture {
        val root = ContainerNode(key = "root", stackLayout = true)
        val early = ContainerNode(key = "early", stackLayout = true).apply {
            width = 120
            height = 60
        }.applyParent(root)
        ContainerNode(key = "later-container", stackLayout = true).apply {
            width = 120
            height = 60
        }.apply {
            ButtonNode("later", key = "later").apply {
                width = 72
                height = 24
            }.applyParent(this)
        }.applyParent(root)
        val fixed = ButtonNode("fixed", key = "fixed").apply {
            width = 72
            height = 24
            zIndex = 9_999
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "8px",
                StyleProperty.TOP to "8px"
            )
        }.applyParent(early)
        return PositionedOverlapFixture(DomTree(root), root, fixed)
    }

    private data class ClipSemanticsFixture(
        val tree: DomTree,
        val root: ContainerNode,
        val fixed: ButtonNode
    )

    private fun createClipSemanticsFixture(): ClipSemanticsFixture {
        val root = ContainerNode(key = "clip-root", stackLayout = true)
        val overflowParent = ContainerNode(key = "clip-parent").apply {
            width = 80
            height = 40
            overflowY = Overflow.Hidden
            inlineStyleDeclarations = styleDeclarations(StyleProperty.POSITION to "relative")
        }.applyParent(root)
        val fixed = ButtonNode("fixed", key = "clip-fixed").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "180px",
                StyleProperty.TOP to "20px"
            )
        }.applyParent(overflowParent)
        ButtonNode("absolute", key = "clip-absolute").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "absolute",
                StyleProperty.LEFT to "140px",
                StyleProperty.TOP to "90px"
            )
        }.applyParent(overflowParent)
        return ClipSemanticsFixture(DomTree(root), root, fixed)
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations {
        return StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }
    }
}
