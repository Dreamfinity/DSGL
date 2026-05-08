package org.dreamfinity.dsgl.mcForge1122

import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent

/**
 * Minimal Forge mod container so FML can discover and load the DSGL MC 1.7.10 adapter module.
 */
@Mod(
    modid = DsglMc1122DemoGeneratedMetadata.MOD_ID,
    name = DsglMc1122DemoGeneratedMetadata.MOD_NAME,
    version = DsglMc1122DemoGeneratedMetadata.MOD_VERSION,
    acceptedMinecraftVersions = DsglMc1122DemoGeneratedMetadata.MC_VERSION_RANGE,
    useMetadata = true
)
class DsglMc1122ModContainer {
    @Mod.EventHandler
    fun onInit(event: FMLInitializationEvent) {
        if (FMLCommonHandler.instance().side.isClient) {
            DsglFonts.ensureInitialized(Minecraft.getMinecraft().gameDir, javaClass.classLoader)
            DsglClientHotkeys.register()
        }
    }
}
