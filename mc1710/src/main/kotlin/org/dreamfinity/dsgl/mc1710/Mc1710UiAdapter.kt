package org.dreamfinity.dsgl.mc1710

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.entity.RenderItem
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.dreamfinity.dsgl.core.dom.layout.FontLineMetrics
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.host.Viewport
import org.dreamfinity.dsgl.core.host.dsglRectToGlScissor
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.mc1710.scissorsHelper.ScissorContext
import org.dreamfinity.dsgl.mc1710.text.MsdfTextRenderer
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.Display
import org.lwjgl.opengl.*
import java.io.File
import java.net.URL
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import javax.imageio.ImageIO

internal fun buildMc1710Viewport(
    logicalWidth: Int,
    logicalHeight: Int,
    framebufferWidth: Int,
    framebufferHeight: Int
): Viewport {
    val safeLogicalWidth = logicalWidth.coerceAtLeast(1)
    val safeLogicalHeight = logicalHeight.coerceAtLeast(1)
    val safeFramebufferWidth = framebufferWidth.coerceAtLeast(1)
    val safeFramebufferHeight = framebufferHeight.coerceAtLeast(1)
    val scaleX = safeFramebufferWidth.toFloat() / safeLogicalWidth.toFloat()
    val scaleY = safeFramebufferHeight.toFloat() / safeLogicalHeight.toFloat()
    val scale = min(scaleX, scaleY)
    return Viewport(
        width = safeLogicalWidth,
        height = safeLogicalHeight,
        scale = scale.coerceAtLeast(1f),
        framebufferWidth = safeFramebufferWidth,
        framebufferHeight = safeFramebufferHeight,
        x = 0,
        y = 0
    )
}

internal data class FramebufferSamplePoint(
    val framebufferX: Int,
    val framebufferYTop: Int
)

internal data class LogicalFramebufferRegion(
    val logicalX: Int,
    val logicalY: Int,
    val logicalWidth: Int,
    val logicalHeight: Int,
    val framebufferX: Int,
    val framebufferYTop: Int,
    val framebufferWidth: Int,
    val framebufferHeight: Int
)

internal data class LogicalFramebufferReadbackPlan(
    val sourceRegion: LogicalFramebufferRegion,
    val outputOffsetX: Int,
    val outputOffsetY: Int
)

private enum class ReadbackApi {
    OpenGl30,
    ArbFramebufferObject,
    ExtFramebufferObject,
    Legacy
}

private data class ReadbackBindingState(
    val readFramebufferBinding: Int,
    val drawFramebufferBinding: Int,
    val framebufferBinding: Int,
    val currentReadBuffer: Int
) {
    val usingFramebufferObject: Boolean
        get() = readFramebufferBinding != 0
}

private data class ReadbackSetup(
    val previousReadBuffer: Int,
    val appliedReadBuffer: Int,
    val shouldRestore: Boolean
)

private data class FramebufferBindingSnapshot(
    val readFramebufferBinding: Int,
    val drawFramebufferBinding: Int,
    val framebufferBinding: Int
)

private data class SceneTextureSource(
    val textureId: Int,
    val textureWidth: Int,
    val textureHeight: Int
)

private data class MagnifierCaptureShader(
    val programId: Int,
    val sourceTextureUniform: Int,
    val sourceOriginUniform: Int,
    val sourceSizeUniform: Int,
    val viewportSizeUniform: Int,
    val sourceTextureSizeUniform: Int,
    val fallbackColorUniform: Int
)

internal fun logicalPointToFramebufferSamplePoint(
    viewport: Viewport,
    logicalX: Int,
    logicalY: Int
): FramebufferSamplePoint? {
    if (
        logicalX < 0 ||
        logicalY < 0 ||
        logicalX >= viewport.logicalWidth ||
        logicalY >= viewport.logicalHeight
    ) return null
    return FramebufferSamplePoint(
        framebufferX = floor(logicalX.toFloat() * viewport.scale).toInt()
            .coerceIn(0, viewport.framebufferWidth - 1),
        framebufferYTop = floor(logicalY.toFloat() * viewport.scale).toInt()
            .coerceIn(0, viewport.framebufferHeight - 1)
    )
}

internal fun logicalRectToFramebufferRegion(
    viewport: Viewport,
    logicalX: Int,
    logicalY: Int,
    logicalWidth: Int,
    logicalHeight: Int
): LogicalFramebufferRegion? {
    if (logicalWidth <= 0 || logicalHeight <= 0) return null
    val logicalLeft = logicalX.coerceIn(0, viewport.logicalWidth)
    val logicalTop = logicalY.coerceIn(0, viewport.logicalHeight)
    val logicalRight = (logicalX.toLong() + logicalWidth.toLong())
        .coerceIn(0L, viewport.logicalWidth.toLong())
        .toInt()
    val logicalBottom = (logicalY.toLong() + logicalHeight.toLong())
        .coerceIn(0L, viewport.logicalHeight.toLong())
        .toInt()
    if (logicalRight <= logicalLeft || logicalBottom <= logicalTop) return null

    val framebufferLeft = floor(logicalLeft.toFloat() * viewport.scale).toInt()
        .coerceIn(0, viewport.framebufferWidth)
    val framebufferTop = floor(logicalTop.toFloat() * viewport.scale).toInt()
        .coerceIn(0, viewport.framebufferHeight)
    val framebufferRight = ceil(logicalRight.toFloat() * viewport.scale).toInt()
        .coerceIn(framebufferLeft, viewport.framebufferWidth)
    val framebufferBottom = ceil(logicalBottom.toFloat() * viewport.scale).toInt()
        .coerceIn(framebufferTop, viewport.framebufferHeight)
    if (framebufferRight <= framebufferLeft || framebufferBottom <= framebufferTop) return null

    return LogicalFramebufferRegion(
        logicalX = logicalLeft,
        logicalY = logicalTop,
        logicalWidth = logicalRight - logicalLeft,
        logicalHeight = logicalBottom - logicalTop,
        framebufferX = framebufferLeft,
        framebufferYTop = framebufferTop,
        framebufferWidth = framebufferRight - framebufferLeft,
        framebufferHeight = framebufferBottom - framebufferTop
    )
}

internal fun planLogicalFramebufferReadback(
    viewport: Viewport,
    logicalX: Int,
    logicalY: Int,
    logicalWidth: Int,
    logicalHeight: Int
): LogicalFramebufferReadbackPlan? {
    val sourceRegion = logicalRectToFramebufferRegion(
        viewport = viewport,
        logicalX = logicalX,
        logicalY = logicalY,
        logicalWidth = logicalWidth,
        logicalHeight = logicalHeight
    ) ?: return null
    return LogicalFramebufferReadbackPlan(
        sourceRegion = sourceRegion,
        outputOffsetX = sourceRegion.logicalX - logicalX,
        outputOffsetY = sourceRegion.logicalY - logicalY
    )
}

