package org.dreamfinity.dsgl.mc1710.scissorsHelper

import org.lwjgl.opengl.GL11
import java.util.*

object ScissorContext {
    val instance = ScissorContext
    val stack: Deque<ScissorsArea> = ArrayDeque()
    var scissorsEnabledByContext = false;

    fun push(x: Number, y: Number, width: Number, height: Number): ScissorsArea {
        if (stack.isEmpty()) {
            scissorsEnabledByContext = !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
            if (scissorsEnabledByContext) GL11.glEnable(GL11.GL_SCISSOR_TEST)
        }
        val scissorsArea =
            ScissorsArea(x.toInt(), y.toInt(), width.toInt(), height.toInt()) intersectionWith stack.peekFirst()
        stack.push(scissorsArea)
        GL11.glScissor(scissorsArea.x, scissorsArea.y, scissorsArea.width, scissorsArea.height)
        return scissorsArea
    }

    fun pop(): ScissorsArea? {
        return if (!stack.isEmpty()) {
            val previousScissorsArea = stack.pop()
            GL11.glScissor(
                previousScissorsArea.x,
                previousScissorsArea.y,
                previousScissorsArea.width,
                previousScissorsArea.height
            )
            previousScissorsArea
        } else {
            if (scissorsEnabledByContext) GL11.glDisable(GL11.GL_SCISSOR_TEST)
            scissorsEnabledByContext = false
            null
        }
    }
}
