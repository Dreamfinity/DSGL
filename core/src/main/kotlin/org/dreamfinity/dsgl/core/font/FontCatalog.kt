package org.dreamfinity.dsgl.core.font

internal interface FontCatalog {
    fun registerMsdf(
        fontId: FontId,
        metaResourcePath: String,
        atlasResourcePath: String,
        ttfResourcePath: String? = null,
        source: FontAssetSource = FontAssetSource.Jar
    )

    fun registerGeneratedFromTtfPath(
        relativeTtfPath: String,
        fontId: FontId = FontRegistry.fontIdFromTtfPath(relativeTtfPath)
    )

    fun registerFileFont(
        fontId: FontId,
        metaFile: java.nio.file.Path,
        atlasFile: java.nio.file.Path,
        ttfFile: java.nio.file.Path? = null
    )

    fun discoverAndPreloadFonts(
        externalFontsDir: java.io.File?,
        classLoader: ClassLoader = FontRegistry::class.java.classLoader
    ): FontPreloadSummary

    fun get(fontId: FontId?): LoadedMsdfFont?

    fun getExact(fontId: FontId): LoadedMsdfFont?

    fun allFontIds(): Set<FontId>

    fun registeredFonts(): List<RegisteredFontInfo>

    fun clearLoadedFonts()
}