internal fun framebufferOffsetForLogicalCoordinate(
    logicalCoordinate: Int,
    framebufferStart: Int,
    framebufferLimitExclusive: Int,
    scale: Float
): Int {
    val framebufferCoordinate = floor(logicalCoordinate.toFloat() * scale).toInt()
    return (framebufferCoordinate - framebufferStart)
        .coerceIn(0, (framebufferLimitExclusive - framebufferStart - 1).coerceAtLeast(0))
}

/**
 * Minecraft 1.7.10 adapter that turns DSGL render commands into Minecraft calls.
 */
class Mc1710UiAdapter(private val mc: Minecraft, var paintsCount: Long = 0L) : UiMeasureContext {
    companion object {
        private val imageCache: MutableMap<String, ResourceLocation> = HashMap()
        private val dynamicTexturesCache: MutableMap<String, DynamicTexture> = HashMap()
        private val MAGNIFIER_CAPTURE_VERTEX_SHADER: String = """
            #version 120
            varying vec2 vUv;
            void main() {
                gl_Position = gl_Vertex;
                vUv = gl_MultiTexCoord0.xy;
            }
        """.trimIndent()
        private val MAGNIFIER_CAPTURE_FRAGMENT_SHADER: String = """
            #version 120
            uniform sampler2D uSourceTexture;
            uniform vec2 uSourceOriginTopLeftPx;
            uniform vec2 uSourceSizePx;
            uniform vec2 uViewportSizePx;
            uniform vec2 uSourceTextureSizePx;
            uniform vec4 uFallbackColor;
            varying vec2 vUv;
            void main() {
                vec2 dstPixel = floor(vUv * uSourceSizePx);
                float sourceX = uSourceOriginTopLeftPx.x + dstPixel.x;
                float sourceYTop = uSourceOriginTopLeftPx.y + (uSourceSizePx.y - 1.0 - dstPixel.y);
                bool inside =
                    sourceX >= 0.0 &&
                    sourceYTop >= 0.0 &&
                    sourceX < uViewportSizePx.x &&
                    sourceYTop < uViewportSizePx.y;
                if (!inside) {
                    gl_FragColor = uFallbackColor;
                    return;
                }
                float sourceYBottom = (uViewportSizePx.y - 1.0) - sourceYTop;
                vec2 sourceUv = vec2(
                    (sourceX + 0.5) / uSourceTextureSizePx.x,
                    (sourceYBottom + 0.5) / uSourceTextureSizePx.y
                );
                vec4 sampled = texture2D(uSourceTexture, sourceUv);
                gl_FragColor = vec4(sampled.rgb, 1.0);
            }
        """.trimIndent()
    }

    private val itemRenderer: RenderItem = RenderItem()
    private val textRenderer: MsdfTextRenderer = MsdfTextRenderer()
    private val opacityStack: MutableList<Float> = ArrayList(8)
    private var opacityMultiplier: Float = 1f
    private val errorLogTimes: MutableMap<String, Long> = linkedMapOf()
    private val readbackDiagnosticsVerbose: Boolean = java.lang.Boolean.getBoolean("dsgl.readback.diagnostics.verbose")
    private val viewportDiagnosticsVerbose: Boolean = java.lang.Boolean.getBoolean("dsgl.viewport.diagnostics.verbose")
    private val readbackApi: ReadbackApi by lazy(LazyThreadSafetyMode.NONE) { resolveReadbackApi() }

    private val samplePixelBuffer = BufferUtils.createByteBuffer(4)
    private var sampleAreaBuffer = BufferUtils.createByteBuffer(4 * 256)
    private val glIntStateQueryBuffer = BufferUtils.createIntBuffer(16)
    private val glFloatStateQueryBuffer = BufferUtils.createFloatBuffer(16)

    private var capturedRegionTextureId: Int = 0
    private var capturedRegionFramebufferId: Int = 0
    private var capturedRegionWidth: Int = 0
    private var capturedRegionHeight: Int = 0
    private var capturedRegionValid: Boolean = false
    private var capturedRegionFallbackColor: Int = 0xFF000000.toInt()
    private var magnifierCaptureShader: MagnifierCaptureShader? = null
    private var magnifierCaptureShaderInitFailed: Boolean = false

    private val checkerTextureCache: LinkedHashMap<Long, Int> = LinkedHashMap(16, 0.75f, true)
    private val checkerTextureUploadBuffer = BufferUtils.createByteBuffer(16)
    private val maxCheckerTextures: Int = 32

    private var cachedViewport: Viewport = Viewport(width = 1, height = 1, scale = 1f, x = 0, y = 0)
    private var cachedDisplayWidth: Int = -1
    private var cachedDisplayHeight: Int = -1
    private var cachedLogicalWidth: Int = -1
    private var cachedLogicalHeight: Int = -1

    override fun measureText(text: String): Int = textRenderer.measureText(text, null, null)
    override fun measureText(text: String, fontId: String?, fontSize: Int?): Int {
        return textRenderer.measureText(text, fontId, fontSize)
    }
    override fun measureTextRange(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        fontId: String?,
        fontSize: Int?
    ): Int {
        return textRenderer.measureTextRange(text, startIndex, endIndexExclusive, fontId, fontSize)
    }

    override val fontHeight: Int
        get() = textRenderer.lineHeight(FontRegistry.DEFAULT_FONT_ID, null)

    override fun fontHeight(fontId: String?, fontSize: Int?): Int {
        return textRenderer.lineHeight(fontId, fontSize)
    }

    override fun fontLineMetrics(fontId: String?, fontSize: Int?): FontLineMetrics? {
        return textRenderer.fontLineMetrics(fontId, fontSize)
    }

    fun updateViewport(logicalWidth: Int, logicalHeight: Int) {
        refreshViewport(logicalWidth, logicalHeight)
    }

    fun viewport(): Viewport {
        refreshViewport(cachedLogicalWidth, cachedLogicalHeight)
        return cachedViewport
    }

