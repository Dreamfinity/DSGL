package org.dreamfinity.dsgl.core.font

import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FontDiscoveryTests {
    @Test
    fun `fontId mapping preserves subdirectories`() {
        assertEquals("Minecraft", FontDiscovery.fontIdFromRelativeTtfPath("Minecraft.ttf"))
        assertEquals("ui/Ubuntu", FontDiscovery.fontIdFromRelativeTtfPath("ui/Ubuntu.ttf"))
        assertEquals("noto/Noto_Sans/NotoSans-Regular", FontDiscovery.fontIdFromRelativeTtfPath("noto\\Noto_Sans\\NotoSans-Regular.ttf"))
    }

    @Test
    fun `generated index parsing keeps only ttf entries`() {
        val parsed = FontDiscovery.parseGeneratedFontIndex(
            """
            minecraft/MinecraftDefault-Regular.ttf
            not-a-font.txt

            ubuntu/Ubuntu-Regular.ttf
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "minecraft/MinecraftDefault-Regular.ttf",
                "ubuntu/Ubuntu-Regular.ttf"
            ),
            parsed
        )
    }

    @Test
    fun `external discovery validates complete font package trio`() {
        val root = createTempDirectory("dsgl-font-discovery")
        try {
            val validBase = root.resolve("ui/Ubuntu")
            validBase.parent.createDirectories()
            validBase.resolveSibling("Ubuntu.ttf").createFile()
            validBase.resolveSibling("Ubuntu-meta.json").createFile()
            validBase.resolveSibling("Ubuntu-mtsdf.png").createFile()

            val invalidBase = root.resolve("broken/MissingMeta")
            invalidBase.parent.createDirectories()
            invalidBase.resolveSibling("MissingMeta.ttf").createFile()
            invalidBase.resolveSibling("MissingMeta-mtsdf.png").createFile()

            val discovered = FontDiscovery.discoverExternalPackages(root)
            val valid = discovered.first { it.fontId == "ui/Ubuntu" }
            assertTrue(valid.isValid)
            assertTrue(valid.missing.isEmpty())

            val invalid = discovered.first { it.fontId == "broken/MissingMeta" }
            assertFalse(invalid.isValid)
            assertEquals(listOf("broken/MissingMeta-meta.json"), invalid.missing)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `external source overrides jar for same font id`() {
        val prioritized = FontDiscovery.resolveSourcePriority(
            jarFontIds = listOf("minecraft/MinecraftDefault-Regular", "ui/Ubuntu"),
            externalFontIds = listOf("ui/Ubuntu")
        )
        assertEquals(FontAssetSource.Jar, prioritized["minecraft/MinecraftDefault-Regular"])
        assertEquals(FontAssetSource.External, prioritized["ui/Ubuntu"])
    }
}
