package org.dreamfinity.dsgl.mc1710

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.entity.RenderItem
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
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

    override fun measureText(text: String): Int = mc.fontRendererObj.getStringWidth(text)
    override val fontHeight: Int
        get() = mc.fontRendererObj.FONT_HEIGHT

    /** Returns the scaled resolution for the current Minecraft window. */
    fun scaledResolution(): ScaledResolution =
        ScaledResolution(mc, mc.displayWidth, mc.displayHeight)

    /** Executes DSGL render commands using Minecraft rendering APIs. */
    override fun paint(commands: List<RenderCommand>) {
        paintsCount++
        for (command in commands) {
            when (command) {
                is RenderCommand.DrawRect -> {
                    Gui.drawRect(
                        command.x,
                        command.y,
                        command.x + command.width,
                        command.y + command.height,
                        command.color
                    )
                }

                is RenderCommand.DrawText -> {
                    mc.fontRendererObj.drawString(command.text, command.x, command.y, command.color)
                }

                is RenderCommand.DrawImage -> {
                    val location = resolveImage(command.resource) ?: return
                    mc.textureManager.bindTexture(location)
                    GL11.glColor3d(1.0, 1.0, 1.0)
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
                    val stack = (command.stack as? McItemStackRef)?.stack ?: return
                    if (isBlockStack(stack)) {
                        draw3DItem(stack, command.x, command.y, command.size, command.rotYDeg, command.rotXDeg)
                    } else {
                        draw2DItemWithName(stack, command.x, command.y, command.size, command.width)
                    }
                }
            }
        }
    }

    private fun isBlockStack(stack: ItemStack): Boolean {
        return stack.item is ItemBlock
    }

    private fun draw2DItemWithName(stack: ItemStack, x: Int, y: Int, size: Int, maxWidth: Int) {
        withStack {
            withAttributes(enable = listOf(GL11.GL_DEPTH_TEST)) {
                val scale = size / 16.0f
                GL11.glTranslatef(x.toFloat(), y.toFloat(), 0.0f)
                GL11.glScalef(scale, scale, 1.0f)
                itemRenderer.renderItemAndEffectIntoGUI(mc.fontRendererObj, mc.textureManager, stack, 0, 0)
            }
        }
    }

    private fun draw3DItem(stack: ItemStack, x: Int, y: Int, size: Int, rotY: Double, rotX: Double) {
        val scale = size / 16.0f
        val yaw = rotY
        val pitch = rotX

        withStack {
            withAttributes(enable = listOf(GL11.GL_BLEND, GL11.GL_DEPTH_TEST, GL12.GL_RESCALE_NORMAL)) {
                withItemGuiLightning {
                    GL11.glTranslated(x.toDouble(), y.toDouble(), 100.0)
                    GL11.glScaled(scale.toDouble(), scale.toDouble(), scale.toDouble())
                    GL11.glTranslated(8.0, 8.0, 0.0)
                    GL11.glRotated(pitch, 1.0, 0.0, 0.0)
                    GL11.glRotated(yaw, 0.0, 1.0, 0.0)
                    GL11.glTranslated(-8.0, -8.0, 0.0)
                    itemRenderer.renderItemAndEffectIntoGUI(mc.fontRendererObj, mc.textureManager, stack, 0, 0)
                }
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