    private fun refreshViewport(logicalWidth: Int, logicalHeight: Int) {
        val displayWidth = mc.displayWidth.coerceAtLeast(1)
        val displayHeight = mc.displayHeight.coerceAtLeast(1)
        val safeLogicalWidth = logicalWidth.coerceAtLeast(1)
        val safeLogicalHeight = logicalHeight.coerceAtLeast(1)
        if (
            displayWidth != cachedDisplayWidth ||
            displayHeight != cachedDisplayHeight ||
            safeLogicalWidth != cachedLogicalWidth ||
            safeLogicalHeight != cachedLogicalHeight
        ) {
            cachedDisplayWidth = displayWidth
            cachedDisplayHeight = displayHeight
            cachedLogicalWidth = safeLogicalWidth
            cachedLogicalHeight = safeLogicalHeight
            cachedViewport = buildMc1710Viewport(
                logicalWidth = safeLogicalWidth,
                logicalHeight = safeLogicalHeight,
                framebufferWidth = displayWidth,
                framebufferHeight = displayHeight
            )
            logViewportDiagnosticsIfEnabled()
        }
    }

    private fun logViewportDiagnosticsIfEnabled() {
        if (!viewportDiagnosticsVerbose) return
        val pixelScaleFactor = runCatching { Display.getPixelScaleFactor() }.getOrNull()
        logRateLimited(
            key = "viewport:contract",
            message = buildString {
                append("[DSGL-Viewport] logical=")
                append(cachedViewport.logicalWidth).append('x').append(cachedViewport.logicalHeight)
                append(" framebuffer=")
                append(cachedViewport.framebufferWidth).append('x').append(cachedViewport.framebufferHeight)
                append(" scale=").append(cachedViewport.scale)
                if (pixelScaleFactor != null) {
                    append(" displayPixelScale=").append(pixelScaleFactor)
                }
            }
        )
    }

    fun sampleScreenColor(x: Int, y: Int): Int? {
        val viewport = viewport()
        val samplePoint = logicalPointToFramebufferSamplePoint(viewport, x, y) ?: return null
        val readX = samplePoint.framebufferX
        val readY = (viewport.framebufferHeight - 1 - samplePoint.framebufferYTop)
            .coerceIn(0, viewport.framebufferHeight - 1)
        samplePixelBuffer.clear()
        return try {
            val setup = beginReadback()
            if (readbackDiagnosticsVerbose) {
                diagnoseReadbackSource(
                    path = "sampleScreenColor",
                    sourceX = x,
                    sourceY = y,
                    sourceWidth = 1,
                    sourceHeight = 1,
                    setup = setup
                )
            }
            try {
                GL11.glReadPixels(readX, readY, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, samplePixelBuffer)
            } finally {
                endReadback(setup)
            }
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
        val readbackPlan = planLogicalFramebufferReadback(viewport, x, y, width, height) ?: return false
        val sourceRegion = readbackPlan.sourceRegion
        val byteCount = sourceRegion.framebufferWidth * sourceRegion.framebufferHeight * 4
        if (sampleAreaBuffer.capacity() < byteCount) {
            sampleAreaBuffer = BufferUtils.createByteBuffer(byteCount)
        }
        sampleAreaBuffer.clear()
        sampleAreaBuffer.limit(byteCount)
        return try {
            val readY = viewport.framebufferHeight - (
                sourceRegion.framebufferYTop + sourceRegion.framebufferHeight
            )
            val setup = beginReadback()
            if (readbackDiagnosticsVerbose) {
                diagnoseReadbackSource(
                    path = "sampleScreenArea",
                    sourceX = sourceRegion.framebufferX,
                    sourceY = sourceRegion.framebufferYTop,
                    sourceWidth = sourceRegion.framebufferWidth,
                    sourceHeight = sourceRegion.framebufferHeight,
                    setup = setup
                )
            }
            try {
                GL11.glReadPixels(
                    sourceRegion.framebufferX,
                    readY,
                    sourceRegion.framebufferWidth,
                    sourceRegion.framebufferHeight,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    sampleAreaBuffer
                )
            } finally {
                endReadback(setup)
            }
            val dstOffsetX = readbackPlan.outputOffsetX
            val dstOffsetY = readbackPlan.outputOffsetY
            var logicalRow = 0
            while (logicalRow < sourceRegion.logicalHeight) {
                val absoluteLogicalY = sourceRegion.logicalY + logicalRow
                val framebufferLocalY = framebufferOffsetForLogicalCoordinate(
                    logicalCoordinate = absoluteLogicalY,
                    framebufferStart = sourceRegion.framebufferYTop,
                    framebufferLimitExclusive = sourceRegion.framebufferYTop + sourceRegion.framebufferHeight,
                    scale = viewport.scale
                )
                val glRow = sourceRegion.framebufferHeight - 1 - framebufferLocalY
                var logicalCol = 0
                while (logicalCol < sourceRegion.logicalWidth) {
                    val absoluteLogicalX = sourceRegion.logicalX + logicalCol
                    val framebufferLocalX = framebufferOffsetForLogicalCoordinate(
                        logicalCoordinate = absoluteLogicalX,
                        framebufferStart = sourceRegion.framebufferX,
                        framebufferLimitExclusive = sourceRegion.framebufferX + sourceRegion.framebufferWidth,
                        scale = viewport.scale
                    )
                    val srcIndex = (glRow * sourceRegion.framebufferWidth + framebufferLocalX) * 4
                    val r = sampleAreaBuffer.get(srcIndex).toInt() and 0xFF
                    val g = sampleAreaBuffer.get(srcIndex + 1).toInt() and 0xFF
                    val b = sampleAreaBuffer.get(srcIndex + 2).toInt() and 0xFF
                    val a = sampleAreaBuffer.get(srcIndex + 3).toInt() and 0xFF
                    val dstX = dstOffsetX + logicalCol
                    val dstY = dstOffsetY + logicalRow
                    outArgb[dstY * width + dstX] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    logicalCol++
                }
                logicalRow++
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun captureScreenRegion(command: RenderCommand.CaptureScreenRegion, viewport: Viewport) {
        val sourceRegion = logicalRectToFramebufferRegion(
            viewport = viewport,
            logicalX = command.sourceX,
            logicalY = command.sourceY,
            logicalWidth = command.sourceWidth.coerceAtLeast(1),
            logicalHeight = command.sourceHeight.coerceAtLeast(1)
        )
        capturedRegionFallbackColor = command.fallbackColor
        if (sourceRegion == null) {
            capturedRegionValid = false
            return
        }
        ensureCapturedRegionTexture(sourceRegion.framebufferWidth, sourceRegion.framebufferHeight)
        if (capturedRegionTextureId == 0) {
            capturedRegionValid = false
            return
        }
        val sceneTextureSource = resolveActiveSceneTextureSource()
        val shader = ensureMagnifierCaptureShader()
        if (sceneTextureSource == null || shader == null || sceneTextureSource.textureId == capturedRegionTextureId) {
            capturedRegionValid = fillCapturedRegionFallbackTexture(
                command.fallbackColor,
                sourceRegion.framebufferWidth,
                sourceRegion.framebufferHeight
            )
            if (readbackDiagnosticsVerbose) {
                logRateLimited(
                    key = "magnifier:capture:fallback",
                    message = "[DSGL-Magnifier] Falling back to solid fill preview. sourceTexture=${sceneTextureSource?.textureId ?: 0} shaderReady=${shader != null}"
                )
            }
            return
        }
        val framebufferSnapshot = snapshotFramebufferBindings()
        val previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER)
        val previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER)
        val previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        snapshotViewportState()
        val previousViewportX = viewportXFromSnapshot()
        val previousViewportY = viewportYFromSnapshot()
        val previousViewportWidth = viewportWidthFromSnapshot()
        val previousViewportHeight = viewportHeightFromSnapshot()
        var renderingSucceeded = false
        try {
            if (!ensureCapturedRegionFramebuffer()) {
                return
            }
            bindDrawFramebuffer(capturedRegionFramebufferId)
            attachCapturedRegionTextureToFramebuffer()
            if (!isCurrentFramebufferComplete()) {
                return
            }
            val attachment = defaultColorAttachmentReadBuffer()
            GL11.glDrawBuffer(attachment)
            GL11.glReadBuffer(attachment)
            GL11.glViewport(0, 0, sourceRegion.framebufferWidth, sourceRegion.framebufferHeight)
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            GL11.glDisable(GL11.GL_BLEND)
            GL11.glDisable(GL11.GL_CULL_FACE)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            ARBShaderObjects.glUseProgramObjectARB(shader.programId)
            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTextureSource.textureId)
            ARBShaderObjects.glUniform1iARB(shader.sourceTextureUniform, 0)
            ARBShaderObjects.glUniform2fARB(
                shader.sourceOriginUniform,
                sourceRegion.framebufferX.toFloat(),
                sourceRegion.framebufferYTop.toFloat()
            )
            ARBShaderObjects.glUniform2fARB(
                shader.sourceSizeUniform,
                sourceRegion.framebufferWidth.toFloat(),
                sourceRegion.framebufferHeight.toFloat()
            )
            ARBShaderObjects.glUniform2fARB(
                shader.viewportSizeUniform,
                viewport.framebufferWidth.toFloat(),
                viewport.framebufferHeight.toFloat()
            )
            ARBShaderObjects.glUniform2fARB(
                shader.sourceTextureSizeUniform,
                sceneTextureSource.textureWidth.toFloat(),
                sceneTextureSource.textureHeight.toFloat()
            )
            val fallbackAlpha = ((command.fallbackColor ushr 24) and 0xFF) / 255f
            val fallbackRed = ((command.fallbackColor ushr 16) and 0xFF) / 255f
            val fallbackGreen = ((command.fallbackColor ushr 8) and 0xFF) / 255f
            val fallbackBlue = (command.fallbackColor and 0xFF) / 255f
            ARBShaderObjects.glUniform4fARB(
                shader.fallbackColorUniform,
                fallbackRed,
                fallbackGreen,
                fallbackBlue,
                fallbackAlpha
            )
            GL11.glColor4f(1f, 1f, 1f, 1f)
            GL11.glBegin(GL11.GL_QUADS)
            GL11.glTexCoord2f(0f, 0f)
            GL11.glVertex2f(-1f, -1f)
            GL11.glTexCoord2f(1f, 0f)
            GL11.glVertex2f(1f, -1f)
            GL11.glTexCoord2f(1f, 1f)
            GL11.glVertex2f(1f, 1f)
            GL11.glTexCoord2f(0f, 1f)
            GL11.glVertex2f(-1f, 1f)
            GL11.glEnd()
            renderingSucceeded = true
        } catch (error: Throwable) {
            if (readbackDiagnosticsVerbose) {
                logRateLimited(
                    key = "magnifier:capture:error",
                    message = "[DSGL-Magnifier] GPU capture failed: ${error.message ?: error::class.java.simpleName}"
                )
            }
        } finally {
            ARBShaderObjects.glUseProgramObjectARB(0)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureBinding)
            GL11.glReadBuffer(previousReadBuffer)
            GL11.glDrawBuffer(previousDrawBuffer)
            restoreFramebufferBindings(framebufferSnapshot)
            GL11.glViewport(
                previousViewportX,
                previousViewportY,
                previousViewportWidth,
                previousViewportHeight
            )
        }
        capturedRegionValid =
            renderingSucceeded || fillCapturedRegionFallbackTexture(
                command.fallbackColor,
                sourceRegion.framebufferWidth,
                sourceRegion.framebufferHeight
            )
    }

