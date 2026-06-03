package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.portal.DomainPortalServices
import org.dreamfinity.dsgl.core.portal.ScreenDomainId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainPortalServicesOwnershipTests {
    @AfterTest
    fun cleanup() {
        DomainPortalServices.closeAllSelects()
    }

    @Test
    fun `application-scoped request opens application engine only`() {
        val owner = Any()
        DomainPortalServices.openSelect(request(owner, ScreenDomainId.Application))

        assertTrue(DomainPortalServices.applicationSelectEngine.isOpenFor(owner))
        assertFalse(DomainPortalServices.systemSelectEngine.isOpenFor(owner))
        assertTrue(DomainPortalServices.isSelectOpenFor(owner))
    }

    @Test
    fun `system-scoped request opens system engine only`() {
        val owner = Any()
        DomainPortalServices.openSelect(request(owner, ScreenDomainId.System))

        assertFalse(DomainPortalServices.applicationSelectEngine.isOpenFor(owner))
        assertTrue(DomainPortalServices.systemSelectEngine.isOpenFor(owner))
        assertTrue(DomainPortalServices.isSelectOpenFor(owner))
    }

    @Test
    fun `opening same owner in another scope switches engine ownership`() {
        val owner = Any()
        DomainPortalServices.openSelect(request(owner, ScreenDomainId.Application))
        assertTrue(DomainPortalServices.applicationSelectEngine.isOpenFor(owner))
        assertFalse(DomainPortalServices.systemSelectEngine.isOpenFor(owner))

        DomainPortalServices.openSelect(request(owner, ScreenDomainId.System))
        assertFalse(DomainPortalServices.applicationSelectEngine.isOpenFor(owner))
        assertTrue(DomainPortalServices.systemSelectEngine.isOpenFor(owner))
    }

    private fun request(owner: Any, scope: ScreenDomainId): SelectOpenRequest =
        SelectOpenRequest(
            owner = owner,
            modelToken = 1L,
            entries = listOf(SelectEntry.Option("a", labelProvider = { "Alpha" })),
            selectedId = "a",
            anchorRect = Rect(10, 10, 100, 20),
            closeOnSelect = true,
            ownerDomain = scope,
        )
}
