package org.dreamfinity.dsgl.mcForge1710

import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.event.FMLInitializationEvent
import net.minecraft.client.Minecraft

/**
 * Minimal Forge mod container so FML can discover and load the DSGL MC 1.7.10 adapter module.
 */
@Mod(
    modid = DsglMc1710DemoGeneratedMetadata.MOD_ID,
    name = DsglMc1710DemoGeneratedMetadata.MOD_NAME,
    version = DsglMc1710DemoGeneratedMetadata.MOD_VERSION,
    acceptedMinecraftVersions = DsglMc1710DemoGeneratedMetadata.MC_VERSION_RANGE,
    useMetadata = true
)
class DsglMc1710ModContainer {
    @Mod.EventHandler
    fun onInit(event: FMLInitializationEvent) {
        if (FMLCommonHandler.instance().side.isClient) {
            DsglFonts.ensureInitialized(Minecraft.getMinecraft().mcDataDir, javaClass.classLoader)
            DsglClientHotkeys.register()
        }
    }
}
