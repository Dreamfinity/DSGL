package org.dreamfinity.dsgl.mc1710.demo.support

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DsglColors

const val DEMO_BG: Int = 0xFF1C1F24.toInt()
const val DEMO_SURFACE: Int = 0xFF252A31.toInt()
const val DEMO_SURFACE_ALT: Int = 0xFF2D333B.toInt()
const val DEMO_ACCENT: Int = 0xFF3E6B9E.toInt()
const val DEMO_OK: Int = 0xFF64C37D.toInt()
const val DEMO_ERR: Int = 0xFFE06A6A.toInt()
const val DEMO_MUTED: Int = 0xFFB0B7C1.toInt()

fun panelProps(
    key: Any,
    width: Int? = null,
    height: Int? = null,
    backgroundColor: Int = DEMO_SURFACE
): ComponentProps = ComponentProps(
    key = key,
    width = width,
    height = height,
    gap = 4,
    padding = 4,
    backgroundColor = backgroundColor,
    color = DsglColors.TEXT,
    style = {
        border(1, DsglColors.BORDER)
    }
)

fun navButtonProps(
    key: Any,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
): ButtonProps = ButtonProps(title).apply {
    this.key = key
    this.width = 96
    this.backgroundColor = if (selected) DEMO_ACCENT else DsglColors.BUTTON
    this.onMouseClick = { onClick() }
}