    private fun drawCapturedScreenRegion(command: RenderCommand.DrawCapturedScreenRegion) {
        if (command.width <= 0 || command.height <= 0) return
        if (!capturedRegionValid || capturedRegionTextureId == 0) {
            Gui.drawRect(
                command.x,
                command.y,
                command.x + command.width,
                command.y + command.height,
                applyOpacity(capturedRegionFallbackColor)
            )
            return
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, capturedRegionTextureId)
        GL11.glColor4f(1f, 1f, 1f, opacityMultiplier.coerceIn(0f, 1f))
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(0f, 1f)
        GL11.glVertex2f(command.x.toFloat(), command.y.toFloat())
        GL11.glTexCoord2f(1f, 1f)
        GL11.glVertex2f((command.x + command.width).toFloat(), command.y.toFloat())
        GL11.glTexCoord2f(1f, 0f)
        GL11.glVertex2f((command.x + command.width).toFloat(), (command.y + command.height).toFloat())
        GL11.glTexCoord2f(0f, 0f)
        GL11.glVertex2f(command.x.toFloat(), (command.y + command.height).toFloat())
        GL11.glEnd()
    }

    private fun drawCheckerboard(command: RenderCommand.DrawCheckerboard) {
        if (command.width <= 0 || command.height <= 0) return
        val cellSize = command.cellSize.coerceAtLeast(1)
        val textureId = resolveCheckerTextureId(command.lightColor, command.darkColor)
        if (textureId == 0) {
            Gui.drawRect(
                command.x,
                command.y,
                command.x + command.width,
                command.y + command.height,
                applyOpacity(command.lightColor)
            )
            return
        }
        val patternSize = (cellSize * 2f).coerceAtLeast(1f)
        val u0 = (command.x + command.offsetX) / patternSize
        val v0 = (command.y + command.offsetY) / patternSize
        val u1 = u0 + (command.width / patternSize)
        val v1 = v0 + (command.height / patternSize)

        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
        GL11.glColor4f(1f, 1f, 1f, opacityMultiplier.coerceIn(0f, 1f))
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(u0, v0)
        GL11.glVertex2f(command.x.toFloat(), command.y.toFloat())
        GL11.glTexCoord2f(u1, v0)
        GL11.glVertex2f((command.x + command.width).toFloat(), command.y.toFloat())
        GL11.glTexCoord2f(u1, v1)
        GL11.glVertex2f((command.x + command.width).toFloat(), (command.y + command.height).toFloat())
        GL11.glTexCoord2f(u0, v1)
        GL11.glVertex2f(command.x.toFloat(), (command.y + command.height).toFloat())
        GL11.glEnd()
    }

