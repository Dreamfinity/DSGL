package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.hooks.createContext
import org.dreamfinity.dsgl.core.hooks.provideContext
import org.dreamfinity.dsgl.core.hooks.useContext
import org.dreamfinity.dsgl.core.hooks.useState
import kotlin.test.Test
import kotlin.test.assertEquals

class UseContextTests {
    private companion object {
        val ThemeContext = createContext(defaultValue = "System", name = "Theme")
    }

    @Test
    fun `useContext returns default when no provider exists`() {
        val window = DefaultOnlyContextWindow()

        renderWithHookSession(window)

        assertEquals("System", window.lastSeen)
    }

    @Test
    fun `nearest provider wins and nested providers override outer provider`() {
        val window = NestedProviderWindow()

        renderWithHookSession(window)

        assertEquals("Outer", window.outerSeen)
        assertEquals("Inner", window.innerSeen)
        assertEquals("Outer", window.afterNestedSeen)
    }

    @Test
    fun `consumer sees updated provider value after rebuild`() {
        val window = ProviderValueChangeWindow()

        renderWithHookSession(window)
        assertEquals("Light", window.lastSeen)

        window.pendingProviderValue = "Dark"
        renderWithHookSession(window)
        assertEquals("Dark", window.lastSeen)
    }

    @Test
    fun `provider disappearance falls back to default and reappearance restores provided value`() {
        val window = ConditionalProviderWindow()

        window.showProvider = true
        renderWithHookSession(window)
        assertEquals("Provided", window.lastSeen)

        window.showProvider = false
        renderWithHookSession(window)
        assertEquals("System", window.lastSeen)

        window.showProvider = true
        renderWithHookSession(window)
        assertEquals("Provided", window.lastSeen)
    }

    private fun renderWithHookSession(window: DsglWindow): DomTree {
        window.beginRenderBuild()
        return try {
            window.render()
        } finally {
            window.endRenderBuild()
            window.commitRenderBuild()
        }
    }

    private class DefaultOnlyContextWindow : DsglWindow() {
        var lastSeen: String? = null

        override fun render(): DomTree =
            ui {
                lastSeen = useContext(ThemeContext)
            }
    }

    private class NestedProviderWindow : DsglWindow() {
        var outerSeen: String? = null
        var innerSeen: String? = null
        var afterNestedSeen: String? = null

        override fun render(): DomTree =
            ui {
                provideContext(ThemeContext, "Outer") {
                    outerSeen = useContext(ThemeContext)
                    provideContext(ThemeContext, "Inner") {
                        innerSeen = useContext(ThemeContext)
                    }
                    afterNestedSeen = useContext(ThemeContext)
                }
            }
    }

    private class ProviderValueChangeWindow : DsglWindow() {
        var pendingProviderValue: String? = null
        var lastSeen: String? = null

        override fun render(): DomTree =
            ui {
                var providerValue by useState("Light")
                pendingProviderValue?.let { next ->
                    providerValue = next
                    pendingProviderValue = null
                }
                provideContext(ThemeContext, providerValue) {
                    lastSeen = useContext(ThemeContext)
                }
            }
    }

    private class ConditionalProviderWindow : DsglWindow() {
        var showProvider: Boolean = true
        var lastSeen: String? = null

        override fun render(): DomTree =
            ui {
                if (showProvider) {
                    provideContext(ThemeContext, "Provided") {
                        lastSeen = useContext(ThemeContext)
                    }
                } else {
                    lastSeen = useContext(ThemeContext)
                }
            }
    }
}
