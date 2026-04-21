package org.dreamfinity.dsgl.core.colorpicker.internal

import org.dreamfinity.dsgl.core.colorpicker.*
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import kotlin.math.roundToInt

internal class EyedropperCaptureNode(
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-eyedropper-capture"

    private var sourceRect: Rect? = null
    private var fallbackColor: Int = 0

    fun bind(sourceRect: Rect, fallbackColor: Int) {
        if (this.sourceRect != sourceRect || this.fallbackColor != fallbackColor) {
            markRenderCommandsDirty()
        }
        this.sourceRect = sourceRect
        this.fallbackColor = fallbackColor
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (display == Display.None) return
        val source = sourceRect ?: return
        if (source.width <= 0 || source.height <= 0) return
        out += RenderCommand.CaptureScreenRegion(
            sourceX = source.x,
            sourceY = source.y,
            sourceWidth = source.width,
            sourceHeight = source.height,
            fallbackColor = fallbackColor
        )
    }
}

internal class EyedropperMagnifierDrawNode(
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-eyedropper-magnifier"

    private var columns: Int = 1
    private var rows: Int = 1
    private var magnification: Int = 1
    private var gridEnabled: Boolean = true
    private var gridColor: Int = 0x66FFFFFF

    fun bind(columns: Int, rows: Int, magnification: Int, gridEnabled: Boolean, gridColor: Int) {
        val nextColumns = columns.coerceAtLeast(1)
        val nextRows = rows.coerceAtLeast(1)
        val nextMagnification = magnification.coerceAtLeast(1)
        if (this.columns != nextColumns ||
            this.rows != nextRows ||
            this.magnification != nextMagnification ||
            this.gridEnabled != gridEnabled ||
            this.gridColor != gridColor
        ) {
            markRenderCommandsDirty()
        }
        this.columns = nextColumns
        this.rows = nextRows
        this.magnification = nextMagnification
        this.gridEnabled = gridEnabled
        this.gridColor = gridColor
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (display == Display.None) return
        if (bounds.width <= 0 || bounds.height <= 0) return
        out += RenderCommand.DrawCapturedScreenRegion(
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
            gridOverlay = if (gridEnabled) {
                RenderCommand.CapturedGridOverlay(
                    columns = columns,
                    rows = rows,
                    magnification = magnification,
                    color = gridColor
                )
            } else {
                null
            }
        )
    }
}

internal class ColorFieldSurfaceNode(
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-color-field"

    private var style: ColorPickerStyle = ColorPickerStyle()
    private var hueDeg: Float = 0f
    private var saturation: Float = 0f
    private var brightness: Float = 1f

    fun bind(style: ColorPickerStyle, color: RgbaColor, hueDeg: Float) {
        val hsv = ColorConversions.rgbToHsv(color, hueDeg)
        val nextSaturation = hsv.saturation
        val nextBrightness = hsv.brightness
        if (this.style != style ||
            this.hueDeg != hueDeg ||
            saturation != nextSaturation ||
            brightness != nextBrightness
        ) {
            markRenderCommandsDirty()
        }
        this.style = style
        this.hueDeg = hueDeg
        saturation = nextSaturation
        brightness = nextBrightness
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (bounds.width <= 0 || bounds.height <= 0) return
        out += RenderCommand.DrawColorField(
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
            hueDeg = hueDeg
        )
        drawBorder(out, bounds, style.inputBorderColor)
        val thumbX = bounds.x + (saturation * bounds.width.toFloat()).roundToInt().coerceIn(0, bounds.width - 1)
        val thumbY =
            bounds.y + ((1f - brightness) * bounds.height.toFloat()).roundToInt().coerceIn(0, bounds.height - 1)
        out += RenderCommand.DrawRect(thumbX - 3, thumbY - 3, 7, 7, style.thumbShadowColor)
        drawBorder(out, Rect(thumbX - 2, thumbY - 2, 5, 5), style.thumbOutlineColor)
    }
}

