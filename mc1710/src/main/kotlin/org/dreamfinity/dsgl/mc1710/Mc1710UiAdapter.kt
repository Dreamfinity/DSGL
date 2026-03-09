package org.dreamfinity.dsgl.mc1710

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.entity.RenderItem
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.host.Viewport
import org.dreamfinity.dsgl.core.host.dsglRectToGlScissor
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.mc1710.scissorsHelper.ScissorContext
import org.dreamfinity.dsgl.mc1710.text.MsdfTextRenderer
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import java.io.File
import java.net.URL
import javax.imageio.ImageIO

/**
 * Minecraft 1.7.10 adapter that turns DSGL render commands into Minecraft calls.
 */
class Mc1710UiAdapter(private val mc: Minecraft, var paintsCount: Long = 0L) : UiMeasureContext {
    companion object {
        private val imageCache: MutableMap<String, ResourceLocation> = HashMap()
        private val dynamicTexturesCache: MutableMap<String, DynamicTexture> = HashMap()
    }

    private val itemRenderer: RenderItem = RenderItem()
    private val textRenderer: MsdfTextRenderer = MsdfTextRenderer()
    private val opacityStack: MutableList<Float> = ArrayList(8)
    private var opacityMultiplier: Float = 1f
    private val errorLogTimes: MutableMap<String, Long> = linkedMapOf()
    private val samplePixelBuffer = BufferUtils.createByteBuffer(4)
    private var sampleAreaBuffer = BufferUtils.createByteBuffer(4 * 256)
    private var cachedViewport: Viewport = Viewport(width = 1, height = 1, scale = 1f, x = 0, y = 0)
    private var cachedDisplayWidth: Int = -1
    private var cachedDisplayHeight: Int = -1

    override fun measureText(text: String): Int = textRenderer.measureText(text, null, null)
    override fun measureText(text: String, fontId: String?, fontSize: Int?): Int {
        return textRenderer.measureText(text, fontId, fontSize)
    }

    override val fontHeight: Int
        get() = textRenderer.lineHeight(FontRegistry.DEFAULT_FONT_ID, null)

    override fun fontHeight(fontId: String?, fontSize: Int?): Int {
        return textRenderer.lineHeight(fontId, fontSize)
    }

    fun viewport(): Viewport {
        val displayWidth = mc.displayWidth.coerceAtLeast(1)
        val displayHeight = mc.displayHeight.coerceAtLeast(1)
        if (displayWidth != cachedDisplayWidth || displayHeight != cachedDisplayHeight) {
            cachedDisplayWidth = displayWidth
            cachedDisplayHeight = displayHeight
            cachedViewport = Viewport(
                width = displayWidth,
                height = displayHeight,
                scale = 1f,
                x = 0,
                y = 0
            )
        }
        return cachedViewport
    }

    fun sampleScreenColor(x: Int, y: Int): Int? {
        val viewport = viewport()
        if (x < 0 || y < 0 || x >= viewport.width || y >= viewport.height) return null
        val readY = viewport.height - 1 - y
        samplePixelBuffer.clear()
        return try {
            GL11.glReadBuffer(GL11.GL_BACK)
            GL11.glReadPixels(x, readY, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, samplePixelBuffer)
            val r = samplePixelBuffer.get(0).toInt() and 0xFF
            val g = samplePixelBuffer.get(1).toInt() and 0xFF
            val b = samplePixelBuffer.get(2).toInt() and 0xFF
            val a = samplePixelBuffer.get(3).toInt() and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or b
        } catch (_: Throwable) {
            null
        }
    }

