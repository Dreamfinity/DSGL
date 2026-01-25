package org.dreamfinity.dsgl.mc1710.demo

import net.minecraft.client.Minecraft
import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.DynamicTextProps
import org.dreamfinity.dsgl.core.InputProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.ui
import org.dreamfinity.dsgl.mc1710.DsglScreenHost

/**
 * Minimal, end-to-end DSGL demo for Minecraft 1.7.10.
 */
class DemoWindow : DsglWindow() {
    private var counter by state(0)
    private var status by state("Ready")
    private var highlighted by state(false)

    override fun render(): DomTree = ui {
        column(
            ComponentProps(
                padding = 8,
                gap = 6,
                backgroundColor = DsglColors.PANEL,
                onKeyDown = { event ->
                    if (event.keyCode == KeyCodes.ENTER) {
                        status = "Enter pressed"
                    }
                }
            )
        ) {
            text(TextProps("DSGL Demo").apply { color = DsglColors.WHITE })
            dynamicText(DynamicTextProps({ "Clicks: $counter | $status" }))
            row(ComponentProps(gap = 6)) {
                button(
                    ButtonProps("Add").apply {
                        onMouseClick = { counter += 1 }
                    }
                )
                button(
                    ButtonProps("Reset").apply {
                        onMouseClick = {
                            counter = 0
                            status = "Reset"
                        }
                    }
                )
            }
            div(
                ComponentProps(
                    padding = 4,
                    backgroundColor = if (highlighted) DsglColors.BUTTON else DsglColors.PANEL,
                    onMouseEnter = { highlighted = true },
                    onMouseLeave = { highlighted = false }
                )
            ) {
                text(TextProps("Hover to highlight"))
            }
            input(InputProps(InputType.Text(placeholder = "Type here...")))
        }
    }
}

/**
 * Screen host wrapper for [DemoWindow].
 */
class DemoScreen : DsglScreenHost(DemoWindow()) {
    companion object {
        /** Opens the demo screen on the client. */
        fun open() {
            Minecraft.getMinecraft().displayGuiScreen(DemoScreen())
        }
    }
}
