package org.dreamfinity.dsgl.mcForge1122

import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.dreamfinity.dsgl.mcForge1122.demo.ShowcaseWindow
import org.lwjgl.input.Keyboard

/**
 * Client-only hotkeys for DSGL.
 */
object DsglClientHotkeys {
    private val openShowcaseKey = KeyBinding(
        "key.dsgl.open_showcase",
        Keyboard.KEY_J,
        "key.categories.dsgl"
    )
    private var registered: Boolean = false

    fun register() {
        if (registered) return
        ClientRegistry.registerKeyBinding(openShowcaseKey)
        MinecraftForge.EVENT_BUS.register(this)
        registered = true
    }

    @SubscribeEvent
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        when {
            openShowcaseKey.isPressed -> Minecraft
                .getMinecraft()
                .displayGuiScreen(object : DsglScreenHost({ ShowcaseWindow() }) {})
        }
    }
}
