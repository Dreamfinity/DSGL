package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.hooks.ComponentHookRuntime
import org.dreamfinity.dsgl.core.hooks.HookRenderSessionMode
import org.dreamfinity.dsgl.core.host.DsglWindowHost
import java.time.Instant
import java.time.ZoneId

/**
 * Version-agnostic window definition. Platform hosts own the UI lifecycle.
 *
 * Implement [render] to return a [DomTree].
 *
 * Use [state] for window-owned state and [org.dreamfinity.dsgl.core.dsl.UiScope] hooks for component-local state.
 * This class is expected to be used on the client/UI thread of the host platform.
 */
abstract class DsglWindow {
    private var invalidator: (() -> Unit)? = null
    private val componentHookRuntime: ComponentHookRuntime = ComponentHookRuntime()

    /**
     * Called by platform hosts to connect this window to a host implementation.
     */
    fun attachHost(host: DsglWindowHost) {
        invalidator = { host.requestRebuild("state") }
    }

    /** Records the open time for date/time controls. */
    fun markOpened(instant: Instant, zoneId: ZoneId) {
    }

    /** Requests a rebuild of the DOM tree. */
    protected fun invalidate() {
        invalidator?.invoke()
    }

    /**
     * Creates window-owned observable state that triggers [invalidate] on change.
     *
     * This is intentionally separate from component-local hook state.
     */
    fun <T> state(initial: T): MutableState<T> {
        return mutableStateOf(initial) { invalidate() }
    }

    /** Build the current UI tree. Called by the host on rebuild. */
    abstract fun render(): DomTree

    /**
     * Builds a tree bound to this window so UiScope hook APIs can resolve the owning runtime.
     */
    protected fun ui(block: UiScope.() -> Unit): DomTree {
        return org.dreamfinity.dsgl.core.dsl.ui(this, block)
    }

    /**
     * Builds a tree from a custom root bound to this window.
     */
    protected fun ui(root: DOMNode, block: UiScope.() -> Unit): DomTree {
        return org.dreamfinity.dsgl.core.dsl.ui(this, root, block)
    }

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
     * Called every render frame with delta-time in seconds.
     * [dtSeconds] is the primary timing source; [partialTicks] is host-provided interpolation hint.
     */
    open fun tick(dtSeconds: Float, partialTicks: Float?) {}

    /**
     * When true, the host will rebuild the tree on resize.
     */
    open val rebuildOnResize: Boolean
        get() = false

    /**
     * Host lifecycle hook: starts a hook render session before calling [render].
     */
    fun beginRenderBuild(mode: HookRenderSessionMode = HookRenderSessionMode.Normal) {
        componentHookRuntime.beginRender(this, mode)
    }

    /**
     * Host lifecycle hook: finalizes per-render hook runtime cleanup after [render].
     */
    fun endRenderBuild() {
        componentHookRuntime.endRender()
    }

    /**
     * Host lifecycle hook: commits post-render hook side effects for the last successful tree commit.
     */
    fun commitRenderBuild() {
        componentHookRuntime.commitPostRenderEffects()
    }

    /**
     * Host lifecycle hook: discards post-render hook side effects from an aborted/failed render attempt.
     */
    fun discardRenderBuild() {
        componentHookRuntime.discardPostRenderEffects()
    }

    /**
     * Host lifecycle hook: disposes all hook-local runtime state for this window instance.
     */
    fun disposeHookRuntime() {
        componentHookRuntime.disposeAll()
    }

    internal fun onHookStateChanged() {
        invalidate()
    }

    internal fun hookRuntime(): ComponentHookRuntime = componentHookRuntime
}

