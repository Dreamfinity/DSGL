package org.dreamfinity.dsgl.core

import java.time.Instant
import java.time.ZoneId

/**
 * Version-agnostic window definition. Platform hosts own the UI lifecycle.
 */
abstract class DsglWindow {
    private var invalidator: (() -> Unit)? = null
    private var openedAtInstant: Instant = Instant.now()
    private var openedZoneId: ZoneId = ZoneId.systemDefault()

    internal fun attachHost(host: org.dreamfinity.dsgl.core.host.DsglWindowHost) {
        invalidator = { host.requestRebuild("state") }
    }

    internal fun markOpened(instant: Instant, zoneId: ZoneId) {
        openedAtInstant = instant
        openedZoneId = zoneId
    }

    protected fun invalidate() {
        invalidator?.invoke()
    }

    protected fun <T> state(initial: T): MutableState<T> {
        return mutableStateOf(initial) { invalidate() }
    }

    protected val openedAt: Instant
        get() = openedAtInstant

    protected val timeZoneId: ZoneId
        get() = openedZoneId

    abstract fun render(): DomTree

    open fun onOpen() {}
    open fun onClose() {}
    open fun onResize(width: Int, height: Int) {}
    open fun onClick(x: Int, y: Int, button: Int) {}
    open fun onKeyTyped(typedChar: Char, keyCode: Int) {}

    open val rebuildOnResize: Boolean
        get() = false
}
