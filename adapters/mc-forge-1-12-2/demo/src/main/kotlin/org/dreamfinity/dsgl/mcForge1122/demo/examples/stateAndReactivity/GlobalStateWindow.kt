package org.dreamfinity.dsgl.mcForge1122.demo.examples.stateAndReactivity

import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dsl.button
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.mcForge1122.demo.examples.containers.centeredFlexWrapper

class GlobalStateWindow : DsglWindow() {
    private var counter by state(0)

    override fun render() = ui {
        centeredFlexWrapper {
            globalStateCounter(counter, { counter += 1 })
        }
    }
}

private fun UiScope.globalStateCounter(counter: Int, setCounter: (_: Event) -> Unit) {
    button("Increment", { onMouseClick = setCounter })
    text("Counter: $counter")
}