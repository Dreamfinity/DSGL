package org.dreamfinity.dsgl.core

import java.util.concurrent.atomic.AtomicBoolean

object HotReloadBridge {
    @JvmStatic
    private val HOTSWAP_PENDING: AtomicBoolean = AtomicBoolean(false)

    @JvmStatic
    fun markHotSwap() {
        HOTSWAP_PENDING.set(true)
    }

    @JvmStatic
    fun consumeHotSwap(): Boolean {
        return HOTSWAP_PENDING.getAndSet(false)
    }
}