    fun sampleScreenArea(x: Int, y: Int, width: Int, height: Int, outArgb: IntArray): Boolean {
        if (width <= 0 || height <= 0) return false
        val required = width * height
        if (outArgb.size < required) return false
        val viewport = viewport()
        var i = 0
        while (i < required) {
            outArgb[i] = 0
            i++
        }

        val srcX = x.coerceIn(0, viewport.width)
        val srcY = y.coerceIn(0, viewport.height)
        val maxW = viewport.width - srcX
        val maxH = viewport.height - srcY
        val srcW = minOf(width, maxW).coerceAtLeast(0)
        val srcH = minOf(height, maxH).coerceAtLeast(0)
        if (srcW <= 0 || srcH <= 0) return false

        val byteCount = srcW * srcH * 4
        if (sampleAreaBuffer.capacity() < byteCount) {
            sampleAreaBuffer = BufferUtils.createByteBuffer(byteCount)
        }
        sampleAreaBuffer.clear()
        sampleAreaBuffer.limit(byteCount)
        return try {
            val readY = viewport.height - (srcY + srcH)
            GL11.glReadBuffer(GL11.GL_BACK)
            GL11.glReadPixels(srcX, readY, srcW, srcH, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, sampleAreaBuffer)
            val dstOffsetX = (srcX - x).coerceAtLeast(0)
            val dstOffsetY = (srcY - y).coerceAtLeast(0)
            var row = 0
            while (row < srcH) {
                val glRow = srcH - 1 - row
                var col = 0
                while (col < srcW) {
                    val srcIndex = (glRow * srcW + col) * 4
                    val r = sampleAreaBuffer.get(srcIndex).toInt() and 0xFF
                    val g = sampleAreaBuffer.get(srcIndex + 1).toInt() and 0xFF
                    val b = sampleAreaBuffer.get(srcIndex + 2).toInt() and 0xFF
                    val a = sampleAreaBuffer.get(srcIndex + 3).toInt() and 0xFF
                    val dstX = dstOffsetX + col
                    val dstY = dstOffsetY + row
                    outArgb[dstY * width + dstX] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    col++
                }
                row++
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Executes DSGL render commands using Minecraft rendering APIs. */
    override fun paint(commands: List<RenderCommand>) {
        paintsCount++
        opacityStack.clear()
        opacityMultiplier = 1f
        val viewport = viewport()
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            ScissorContext.clear()
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            GL11.glViewport(viewport.x, viewport.y, viewport.width, viewport.height)
            GL11.glMatrixMode(GL11.GL_PROJECTION)
            GL11.glPushMatrix()
            GL11.glLoadIdentity()
            GL11.glOrtho(0.0, viewport.width.toDouble(), viewport.height.toDouble(), 0.0, -1000.0, 1000.0)
            GL11.glMatrixMode(GL11.GL_MODELVIEW)
            GL11.glPushMatrix()
            GL11.glLoadIdentity()
            try {
                for (command in commands) {
                    when (command) {
                        is RenderCommand.DrawRect -> {
                            Gui.drawRect(
                                command.x,
                                command.y,
                                command.x + command.width,
                                command.y + command.height,
                                applyOpacity(command.color)
                            )
                        }

                        is RenderCommand.DrawColorField -> {
                            drawColorField(
                                x = command.x,
                                y = command.y,
                                width = command.width,
                                height = command.height,
                                hueDeg = command.hueDeg
                            )
                        }

                        is RenderCommand.DrawHueBar -> {
                            drawHueBar(
                                x = command.x,
                                y = command.y,
                                width = command.width,
                                height = command.height
                            )
                        }

                        is RenderCommand.DrawAlphaBar -> {
                            drawAlphaBar(
                                x = command.x,
                                y = command.y,
                                width = command.width,
                                height = command.height,
                                rgbColor = command.rgbColor
                            )
                        }

                        is RenderCommand.DrawText -> {
                            try {
                                textRenderer.draw(
                                    command = command,
                                    opacityMultiplier = opacityMultiplier
                                )
                            } catch (error: LinkageError) {
                                logRateLimited(
                                    key = "drawText:linkage",
                                    message = "[DSGL] Skipping DrawText due linkage error in text renderer: ${error.message}"
                                )
                            } catch (error: Throwable) {
                                logRateLimited(
                                    key = "drawText:runtime",
                                    message = "[DSGL] Skipping DrawText due renderer error: ${error.message}"
                                )
                            }
                        }

                        is RenderCommand.DrawImage -> {
                            val location = resolveImage(command.resource) ?: continue
                            mc.textureManager.bindTexture(location)
                            GL11.glColor4f(1f, 1f, 1f, opacityMultiplier.coerceIn(0f, 1f))
                            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
                            Gui.drawModalRectWithCustomSizedTexture(
                                command.x,
                                command.y,
                                0f,
                                0f,
                                command.width,
                                command.height,
                                command.width.toFloat(),
                                command.height.toFloat()
                            )
                        }

                        is RenderCommand.DrawItemStack -> {
                            val stack = (command.stack as? McItemStackRef)?.stack ?: continue
                            drawItemStack(
                                stack = stack,
                                x = command.x,
                                y = command.y,
                                size = command.size,
                                width = command.width,
                                rotY = command.rotYDeg,
                                rotX = command.rotXDeg
                            )
                        }

                        is RenderCommand.PushClip -> {
                            pushClip(
                                viewport = viewport,
                                guiX = command.x,
                                guiY = command.y,
                                guiWidth = command.width,
                                guiHeight = command.height
                            )
                        }

                        is RenderCommand.PopClip -> {
                            ScissorContext.pop()
                        }

                        is RenderCommand.PushTransform -> {
                            GL11.glPushMatrix()
                            GL11.glTranslatef(command.originX, command.originY, 0f)
                            GL11.glTranslatef(command.translateX, command.translateY, 0f)
                            GL11.glRotatef(command.rotateDeg, 0f, 0f, 1f)
                            GL11.glScalef(command.scaleX, command.scaleY, 1f)
                            GL11.glTranslatef(-command.originX, -command.originY, 0f)
                        }

                        is RenderCommand.PopTransform -> {
                            GL11.glPopMatrix()
                        }

                        is RenderCommand.PushOpacity -> {
                            opacityStack.add(opacityMultiplier)
                            opacityMultiplier = (opacityMultiplier * command.opacity).coerceIn(0f, 1f)
                        }

                        is RenderCommand.PopOpacity -> {
                            opacityMultiplier =
                                if (opacityStack.isEmpty()) 1f else opacityStack.removeAt(opacityStack.lastIndex)
                        }
                    }
                }
            } finally {
                GL11.glMatrixMode(GL11.GL_MODELVIEW)
                GL11.glPopMatrix()
                GL11.glMatrixMode(GL11.GL_PROJECTION)
                GL11.glPopMatrix()
                GL11.glMatrixMode(GL11.GL_MODELVIEW)
            }
        } finally {
            ScissorContext.clear()
            opacityStack.clear()
            opacityMultiplier = 1f
            GL11.glPopAttrib()
        }
    }

    private fun applyOpacity(color: Int): Int {
        if (opacityMultiplier >= 0.999f) return color
        val alpha = ((color ushr 24) and 0xFF)
        val scaled = (alpha * opacityMultiplier).toInt().coerceIn(0, 255)
        return (color and 0x00FF_FFFF) or (scaled shl 24)
    }

    private fun drawColorField(x: Int, y: Int, width: Int, height: Int, hueDeg: Float) {
        if (width <= 0 || height <= 0) return

        val normalizedHue = ((hueDeg % 360f) + 360f) % 360f
        val hueColor = (hsvToArgbInt(normalizedHue, 1f, 1f) and 0x00FF_FFFF) or (0xFF shl 24)

        drawGradientBlock {
            drawHorizontalGradientRectRaw(
                x, y, width, height,
                applyOpacity(0xFFFFFFFF.toInt()),
                applyOpacity(hueColor)
            )
            drawVerticalGradientRectRaw(
                x, y, width, height,
                applyOpacity(0x00000000),
                applyOpacity(0xFF000000.toInt())
            )
        }
    }

    private fun drawHueBar(x: Int, y: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val segments = 6
        val hueStops = floatArrayOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
        var index = 0
        while (index < segments) {
            val startX = x + (width * index) / segments
            val endX = if (index == segments - 1) x + width else x + (width * (index + 1)) / segments
            val segmentWidth = (endX - startX).coerceAtLeast(1)
            val startColor = applyOpacity(hsvToArgbInt(hueStops[index], 1f, 1f))
            val endColor = applyOpacity(hsvToArgbInt(hueStops[index + 1], 1f, 1f))
            drawHorizontalGradientRect(startX, y, segmentWidth, height, startColor, endColor)
            index += 1
        }
    }

    private fun drawAlphaBar(x: Int, y: Int, width: Int, height: Int, rgbColor: Int) {
        if (width <= 0 || height <= 0) return
        val rgbOnly = rgbColor and 0x00FF_FFFF
        val leftColor = applyOpacity(rgbOnly)
        val rightColor = applyOpacity(rgbOnly or (0xFF shl 24))
        drawHorizontalGradientRect(x, y, width, height, leftColor, rightColor)
    }

    private fun drawHorizontalGradientRect(x: Int, y: Int, width: Int, height: Int, leftColor: Int, rightColor: Int) {
        if (width <= 0 || height <= 0) return
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glShadeModel(GL11.GL_SMOOTH)
        drawHorizontalGradientRectRaw(x, y, width, height, leftColor, rightColor)
        GL11.glShadeModel(GL11.GL_FLAT)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glColor4f(1f, 1f, 1f, 1f)
    }

    private fun drawVerticalGradientRect(x: Int, y: Int, width: Int, height: Int, topColor: Int, bottomColor: Int) {
        if (width <= 0 || height <= 0) return
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_ALPHA)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glShadeModel(GL11.GL_SMOOTH)
        drawVerticalGradientRectRaw(x, y, width, height, topColor, bottomColor)
        GL11.glEnable(GL11.GL_ALPHA)
        GL11.glShadeModel(GL11.GL_FLAT)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glColor4f(1f, 1f, 1f, 1f)
    }

    private inline fun drawGradientBlock(block: () -> Unit) {
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDepthMask(false)
        GL11.glDisable(GL11.GL_ALPHA_TEST)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glShadeModel(GL11.GL_SMOOTH)

        block()

        GL11.glShadeModel(GL11.GL_FLAT)
        GL11.glDisable(GL11.GL_BLEND)
        GL11.glEnable(GL11.GL_ALPHA_TEST)
        GL11.glDepthMask(true)
        GL11.glEnable(GL11.GL_DEPTH_TEST)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glColor4f(1f, 1f, 1f, 1f)
    }

    private fun drawHorizontalGradientRectRaw(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        leftColor: Int,
        rightColor: Int
    ) {
        GL11.glBegin(GL11.GL_QUADS)
        glColor(leftColor)
        GL11.glVertex2f(x.toFloat(), y.toFloat())
        GL11.glVertex2f(x.toFloat(), (y + height).toFloat())
        glColor(rightColor)
        GL11.glVertex2f((x + width).toFloat(), (y + height).toFloat())
        GL11.glVertex2f((x + width).toFloat(), y.toFloat())
        GL11.glEnd()
    }

    private fun drawVerticalGradientRectRaw(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        topColor: Int,
        bottomColor: Int
    ) {
        GL11.glBegin(GL11.GL_QUADS)
        glColor(topColor)
        GL11.glVertex2f((x + width).toFloat(), y.toFloat())
        GL11.glVertex2f(x.toFloat(), y.toFloat())
        glColor(bottomColor)
        GL11.glVertex2f(x.toFloat(), (y + height).toFloat())
        GL11.glVertex2f((x + width).toFloat(), (y + height).toFloat())
        GL11.glEnd()
    }

    private fun drawBilinearGradientRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        topLeftColor: Int,
        topRightColor: Int,
        bottomRightColor: Int,
        bottomLeftColor: Int
    ) {
        if (width <= 0 || height <= 0) return
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glShadeModel(GL11.GL_SMOOTH)
        GL11.glBegin(GL11.GL_QUADS)
        glColor(topLeftColor)
        GL11.glVertex2f(x.toFloat(), y.toFloat())
        glColor(bottomLeftColor)
        GL11.glVertex2f(x.toFloat(), (y + height).toFloat())
        glColor(bottomRightColor)
        GL11.glVertex2f((x + width).toFloat(), (y + height).toFloat())
        glColor(topRightColor)
        GL11.glVertex2f((x + width).toFloat(), y.toFloat())
        GL11.glEnd()
        GL11.glShadeModel(GL11.GL_FLAT)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glColor4f(1f, 1f, 1f, 1f)
    }