    private fun resolveCheckerTextureId(lightColor: Int, darkColor: Int): Int {
        val key = checkerTextureKey(lightColor, darkColor)
        checkerTextureCache[key]?.let { return it }

        val textureId = GL11.glGenTextures()
        if (textureId == 0) return 0
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT)

        val light = argbToRgbaBytes(lightColor)
        val dark = argbToRgbaBytes(darkColor)
        checkerTextureUploadBuffer.clear()
        checkerTextureUploadBuffer.put(light[0]).put(light[1]).put(light[2]).put(light[3])
        checkerTextureUploadBuffer.put(dark[0]).put(dark[1]).put(dark[2]).put(dark[3])
        checkerTextureUploadBuffer.put(dark[0]).put(dark[1]).put(dark[2]).put(dark[3])
        checkerTextureUploadBuffer.put(light[0]).put(light[1]).put(light[2]).put(light[3])
        checkerTextureUploadBuffer.flip()

        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA,
            2,
            2,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            checkerTextureUploadBuffer
        )

        checkerTextureCache[key] = textureId
        while (checkerTextureCache.size > maxCheckerTextures) {
            val eldest = checkerTextureCache.entries.iterator().next()
            GL11.glDeleteTextures(eldest.value)
            checkerTextureCache.remove(eldest.key)
        }
        return textureId
    }

    private fun checkerTextureKey(lightColor: Int, darkColor: Int): Long {
        return (lightColor.toLong() shl 32) xor (darkColor.toLong() and 0xFFFF_FFFFL)
    }

    private fun ensureCapturedRegionTexture(width: Int, height: Int) {
        if (capturedRegionTextureId == 0) {
            capturedRegionTextureId = GL11.glGenTextures()
            if (capturedRegionTextureId == 0) return
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, capturedRegionTextureId)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP)
            capturedRegionWidth = 0
            capturedRegionHeight = 0
        }
        if (capturedRegionWidth == width && capturedRegionHeight == height) return
        capturedRegionWidth = width
        capturedRegionHeight = height
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, capturedRegionTextureId)
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA,
            capturedRegionWidth,
            capturedRegionHeight,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            null as java.nio.ByteBuffer?
        )
    }

    private fun ensureCapturedRegionFramebuffer(): Boolean {
        if (capturedRegionFramebufferId != 0) return true
        capturedRegionFramebufferId = generateFramebufferObject()
        return capturedRegionFramebufferId != 0
    }

    private fun resolveActiveSceneTextureSource(): SceneTextureSource? {
        val state = detectReadbackBindingState()
        if (!state.usingFramebufferObject) return null
        val colorAttachment = if (isColorAttachmentReadBuffer(state.currentReadBuffer)) {
            state.currentReadBuffer
        } else {
            defaultColorAttachmentReadBuffer()
        }
        val objectType = getFramebufferAttachmentObjectType(colorAttachment)
        if (objectType != GL11.GL_TEXTURE) return null
        val textureId = getFramebufferAttachmentObjectName(colorAttachment)
        if (textureId <= 0) return null
        val previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        return try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            val textureWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)
            val textureHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT)
            if (textureWidth <= 0 || textureHeight <= 0) return null
            SceneTextureSource(textureId = textureId, textureWidth = textureWidth, textureHeight = textureHeight)
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureBinding)
        }
    }

    private fun getFramebufferAttachmentObjectType(colorAttachment: Int): Int {
        return when (readbackApi) {
            ReadbackApi.OpenGl30 -> GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_READ_FRAMEBUFFER,
                colorAttachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            )

            ReadbackApi.ArbFramebufferObject -> ARBFramebufferObject.glGetFramebufferAttachmentParameteri(
                ARBFramebufferObject.GL_READ_FRAMEBUFFER,
                colorAttachment,
                ARBFramebufferObject.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            )

            ReadbackApi.ExtFramebufferObject -> EXTFramebufferObject.glGetFramebufferAttachmentParameteriEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                colorAttachment,
                EXTFramebufferObject.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE_EXT
            )

            ReadbackApi.Legacy -> GL11.GL_NONE
        }
    }

    private fun getFramebufferAttachmentObjectName(colorAttachment: Int): Int {
        return when (readbackApi) {
            ReadbackApi.OpenGl30 -> GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_READ_FRAMEBUFFER,
                colorAttachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            )

            ReadbackApi.ArbFramebufferObject -> ARBFramebufferObject.glGetFramebufferAttachmentParameteri(
                ARBFramebufferObject.GL_READ_FRAMEBUFFER,
                colorAttachment,
                ARBFramebufferObject.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            )

            ReadbackApi.ExtFramebufferObject -> EXTFramebufferObject.glGetFramebufferAttachmentParameteriEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                colorAttachment,
                EXTFramebufferObject.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME_EXT
            )

            ReadbackApi.Legacy -> 0
        }
    }

    private fun ensureMagnifierCaptureShader(): MagnifierCaptureShader? {
        magnifierCaptureShader?.let { return it }
        if (magnifierCaptureShaderInitFailed) return null
        return try {
            val vertexShader = compileShaderObject(
                type = ARBVertexShader.GL_VERTEX_SHADER_ARB,
                source = MAGNIFIER_CAPTURE_VERTEX_SHADER
            )
            val fragmentShader = compileShaderObject(
                type = ARBFragmentShader.GL_FRAGMENT_SHADER_ARB,
                source = MAGNIFIER_CAPTURE_FRAGMENT_SHADER
            )
            val program = ARBShaderObjects.glCreateProgramObjectARB()
            ARBShaderObjects.glAttachObjectARB(program, vertexShader)
            ARBShaderObjects.glAttachObjectARB(program, fragmentShader)
            ARBShaderObjects.glLinkProgramARB(program)
            val linkStatus = ARBShaderObjects.glGetObjectParameteriARB(
                program,
                ARBShaderObjects.GL_OBJECT_LINK_STATUS_ARB
            )
            if (linkStatus == GL11.GL_FALSE) {
                val info = ARBShaderObjects.glGetInfoLogARB(program, 4096)
                throw IllegalStateException("Magnifier shader link failed: $info")
            }
            val shader = MagnifierCaptureShader(
                programId = program,
                sourceTextureUniform = ARBShaderObjects.glGetUniformLocationARB(program, "uSourceTexture"),
                sourceOriginUniform = ARBShaderObjects.glGetUniformLocationARB(program, "uSourceOriginTopLeftPx"),
                sourceSizeUniform = ARBShaderObjects.glGetUniformLocationARB(program, "uSourceSizePx"),
                viewportSizeUniform = ARBShaderObjects.glGetUniformLocationARB(program, "uViewportSizePx"),
                sourceTextureSizeUniform = ARBShaderObjects.glGetUniformLocationARB(program, "uSourceTextureSizePx"),
                fallbackColorUniform = ARBShaderObjects.glGetUniformLocationARB(program, "uFallbackColor")
            )
            magnifierCaptureShader = shader
            shader
        } catch (error: Throwable) {
            magnifierCaptureShaderInitFailed = true
            if (readbackDiagnosticsVerbose) {
                logRateLimited(
                    key = "magnifier:shader:init",
                    message = "[DSGL-Magnifier] Failed to initialize capture shader: ${error.message ?: error::class.java.simpleName}"
                )
            }
            null
        }
    }

    private fun compileShaderObject(type: Int, source: String): Int {
        val shader = ARBShaderObjects.glCreateShaderObjectARB(type)
        ARBShaderObjects.glShaderSourceARB(shader, source)
        ARBShaderObjects.glCompileShaderARB(shader)
        val compileStatus = ARBShaderObjects.glGetObjectParameteriARB(
            shader,
            ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB
        )
        if (compileStatus == GL11.GL_FALSE) {
            val info = ARBShaderObjects.glGetInfoLogARB(shader, 4096)
            throw IllegalStateException("Magnifier shader compile failed: $info")
        }
        return shader
    }

    private fun generateFramebufferObject(): Int {
        return when (readbackApi) {
            ReadbackApi.OpenGl30 -> GL30.glGenFramebuffers()
            ReadbackApi.ArbFramebufferObject -> ARBFramebufferObject.glGenFramebuffers()
            ReadbackApi.ExtFramebufferObject -> EXTFramebufferObject.glGenFramebuffersEXT()
            ReadbackApi.Legacy -> 0
        }
    }

    private fun snapshotFramebufferBindings(): FramebufferBindingSnapshot {
        return FramebufferBindingSnapshot(
            readFramebufferBinding = currentReadFramebufferBinding(),
            drawFramebufferBinding = currentDrawFramebufferBinding(),
            framebufferBinding = currentFramebufferBinding()
        )
    }

    private fun restoreFramebufferBindings(snapshot: FramebufferBindingSnapshot) {
        when (readbackApi) {
            ReadbackApi.OpenGl30 -> {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.readFramebufferBinding)
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.drawFramebufferBinding)
            }

            ReadbackApi.ArbFramebufferObject -> {
                ARBFramebufferObject.glBindFramebuffer(
                    ARBFramebufferObject.GL_READ_FRAMEBUFFER,
                    snapshot.readFramebufferBinding
                )
                ARBFramebufferObject.glBindFramebuffer(
                    ARBFramebufferObject.GL_DRAW_FRAMEBUFFER,
                    snapshot.drawFramebufferBinding
                )
            }

            ReadbackApi.ExtFramebufferObject -> {
                EXTFramebufferObject.glBindFramebufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    snapshot.framebufferBinding
                )
            }

            ReadbackApi.Legacy -> Unit
        }
    }

    private fun bindDrawFramebuffer(framebufferId: Int) {
        when (readbackApi) {
            ReadbackApi.OpenGl30 -> GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebufferId)
            ReadbackApi.ArbFramebufferObject -> ARBFramebufferObject.glBindFramebuffer(
                ARBFramebufferObject.GL_DRAW_FRAMEBUFFER,
                framebufferId
            )

            ReadbackApi.ExtFramebufferObject -> EXTFramebufferObject.glBindFramebufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                framebufferId
            )

            ReadbackApi.Legacy -> Unit
        }
    }

    private fun attachCapturedRegionTextureToFramebuffer() {
        when (readbackApi) {
            ReadbackApi.OpenGl30 -> GL30.glFramebufferTexture2D(
                GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                capturedRegionTextureId,
                0
            )

            ReadbackApi.ArbFramebufferObject -> ARBFramebufferObject.glFramebufferTexture2D(
                ARBFramebufferObject.GL_DRAW_FRAMEBUFFER,
                ARBFramebufferObject.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                capturedRegionTextureId,
                0
            )

            ReadbackApi.ExtFramebufferObject -> EXTFramebufferObject.glFramebufferTexture2DEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT,
                GL11.GL_TEXTURE_2D,
                capturedRegionTextureId,
                0
            )

            ReadbackApi.Legacy -> Unit
        }
    }

    private fun isCurrentFramebufferComplete(): Boolean {
        return when (readbackApi) {
            ReadbackApi.OpenGl30 -> GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE
            ReadbackApi.ArbFramebufferObject -> ARBFramebufferObject.glCheckFramebufferStatus(
                ARBFramebufferObject.GL_DRAW_FRAMEBUFFER
            ) == ARBFramebufferObject.GL_FRAMEBUFFER_COMPLETE

            ReadbackApi.ExtFramebufferObject -> EXTFramebufferObject.glCheckFramebufferStatusEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT
            ) == EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT

            ReadbackApi.Legacy -> false
        }
    }

    private fun fillCapturedRegionFallbackTexture(
        fallbackColor: Int,
        width: Int,
        height: Int
    ): Boolean {
        val snapshot = snapshotFramebufferBindings()
        val previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER)
        val previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER)
        snapshotViewportState()
        val previousViewportX = viewportXFromSnapshot()
        val previousViewportY = viewportYFromSnapshot()
        val previousViewportWidth = viewportWidthFromSnapshot()
        val previousViewportHeight = viewportHeightFromSnapshot()
        snapshotClearColorState()
        val previousClearRed = clearRedFromSnapshot()
        val previousClearGreen = clearGreenFromSnapshot()
        val previousClearBlue = clearBlueFromSnapshot()
        val previousClearAlpha = clearAlphaFromSnapshot()
        return try {
            if (!ensureCapturedRegionFramebuffer()) return false
            bindDrawFramebuffer(capturedRegionFramebufferId)
            attachCapturedRegionTextureToFramebuffer()
            if (!isCurrentFramebufferComplete()) return false
            val attachment = defaultColorAttachmentReadBuffer()
            GL11.glDrawBuffer(attachment)
            GL11.glViewport(0, 0, width, height)
            val alpha = ((fallbackColor ushr 24) and 0xFF) / 255f
            val red = ((fallbackColor ushr 16) and 0xFF) / 255f
            val green = ((fallbackColor ushr 8) and 0xFF) / 255f
            val blue = (fallbackColor and 0xFF) / 255f
            GL11.glClearColor(red, green, blue, alpha)
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
            true
        } catch (_: Throwable) {
            false
        } finally {
            GL11.glClearColor(
                previousClearRed,
                previousClearGreen,
                previousClearBlue,
                previousClearAlpha
            )
            GL11.glReadBuffer(previousReadBuffer)
            GL11.glDrawBuffer(previousDrawBuffer)
            restoreFramebufferBindings(snapshot)
            GL11.glViewport(
                previousViewportX,
                previousViewportY,
                previousViewportWidth,
                previousViewportHeight
            )
        }
    }

    private fun snapshotViewportState() {
        glIntStateQueryBuffer.clear()
        GL11.glGetInteger(GL11.GL_VIEWPORT, glIntStateQueryBuffer)
    }

    private fun viewportXFromSnapshot(): Int = glIntStateQueryBuffer.get(0)
    private fun viewportYFromSnapshot(): Int = glIntStateQueryBuffer.get(1)
    private fun viewportWidthFromSnapshot(): Int = glIntStateQueryBuffer.get(2)
    private fun viewportHeightFromSnapshot(): Int = glIntStateQueryBuffer.get(3)

    private fun snapshotClearColorState() {
        glFloatStateQueryBuffer.clear()
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, glFloatStateQueryBuffer)
    }

    private fun clearRedFromSnapshot(): Float = glFloatStateQueryBuffer.get(0)
    private fun clearGreenFromSnapshot(): Float = glFloatStateQueryBuffer.get(1)
    private fun clearBlueFromSnapshot(): Float = glFloatStateQueryBuffer.get(2)
    private fun clearAlphaFromSnapshot(): Float = glFloatStateQueryBuffer.get(3)

    private fun argbToRgbaBytes(argb: Int, forceOpaqueAlpha: Boolean = false): ByteArray {
        val r = ((argb ushr 16) and 0xFF).toByte()
        val g = ((argb ushr 8) and 0xFF).toByte()
        val b = (argb and 0xFF).toByte()
        val a = if (forceOpaqueAlpha) 0xFF.toByte() else ((argb ushr 24) and 0xFF).toByte()
        return byteArrayOf(r, g, b, a)
    }

    /** Executes DSGL render commands using Minecraft rendering APIs. */
    override fun paint(commands: List<RenderCommand>) {
        paintsCount++
        opacityStack.clear()
        opacityMultiplier = 1f
        val transformStack = RenderCommandTransformStack()
        transformStack.reset()
        val viewport = viewport()
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            ScissorContext.clear()
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            GL11.glViewport(
                viewport.framebufferX,
                viewport.framebufferY,
                viewport.framebufferWidth,
                viewport.framebufferHeight
            )
            GL11.glMatrixMode(GL11.GL_PROJECTION)
            GL11.glPushMatrix()
            GL11.glLoadIdentity()
            GL11.glOrtho(
                0.0,
                viewport.logicalWidth.toDouble(),
                viewport.logicalHeight.toDouble(),
                0.0,
                -1000.0,
                1000.0
            )
            GL11.glMatrixMode(GL11.GL_MODELVIEW)
            GL11.glPushMatrix()
            GL11.glLoadIdentity()
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.0f)
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

                        is RenderCommand.DrawCheckerboard -> {
                            drawCheckerboard(command)
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

                        is RenderCommand.CaptureScreenRegion -> {
                            captureScreenRegion(command, viewport)
                        }

                        is RenderCommand.DrawCapturedScreenRegion -> {
                            drawCapturedScreenRegion(command)
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
                            val transformedClip = transformStack.resolveClipRect(
                                x = command.x,
                                y = command.y,
                                width = command.width,
                                height = command.height
                            )
                            pushClip(
                                viewport = viewport,
                                guiX = transformedClip.x,
                                guiY = transformedClip.y,
                                guiWidth = transformedClip.width,
                                guiHeight = transformedClip.height
                            )
                        }

                        is RenderCommand.PopClip -> {
                            ScissorContext.pop()
                        }

                        is RenderCommand.PushTransform -> {
                            transformStack.push(command)
                            GL11.glPushMatrix()
                            GL11.glTranslatef(command.originX, command.originY, 0f)
                            GL11.glTranslatef(command.translateX, command.translateY, 0f)
                            GL11.glRotatef(command.rotateDeg, 0f, 0f, 1f)
                            GL11.glScalef(command.scaleX, command.scaleY, 1f)
                            GL11.glTranslatef(-command.originX, -command.originY, 0f)
                        }

                        is RenderCommand.PopTransform -> {
                            transformStack.pop()
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
                GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f)
                GL11.glMatrixMode(GL11.GL_MODELVIEW)
                GL11.glPopMatrix()
                GL11.glMatrixMode(GL11.GL_PROJECTION)
                GL11.glPopMatrix()
                GL11.glMatrixMode(GL11.GL_MODELVIEW)
            }
        } finally {
            ScissorContext.clear()
            transformStack.reset()
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

    private fun beginReadback(): ReadbackSetup {
        val previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER)
        val desiredReadBuffer = selectReadBufferForActiveTarget(previousReadBuffer)
        if (desiredReadBuffer == previousReadBuffer) {
            return ReadbackSetup(
                previousReadBuffer = previousReadBuffer,
                appliedReadBuffer = desiredReadBuffer,
                shouldRestore = false
            )
        }
        GL11.glReadBuffer(desiredReadBuffer)
        return ReadbackSetup(
            previousReadBuffer = previousReadBuffer,
            appliedReadBuffer = desiredReadBuffer,
            shouldRestore = true
        )
    }

    private fun endReadback(setup: ReadbackSetup) {
        if (!setup.shouldRestore) return
        if (setup.previousReadBuffer == setup.appliedReadBuffer) return
        GL11.glReadBuffer(setup.previousReadBuffer)
    }

    private fun selectReadBufferForActiveTarget(currentReadBuffer: Int): Int {
        val readFramebufferBinding = currentReadFramebufferBinding()
        if (readFramebufferBinding == 0) {
            return GL11.GL_BACK
        }
        if (isColorAttachmentReadBuffer(currentReadBuffer)) {
            return currentReadBuffer
        }
        return defaultColorAttachmentReadBuffer()
    }

    private fun resolveReadbackApi(): ReadbackApi {
        val caps = GLContext.getCapabilities()
        return when {
            caps.OpenGL30 -> ReadbackApi.OpenGl30
            caps.GL_ARB_framebuffer_object -> ReadbackApi.ArbFramebufferObject
            caps.GL_EXT_framebuffer_object -> ReadbackApi.ExtFramebufferObject
            else -> ReadbackApi.Legacy
        }
    }

    private fun currentReadFramebufferBinding(): Int {
        return when (readbackApi) {
            ReadbackApi.OpenGl30 -> GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            ReadbackApi.ArbFramebufferObject -> GL11.glGetInteger(ARBFramebufferObject.GL_READ_FRAMEBUFFER_BINDING)
            ReadbackApi.ExtFramebufferObject -> GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)
            ReadbackApi.Legacy -> 0
        }
    }

    private fun currentDrawFramebufferBinding(): Int {
        return when (readbackApi) {
            ReadbackApi.OpenGl30 -> GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
            ReadbackApi.ArbFramebufferObject -> GL11.glGetInteger(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER_BINDING)
            ReadbackApi.ExtFramebufferObject -> GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)
            ReadbackApi.Legacy -> 0
        }
    }

    private fun currentFramebufferBinding(): Int {
        return when (readbackApi) {
            ReadbackApi.OpenGl30 -> GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
            ReadbackApi.ArbFramebufferObject -> GL11.glGetInteger(ARBFramebufferObject.GL_FRAMEBUFFER_BINDING)
            ReadbackApi.ExtFramebufferObject -> GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)
            ReadbackApi.Legacy -> 0
        }
    }

    private fun defaultColorAttachmentReadBuffer(): Int {
        return when (readbackApi) {
            ReadbackApi.OpenGl30 -> GL30.GL_COLOR_ATTACHMENT0
            ReadbackApi.ArbFramebufferObject -> ARBFramebufferObject.GL_COLOR_ATTACHMENT0
            ReadbackApi.ExtFramebufferObject -> EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT
            ReadbackApi.Legacy -> GL11.GL_BACK
        }
    }

    private fun detectReadbackBindingState(): ReadbackBindingState {
        val readFramebufferBinding = currentReadFramebufferBinding()
        return ReadbackBindingState(
            readFramebufferBinding = readFramebufferBinding,
            drawFramebufferBinding = currentDrawFramebufferBinding(),
            framebufferBinding = currentFramebufferBinding(),
            currentReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER)
        )
    }

    private fun diagnoseReadbackSource(
        path: String,
        sourceX: Int,
        sourceY: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        setup: ReadbackSetup
    ) {
        val state = detectReadbackBindingState()
        val recommended = selectReadBufferForActiveTarget(state.currentReadBuffer)
        val appliedCompatible = isReadBufferCompatibleWithActiveTarget(setup.appliedReadBuffer, state)
        val previousCompatible = isReadBufferCompatibleWithActiveTarget(setup.previousReadBuffer, state)
        val message = buildString {
            append("[DSGL-Readback] path=").append(path)
            append(" src=(").append(sourceX).append(',').append(sourceY).append(' ')
            append(sourceWidth).append('x').append(sourceHeight).append(')')
            append(" readFbo=").append(state.readFramebufferBinding)
            append(" drawFbo=").append(state.drawFramebufferBinding)
            append(" fbo=").append(state.framebufferBinding)
            append(" previousReadBuffer=").append(glEnumName(setup.previousReadBuffer))
            append(" appliedReadBuffer=").append(glEnumName(setup.appliedReadBuffer))
            append(" currentReadBuffer=").append(glEnumName(state.currentReadBuffer))
            append(" changed=").append(setup.shouldRestore)
            append(" previousCompatible=").append(previousCompatible)
            append(" appliedCompatible=").append(appliedCompatible)
            append(" recommendedReadBuffer=").append(glEnumName(recommended))
            append(" api=").append(readbackApi.name)
        }
        logRateLimited(
            key = "readback:$path:${state.readFramebufferBinding}:${setup.appliedReadBuffer}",
            message = message
        )
    }

    private fun isReadBufferCompatibleWithActiveTarget(readBuffer: Int, state: ReadbackBindingState): Boolean {
        return if (state.usingFramebufferObject) {
            readBuffer == GL11.GL_NONE || isColorAttachmentReadBuffer(readBuffer)
        } else {
            when (readBuffer) {
                GL11.GL_BACK,
                GL11.GL_FRONT,
                GL11.GL_LEFT,
                GL11.GL_RIGHT,
                GL11.GL_FRONT_LEFT,
                GL11.GL_FRONT_RIGHT,
                GL11.GL_BACK_LEFT,
                GL11.GL_BACK_RIGHT -> true

                else -> false
            }
        }
    }

    private fun isColorAttachmentReadBuffer(readBuffer: Int): Boolean {
        return when (readbackApi) {
            ReadbackApi.OpenGl30 -> readBuffer in GL30.GL_COLOR_ATTACHMENT0..(GL30.GL_COLOR_ATTACHMENT0 + 31)
            ReadbackApi.ArbFramebufferObject -> readBuffer in ARBFramebufferObject.GL_COLOR_ATTACHMENT0..(ARBFramebufferObject.GL_COLOR_ATTACHMENT0 + 15)
            ReadbackApi.ExtFramebufferObject -> readBuffer in EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT..(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT + 15)
            ReadbackApi.Legacy -> false
        }
    }

    private fun glEnumName(value: Int): String {
        return when (value) {
            GL11.GL_NONE -> "GL_NONE"
            GL11.GL_FRONT -> "GL_FRONT"
            GL11.GL_BACK -> "GL_BACK"
            GL11.GL_LEFT -> "GL_LEFT"
            GL11.GL_RIGHT -> "GL_RIGHT"
            GL11.GL_FRONT_LEFT -> "GL_FRONT_LEFT"
            GL11.GL_FRONT_RIGHT -> "GL_FRONT_RIGHT"
            GL11.GL_BACK_LEFT -> "GL_BACK_LEFT"
            GL11.GL_BACK_RIGHT -> "GL_BACK_RIGHT"
            GL30.GL_COLOR_ATTACHMENT0,
            ARBFramebufferObject.GL_COLOR_ATTACHMENT0,
            EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT -> "GL_COLOR_ATTACHMENT0"
            else -> {
                val hex = Integer.toHexString(value).uppercase()
                "0x$hex"
            }
        }
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
