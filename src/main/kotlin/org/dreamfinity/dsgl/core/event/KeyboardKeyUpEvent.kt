package org.dreamfinity.dsgl.core.event

data class KeyboardKeyUpEvent(var keyChar: Char, var keyCode: Int) : Event() {
    override val type: Events
        get() = Events.KEYUP
}