internal class HueSurfaceNode(
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-hue-slider"

    private var style: ColorPickerStyle = ColorPickerStyle()
    private var hueDeg: Float = 0f

    fun bind(style: ColorPickerStyle, hueDeg: Float) {
        if (this.style != style || this.hueDeg != hueDeg) {
            markRenderCommandsDirty()
        }
        this.style = style
        this.hueDeg = hueDeg
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (bounds.width <= 0 || bounds.height <= 0) return
        out += RenderCommand.DrawHueBar(
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height
        )
        drawBorder(out, bounds, style.inputBorderColor)
        val thumbX = bounds.x + ((hueDeg / 360f) * bounds.width.toFloat()).roundToInt().coerceIn(0, bounds.width - 1)
        out += RenderCommand.DrawRect(thumbX - 1, bounds.y - 1, 3, bounds.height + 2, style.thumbOutlineColor)
    }
}

internal class AlphaSurfaceNode(
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-alpha-slider"

    private var style: ColorPickerStyle = ColorPickerStyle()
    private var color: RgbaColor = RgbaColor.WHITE

    fun bind(style: ColorPickerStyle, color: RgbaColor) {
        if (this.style != style || this.color != color) {
            markRenderCommandsDirty()
        }
        this.style = style
        this.color = color
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (bounds.width <= 0 || bounds.height <= 0) return
        drawChecker(out, bounds, style)
        out += RenderCommand.DrawAlphaBar(
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
            rgbColor = color.copy(a = 1f).toArgbInt()
        )
        drawBorder(out, bounds, style.inputBorderColor)
        val thumbX = bounds.x + (color.a * bounds.width.toFloat()).roundToInt().coerceIn(0, bounds.width - 1)
        out += RenderCommand.DrawRect(thumbX - 1, bounds.y - 1, 3, bounds.height + 2, style.thumbOutlineColor)
    }
}

internal class ColorSwatchSurfaceNode(
    private val allowEmpty: Boolean = false,
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-swatch"

    private var style: ColorPickerStyle = ColorPickerStyle()
    private var color: RgbaColor? = RgbaColor.WHITE
    private var highlighted: Boolean = false

    fun bind(style: ColorPickerStyle, color: RgbaColor?, highlighted: Boolean) {
        if (this.style != style || this.color != color || this.highlighted != highlighted) {
            markRenderCommandsDirty()
        }
        this.style = style
        this.color = color
        this.highlighted = highlighted
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (bounds.width <= 0 || bounds.height <= 0) return
        val localColor = color
        if (localColor == null && allowEmpty) {
            out += RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, 0x33222A34)
            drawBorder(out, bounds, if (highlighted) style.inputActiveBorderColor else style.recentGridBorderColor)
            return
        }
        drawChecker(out, bounds, style)
        out += RenderCommand.DrawRect(
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            (localColor ?: RgbaColor.WHITE).toArgbInt()
        )
        drawBorder(out, bounds, if (highlighted) style.inputActiveBorderColor else style.inputBorderColor)
    }
}

private fun drawChecker(out: MutableList<RenderCommand>, rect: Rect, style: ColorPickerStyle) {
    if (rect.width <= 0 || rect.height <= 0) return
    out += RenderCommand.DrawCheckerboard(
        x = rect.x,
        y = rect.y,
        width = rect.width,
        height = rect.height,
        cellSize = 4,
        lightColor = style.checkerLightColor,
        darkColor = style.checkerDarkColor
    )
}

private fun drawBorder(out: MutableList<RenderCommand>, rect: Rect, color: Int) {
    if (rect.width <= 0 || rect.height <= 0) return
    out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, 1, color)
    out += RenderCommand.DrawRect(rect.x, rect.y + rect.height - 1, rect.width, 1, color)
    out += RenderCommand.DrawRect(rect.x, rect.y, 1, rect.height, color)
    out += RenderCommand.DrawRect(rect.x + rect.width - 1, rect.y, 1, rect.height, color)
}

