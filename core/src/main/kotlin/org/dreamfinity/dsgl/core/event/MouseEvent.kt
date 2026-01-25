package org.dreamfinity.dsgl.core.event

/**
 * Base mouse event with cursor position in UI coordinates.
 */
abstract class MouseEvent(open var mouseX: Int, open var mouseY: Int) : Event()
