package org.dreamfinity.dsgl.mc1710.demo

import net.minecraft.client.Minecraft
import org.dreamfinity.dsgl.mc1710.DsglScreenHost

/**
 * Screen host wrapper for the full DSGL showcase window.
 */
class DemoScreen : DsglScreenHost(ShowcaseWindow()) {
    companion object {
        /** Opens the demo screen on the client. */
        fun open() {
            Minecraft.getMinecraft().displayGuiScreen(DemoScreen())
        }
    }
}
