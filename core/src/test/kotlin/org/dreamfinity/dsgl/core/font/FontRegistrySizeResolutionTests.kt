package org.dreamfinity.dsgl.core.font

import kotlin.test.Test
import kotlin.test.assertEquals

class FontRegistrySizeResolutionTests {
    @Test
    fun `resolveFontSize does not apply hidden upper clamp`() {
        assertEquals(320, FontRegistry.resolveFontSize(320))
    }
}
