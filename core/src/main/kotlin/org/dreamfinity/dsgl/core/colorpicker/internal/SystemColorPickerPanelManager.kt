package org.dreamfinity.dsgl.core.colorpicker.internal

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupManager
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerStyle
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.dom.layout.Rect

internal class SystemColorPickerPanelManager(
    private val delegate: ColorPickerPopupManager = ColorPickerPopupManager()
) {
    fun open(
        anchorRect: Rect,
        title: String,
        state: ColorPickerState,
        style: ColorPickerStyle = ColorPickerStyle(),
        width: Int = 320,
        draggable: Boolean = true,
        closeOnOutsideClick: Boolean = false,
        onPreview: ((RgbaColor) -> Unit)? = null,
        onChange: ((RgbaColor) -> Unit)? = null,
        onCommit: ((RgbaColor) -> Unit)? = null,
        onClose: (() -> Unit)? = null
    ) {
        delegate.open(
            anchorRect = anchorRect,
            title = title,
            state = state,
            style = style,
            width = width,
            draggable = draggable,
            closeOnOutsideClick = closeOnOutsideClick,
            onPreview = onPreview,
            onChange = onChange,
            onCommit = onCommit,
            onClose = onClose
        )
    }

    fun close() {
        delegate.close()
    }

    fun isOpen(): Boolean = delegate.isOpen()
}

