package org.dreamfinity.dsgl.mcForge1710

import org.dreamfinity.dsgl.core.dom.layout.AffineTransform2D
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderCommandTransformStackCompositionTests {
    @Test
    fun `closed-form push matches chained matrix composition`() {
        val cases =
            listOf(
                transformCommand(),
                transformCommand(originX = 10f, originY = 20f, translateX = 5f, translateY = -3f),
                transformCommand(originX = 50f, originY = 25f, scaleX = 2f, scaleY = 0.5f),
                transformCommand(originX = 12f, originY = 34f, rotateDeg = 45f),
                transformCommand(
                    originX = 100f,
                    originY = 60f,
                    translateX = -8f,
                    translateY = 4f,
                    scaleX = 1.5f,
                    scaleY = 0.75f,
                    rotateDeg = 30f,
                ),
                transformCommand(
                    originX = -16f,
                    originY = 8f,
                    translateX = 3.5f,
                    translateY = 7.25f,
                    scaleX = 0.25f,
                    scaleY = 3f,
                    rotateDeg = -120f,
                ),
                transformCommand(translateX = 1f, translateY = 2f, scaleX = -1f, rotateDeg = 270f),
            )
        cases.forEach { command ->
            val stack = RenderCommandTransformStack()
            stack.push(command)
            assertMatrixNearlyEquals(referenceTransform(command), stack.currentTransform(), command)
        }
    }

    @Test
    fun `pop restores the previous transform`() {
        val stack = RenderCommandTransformStack()
        val outer = transformCommand(originX = 5f, originY = 5f, translateX = 2f, scaleX = 2f, scaleY = 2f)
        val inner = transformCommand(rotateDeg = 90f)
        stack.push(outer)
        val outerTransform = stack.currentTransform()
        stack.push(inner)
        stack.pop()
        assertEquals(outerTransform, stack.currentTransform())
        stack.pop()
        assertEquals(AffineTransform2D.IDENTITY, stack.currentTransform())
    }

    @Test
    fun `resolveClipRect under rotation matches transformed bounding box`() {
        val stack = RenderCommandTransformStack()
        stack.push(transformCommand(rotateDeg = 90f))
        val clip = stack.resolveClipRect(10, 0, 20, 10)
        assertEquals(-10, clip.x)
        assertEquals(10, clip.y)
        assertEquals(10, clip.width)
        assertEquals(20, clip.height)
    }

    private fun transformCommand(
        originX: Float = 0f,
        originY: Float = 0f,
        translateX: Float = 0f,
        translateY: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        rotateDeg: Float = 0f,
    ): RenderCommand.PushTransform =
        RenderCommand.PushTransform(
            originX = originX,
            originY = originY,
            translateX = translateX,
            translateY = translateY,
            scaleX = scaleX,
            scaleY = scaleY,
            rotateDeg = rotateDeg,
        )

    private fun referenceTransform(command: RenderCommand.PushTransform): AffineTransform2D =
        AffineTransform2D
            .translation(command.originX, command.originY)
            .times(AffineTransform2D.translation(command.translateX, command.translateY))
            .times(AffineTransform2D.rotation(command.rotateDeg))
            .times(AffineTransform2D.scale(command.scaleX, command.scaleY))
            .times(AffineTransform2D.translation(-command.originX, -command.originY))

    private fun assertMatrixNearlyEquals(expected: AffineTransform2D, actual: AffineTransform2D, command: RenderCommand.PushTransform) {
        assertNearlyEqual(expected.a, actual.a, "a", command)
        assertNearlyEqual(expected.b, actual.b, "b", command)
        assertNearlyEqual(expected.c, actual.c, "c", command)
        assertNearlyEqual(expected.d, actual.d, "d", command)
        assertNearlyEqual(expected.tx, actual.tx, "tx", command)
        assertNearlyEqual(expected.ty, actual.ty, "ty", command)
    }

    private fun assertNearlyEqual(
        expected: Float,
        actual: Float,
        field: String,
        command: RenderCommand.PushTransform,
    ) {
        assertTrue(
            abs(expected - actual) <= 1e-3f,
            "Field $field diverged for $command: expected $expected, actual $actual",
        )
    }
}
