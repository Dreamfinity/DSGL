package org.dreamfinity.dsgl.core

import java.time.Instant
import java.time.ZoneId

/**
 * Version-agnostic window definition. Platform hosts own the UI lifecycle.
 *
 * Implement [render] to return a [DomTree]. Use [state] or [invalidate] to request
 * rebuilds from the host when UI state changes. This class is expected to be used
 * on the client/UI thread of the host platform.
 */
abstract class DsglWindow {
    private var invalidator: (() -> Unit)? = null
    private var openedAtInstant: Instant = Instant.now()
    private var openedZoneId: ZoneId = ZoneId.systemDefault()

    /**
     * Called by platform hosts to connect this window to a host implementation.
     */
    fun attachHost(host: org.dreamfinity.dsgl.core.host.DsglWindowHost) {
        invalidator = { host.requestRebuild("state") }
    }

    /** Records the open time for date/time controls. */
    fun markOpened(instant: Instant, zoneId: ZoneId) {
        openedAtInstant = instant
        openedZoneId = zoneId
    }

    /** Requests a rebuild of the DOM tree. */
    protected fun invalidate() {
        invalidator?.invoke()
    }

    /**
     * Creates observable state that triggers [invalidate] on change.
     * TODO(Veritaris): think how to avoid making it private; maybe rework state to be kept
     * TODO: between re-renders and rebuild inside closure
     */
    fun <T> state(initial: T): MutableState<T> {
        return mutableStateOf(initial) { invalidate() }
    }

    /** Time when the window was opened, as provided by the host. */
    protected val openedAt: Instant
        get() = openedAtInstant

    /** Time zone used for date/time inputs. */
    protected val timeZoneId: ZoneId
        get() = openedZoneId

    /** Build the current UI tree. Called by the host on rebuild. */
    abstract fun render(): DomTree

    /** Lifecycle callback when the UI is opened by the host. */
    open fun onOpen() {}
    /** Lifecycle callback when the UI is closed by the host. */
    open fun onClose() {}
    /** Called when the host viewport changes. */
    open fun onResize(width: Int, height: Int) {}
    /** Raw mouse click hook at the host level. */
    open fun onClick(x: Int, y: Int, button: Int) {}
    /** Raw key input hook at the host level. */
    open fun onKeyTyped(typedChar: Char, keyCode: Int) {}
    /** Called every host frame before draw/rebuild decisions. */
    open fun onFrame(frameTimeMs: Long) {}

    /**
     * When true, the host will rebuild the tree on resize.
     */
    open val rebuildOnResize: Boolean
        get() = false
}
