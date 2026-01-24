package org.dreamfinity.dsgl.core.event

data class KeyboardKeyDownEvent(var keyChar: Char, var keyCode: Int) : Event() {
    override val type: Events
        get() = Events.KEYDOWN
}
