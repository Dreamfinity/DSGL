package org.dreamfinity.dsgl.mcForge1122

import org.dreamfinity.dsgl.core.font.FontPreloadSummary
import org.dreamfinity.dsgl.core.font.FontRegistry
import java.io.File

object DsglFonts {
    private val lock = Any()

    @Volatile
    private var initialized: Boolean = false

    @Volatile
    private var lastSummary: FontPreloadSummary? = null

    private val warmFontIds: Set<String> = linkedSetOf(
        FontRegistry.DEFAULT_FONT_ID,
        FontRegistry.FONT_UBUNTU,
        FontRegistry.FONT_JB_MONO,
        FontRegistry.TELEGRAFICO,
        FontRegistry.FALLBACK_FONT_ID
    )

    fun ensureInitialized(gameDir: File, classLoader: ClassLoader = javaClass.classLoader): FontPreloadSummary {
        if (initialized) {
            return lastSummary ?: FontRegistry.discoverAndPreloadFonts(
                externalFontsDir = File(gameDir, "dsgl/fonts"),
                classLoader = classLoader
            ).also { lastSummary = it }
        }
        synchronized(lock) {
            if (initialized) {
                return lastSummary ?: FontRegistry.discoverAndPreloadFonts(
                    externalFontsDir = File(gameDir, "dsgl/fonts"),
                    classLoader = classLoader
                ).also { lastSummary = it }
            }
            val summary = FontRegistry.discoverAndPreloadFonts(
                externalFontsDir = File(gameDir, "dsgl/fonts"),
                classLoader = classLoader
            )
            val warmed = FontRegistry.predecodeAtlases(warmFontIds)
            if (warmed > 0) {
                println("[DSGL-MSDF] predecoded atlases for $warmed warm fonts")
            }
            lastSummary = summary
            initialized = true
            return summary
        }
    }

    fun summaryOrNull(): FontPreloadSummary? = lastSummary
}
