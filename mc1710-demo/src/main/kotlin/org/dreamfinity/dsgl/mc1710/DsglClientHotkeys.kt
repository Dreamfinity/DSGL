package org.dreamfinity.dsgl.mc1710

import cpw.mods.fml.client.registry.ClientRegistry
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.InputEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft
import org.dreamfinity.dsgl.mc1710.demo.DemoScreen
import org.lwjgl.input.Keyboard

/**
 * Client-only hotkeys for DSGL.
 */
@SideOnly(Side.CLIENT)
object DsglClientHotkeys {
    private val openShowcaseKey = net.minecraft.client.settings.KeyBinding(
        "key.dsgl.open_showcase",
        Keyboard.KEY_J,
        "key.categories.dsgl"
    )
    private var registered: Boolean = false

    fun register() {
        if (registered) return
        ClientRegistry.registerKeyBinding(openShowcaseKey)
        FMLCommonHandler.instance().bus().register(this)
        registered = true
    }

    @SubscribeEvent
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        if (!openShowcaseKey.isPressed) return
        val mc = Minecraft.getMinecraft()
        if (mc.currentScreen !is DemoScreen) {
            DemoScreen.open()
        }
    }
}
