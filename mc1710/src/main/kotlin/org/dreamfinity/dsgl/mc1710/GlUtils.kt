package org.dreamfinity.dsgl.mc1710

import net.minecraft.client.renderer.RenderHelper
import org.lwjgl.opengl.GL11

inline fun withStack(
    attributes: List<Int> = emptyList(),
    block: () -> Unit
) {
    val hasAttributes = attributes.isNotEmpty()
    if (hasAttributes) {
        var mask = 0
        for (attr in attributes) {
            mask = mask or attr
        }
        GL11.glPushAttrib(mask)
    }
    GL11.glPushMatrix()
    try {
        block()
    } finally {
        GL11.glPopMatrix()
        if (hasAttributes) {
            GL11.glPopAttrib()
        }
    }
}

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

inline fun withItemGuiLightning(block: () -> Unit) {
    RenderHelper.enableGUIStandardItemLighting()
    try {
        block()
    } finally {
        RenderHelper.disableStandardItemLighting()
    }
}
