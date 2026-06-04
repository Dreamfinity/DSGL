package org.dreamfinity.dsgl.mcForge1122

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
    GL11.glPushMatrix()
    if (attributesBitMask != 0) {
        GL11.glPushAttrib(attributesBitMask)
    }
    try {
        block()
    } finally {
        if (attributesBitMask != 0) {
            GL11.glPopAttrib()
        }
        GL11.glPopMatrix()
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
    val wasDisabled = mutableListOf<Int>()
    for (capability in enable) {
        if(!GL11.glIsEnabled(capability)) {
            GL11.glEnable(capability)
            wasDisabled += capability
        }
    }

    val wasEnabled = mutableListOf<Int>()
    for (capability in disable) {
        if(GL11.glIsEnabled(capability)) {
            GL11.glDisable(capability)
            wasEnabled += capability
        }
    }
    try {
        block()
    } finally {
        for (capability in wasDisabled) {
            GL11.glDisable(capability)
        }
        for (capability in wasEnabled) {
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
