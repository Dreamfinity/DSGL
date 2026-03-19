package org.dreamfinity.dsgl.core.colorpicker

import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveColorSamplerOwnershipRouterTests {
    @Test
    fun `inline owner becomes active without popup`() {
        val router = ActiveColorSamplerOwnershipRouter()

        assertEquals(ActiveColorSamplerOwner.Inline("inline"), router.update(false, setOf("inline")))
    }

    @Test
    fun `opening popup does not steal existing inline owner`() {
        val router = ActiveColorSamplerOwnershipRouter()

        router.update(false, setOf("inline"))
        assertEquals(ActiveColorSamplerOwner.Inline("inline"), router.update(true, setOf("inline")))
    }

    @Test
    fun `popup owner becomes active when popup eyedropper starts first`() {
        val router = ActiveColorSamplerOwnershipRouter()

        assertEquals(ActiveColorSamplerOwner.Popup, router.update(true, emptySet()))
        assertEquals(ActiveColorSamplerOwner.Popup, router.update(true, emptySet()))
    }

    @Test
    fun `ownership falls back to popup when inline session ends`() {
        val router = ActiveColorSamplerOwnershipRouter()

        router.update(true, emptySet())
        router.update(true, setOf("inline"))

        assertEquals(ActiveColorSamplerOwner.Popup, router.update(true, emptySet()))
    }

    @Test
    fun `new inline session takes ownership over active popup`() {
        val router = ActiveColorSamplerOwnershipRouter()

        router.update(true, emptySet())
        router.update(true, setOf("inline-a"))

        assertEquals(ActiveColorSamplerOwner.Inline("inline-b"), router.update(true, setOf("inline-a", "inline-b")))
    }
}
