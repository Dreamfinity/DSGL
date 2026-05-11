package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectPortalServicesOwnershipTests {
    @AfterTest
    fun cleanup() {
        SelectPortalServices.closeAll()
    }

    @Test
    fun `application-scoped request opens application engine only`() {
        val owner = Any()
        SelectPortalServices.open(request(owner, OverlayOwnerScope.Application))

        assertTrue(SelectPortalServices.applicationEngine.isOpenFor(owner))
        assertFalse(SelectPortalServices.systemEngine.isOpenFor(owner))
        assertTrue(SelectPortalServices.isOpenFor(owner))
    }

    @Test
    fun `system-scoped request opens system engine only`() {
        val owner = Any()
        SelectPortalServices.open(request(owner, OverlayOwnerScope.System))

        assertFalse(SelectPortalServices.applicationEngine.isOpenFor(owner))
        assertTrue(SelectPortalServices.systemEngine.isOpenFor(owner))
        assertTrue(SelectPortalServices.isOpenFor(owner))
    }

    @Test
    fun `opening same owner in another scope switches engine ownership`() {
        val owner = Any()
        SelectPortalServices.open(request(owner, OverlayOwnerScope.Application))
        assertTrue(SelectPortalServices.applicationEngine.isOpenFor(owner))
        assertFalse(SelectPortalServices.systemEngine.isOpenFor(owner))

        SelectPortalServices.open(request(owner, OverlayOwnerScope.System))
        assertFalse(SelectPortalServices.applicationEngine.isOpenFor(owner))
        assertTrue(SelectPortalServices.systemEngine.isOpenFor(owner))
    }

    private fun request(owner: Any, scope: OverlayOwnerScope): SelectOpenRequest =
        SelectOpenRequest(
            owner = owner,
            modelToken = 1L,
            entries = listOf(SelectEntry.Option("a", labelProvider = { "Alpha" })),
            selectedId = "a",
            anchorRect = Rect(10, 10, 100, 20),
            closeOnSelect = true,
            ownerScope = scope,
        )
}
