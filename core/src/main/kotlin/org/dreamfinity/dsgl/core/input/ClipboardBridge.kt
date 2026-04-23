package org.dreamfinity.dsgl.core.input

/**
 * Host-provided clipboard bridge for platform-agnostic input nodes.
 */
interface ClipboardAccess {
    fun readText(): String

    fun writeText(value: String)
}

object ClipboardBridge {
    @Volatile
    private var provider: ClipboardAccess? = null

    fun install(access: ClipboardAccess?) {
        provider = access
    }

    fun readText(): String = provider?.readText() ?: ""

    fun writeText(value: String) {
        provider?.writeText(value)
    }
}
