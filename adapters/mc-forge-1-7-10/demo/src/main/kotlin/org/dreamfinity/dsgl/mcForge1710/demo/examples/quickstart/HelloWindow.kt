package org.dreamfinity.dsgl.mcForge1710.demo.examples.quickstart

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dsl.button
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.mcForge1710.demo.examples.containers.centeredFlexWrapper

class HelloWindow : DsglWindow() {
    private var clicks by state(0)

    override fun render(): DomTree =
        ui {
            centeredFlexWrapper {
                helloDSGL(clicks, { clicks += 1 })
            }
        }
}

private fun UiScope.helloDSGL(clicks: Int, setClicks: (_: Event) -> Unit) {
    text("Hello DSGL")
    text("Clicks: $clicks")
    button("Click me #${clicks + 1}th time", { onMouseClick = setClicks })
}