    private fun glColor(argb: Int) {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        GL11.glColor4f(r, g, b, a)
    }

    private fun hsvToArgbInt(hueDeg: Float, saturation: Float, value: Float): Int {
        val h = ((hueDeg % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun logRateLimited(key: String, message: String) {
        val now = System.currentTimeMillis()
        val previous = errorLogTimes[key] ?: 0L
        if (now - previous < 3_000L) return
        errorLogTimes[key] = now
        println(message)
    }

    private fun pushClip(viewport: Viewport, guiX: Int, guiY: Int, guiWidth: Int, guiHeight: Int) {
        val scissor = viewport.dsglRectToGlScissor(guiX, guiY, guiWidth, guiHeight)
        ScissorContext.push(scissor.x, scissor.y, scissor.width, scissor.height)
    }

    private fun isBlockStack(stack: ItemStack): Boolean {
        return stack.item is ItemBlock
    }

    private fun draw2DItem(stack: ItemStack, x: Int, y: Int, size: Int, maxWidth: Int) {
        val drawX = x + ((maxWidth - size) / 2).coerceAtLeast(0)
        withStack {
            withAttributes(enable = listOf(GL11.GL_DEPTH_TEST)) {
                val scale = size / 16.0f
                GL11.glTranslatef(drawX.toFloat(), y.toFloat(), 0.0f)
                GL11.glScalef(scale, scale, 1.0f)
                itemRenderer.renderItemAndEffectIntoGUI(mc.fontRendererObj, mc.textureManager, stack, 0, 0)
            }
        }
    }

    private fun draw3DItem(stack: ItemStack, x: Int, y: Int, size: Int, width: Int, rotY: Double, rotX: Double) {
        val scale = size / 16.0f
        val drawX = x + ((width - size) / 2).coerceAtLeast(0)

        withStack {
            withAttributes(enable = listOf(GL11.GL_BLEND, GL11.GL_DEPTH_TEST, GL12.GL_RESCALE_NORMAL)) {
                withItemGuiLightning {
                    GL11.glTranslated(drawX.toDouble(), y.toDouble(), 100.0)
                    GL11.glScaled(scale.toDouble(), scale.toDouble(), scale.toDouble())
                    GL11.glTranslated(8.0, 8.0, 0.0)
                    GL11.glRotated(rotX, 1.0, 0.0, 0.0)
                    GL11.glRotated(rotY, 0.0, 1.0, 0.0)
                    GL11.glTranslated(-8.0, -8.0, 0.0)
                    itemRenderer.renderItemAndEffectIntoGUI(mc.fontRendererObj, mc.textureManager, stack, 0, 0)
                }
            }
        }
    }

    private fun drawItemStack(
        stack: ItemStack,
        x: Int,
        y: Int,
        size: Int,
        width: Int,
        rotY: Double,
        rotX: Double
    ) {
        withStack(attributesBitMask = GL11.GL_ALL_ATTRIB_BITS) {
            val previousZ = itemRenderer.zLevel
            try {
                itemRenderer.zLevel = 0f
                GL11.glColor4f(1f, 1f, 1f, opacityMultiplier.coerceIn(0f, 1f))
                if (isBlockStack(stack)) {
                    draw3DItem(stack, x, y, size, width, rotY, rotX)
                } else {
                    draw2DItem(stack, x, y, size, width)
                }
            } finally {
                itemRenderer.zLevel = previousZ
            }
        }
    }

    private fun resolveImage(source: String): ResourceLocation? {
        imageCache[source]?.let { return it }

        return when {
            source.startsWith("http://") || source.startsWith("https://") -> {
                val url = runCatching { URL(source) }.getOrNull() ?: return null
                val file = remoteFileFor(url)
                if (!file.exists()) {
                    if (!downloadToFile(url, file)) return null
                }
                loadDynamicTexture(file, source)
            }

            source.startsWith("file://") -> {
                var relative = source.removePrefix("file://")
                while (relative.startsWith("/") || relative.startsWith("\\")) {
                    relative = relative.substring(1)
                }
                val baseDir = File(mc.mcDataDir, "dsgl")
                val file = File(baseDir, relative)
                loadDynamicTexture(file, source)
            }

            else -> {
                val location = ResourceLocation(source)
                imageCache[source] = location
                location
            }
        }
    }

    private fun remoteFileFor(url: URL): File {
        val host = if (url.host.isNullOrBlank()) "unknown" else url.host
        var path = url.path
        if (path.isBlank() || path == "/") {
            path = "/index"
        }
        if (path.startsWith("/")) {
            path = path.substring(1)
        }
        path = path.replace("..", "_")
        val baseDir = File(mc.mcDataDir, "dsgl/cache/downloads")
        return File(baseDir, host + File.separator + path)
    }

    private fun downloadToFile(url: URL, file: File): Boolean {
        return try {
            file.parentFile?.mkdirs()
            url.openStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (ex: Exception) {
            false
        }
    }

    private fun loadDynamicTexture(file: File, cacheKey: String): ResourceLocation? {
        if (!file.exists()) return null
        val cached = imageCache[cacheKey]
        if (cached != null) return cached
        return try {
            val image = ImageIO.read(file) ?: return null
            val texture = DynamicTexture(image)
            dynamicTexturesCache[cacheKey] = texture
            val name = "dsgl_${cacheKey.hashCode().toString(16)}"
            val location = mc.textureManager.getDynamicTextureLocation(name, texture)
            imageCache[cacheKey] = location
            location
        } catch (ex: Exception) {
            null
        }
    }
}
