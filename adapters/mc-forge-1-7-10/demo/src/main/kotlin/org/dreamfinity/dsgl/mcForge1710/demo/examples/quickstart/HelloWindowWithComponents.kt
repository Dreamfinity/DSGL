package org.dreamfinity.dsgl.mcForge1710.demo.examples.quickstart

import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dsl.button
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mcForge1710.demo.examples.containers.centeredFlexWrapper

class HelloWindowWithComponents : DsglWindow() {
    override fun render() = ui {
        centeredFlexWrapper(direction = FlexDirection.Row) {
            counterCard("Left panel")
            counterCard("Right panel")
        }
    }
}

private fun UiScope.counterCard(title: String) {
    var count by useState(0)

    div({
        key = "counter.$title"
        style = {
            display = Display.Flex
            flexDirection = FlexDirection.Column
            gap = 3.px
            padding = 4.px
        }
    }) {
        text(title)
        text("Count: $count")
        button("Increment", { onMouseClick = { count += 1 } })
    }
}
