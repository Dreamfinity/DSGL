package org.dreamfinity.dsgl.mc1710

import net.minecraft.client.renderer.RenderHelper
import org.lwjgl.opengl.GL11

/**
 * Executes a block with an OpenGL matrix/attribute stack push/pop.
 */
inline fun withStack(
    attributes: List<Int>,
    block: () -> Unit
) {
    val attributesBitMask = attributes.reduce { a, b -> a or b }
    withStack(attributesBitMask) { block() }
}

/**
 * Executes a block with an OpenGL matrix/attribute stack push/pop.
 */
inline fun withStack(
    attributesBitMask: Int = 0,
    block: () -> Unit
) {
    if (attributesBitMask > 0) {
        GL11.GL_MAX_TEXTURE_SIZE
        GL11.glPushAttrib(attributesBitMask)
    }
    GL11.glPushMatrix()
    try {
        block()
    } finally {
        GL11.glPopMatrix()
        if (attributesBitMask > 0) {
            GL11.glPopAttrib()
        }
    }
}

/**
 * Enables and disables GL capabilities for the duration of [block].
 */
inline fun withAttributes(
    enable: List<Int> = emptyList(),
    disable: List<Int> = emptyList(),
    block: () -> Unit
) {
    for (capability in enable) {
        GL11.glEnable(capability)
    }
    for (capability in disable) {
        GL11.glDisable(capability)
    }
    try {
        block()
    } finally {
        for (capability in enable) {
            GL11.glDisable(capability)
        }
        for (capability in disable) {
            GL11.glEnable(capability)
        }
    }
}

/**
 * Executes a block with an OpenGL matrix/attribute stack push/pop.
 */
inline fun withAttributes(
    enableBitMask: Int? = null,
    disableBitMask: Int? = null,
    block: () -> Unit
) {
    enableBitMask?.let { GL11.glEnable(it) }
    disableBitMask?.let { GL11.glDisable(it) }
    try {
        block()
    } finally {
        enableBitMask?.let { GL11.glDisable(it) }
        disableBitMask?.let { GL11.glEnable(it) }
    }
}

/**
 * Runs [block] with standard item lighting enabled.
 */
inline fun withItemGuiLightning(block: () -> Unit) {
    RenderHelper.enableGUIStandardItemLighting()
    try {
        block()
    } finally {
        RenderHelper.disableStandardItemLighting()
    }
}
