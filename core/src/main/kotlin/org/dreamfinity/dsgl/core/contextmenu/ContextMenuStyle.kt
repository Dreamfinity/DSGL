package org.dreamfinity.dsgl.core.contextmenu

import org.dreamfinity.dsgl.core.DsglColors

data class ContextMenuStyle(
    val viewportPadding: Int = 6,
    val panelPaddingX: Int = 5,
    val panelPaddingY: Int = 5,
    val rowPaddingX: Int = 6,
    val rowPaddingY: Int = 3,
    val rowGap: Int = 2,
    val separatorHeight: Int = 6,
    val separatorInsetX: Int = 5,
    val contentSpacing: Int = 6,
    val hintSpacing: Int = 14,
    val minPanelWidth: Int = 120,
    val maxPanelHeightPadding: Int = 10,
    val hoverOpenDelayMs: Long = 130L,
    val submenuCloseDelayMs: Long = 180L,
    val wheelStepRows: Int = 3,
    val fontId: String? = null,
    val fontSize: Int? = null,
    val panelBackgroundColor: Int = 0xFF1F2630.toInt(),
    val panelBorderColor: Int = 0xFF5C6B7A.toInt(),
    val panelShadowColor: Int = 0x70101722,
    val itemHoverBackgroundColor: Int = 0xFF35506A.toInt(),
    val itemSelectedBackgroundColor: Int = 0xFF2B3F54.toInt(),
    val itemTextColor: Int = DsglColors.WHITE,
    val disabledTextColor: Int = 0xFF8E98A2.toInt(),
    val hintTextColor: Int = 0xFFB6C2CF.toInt(),
    val separatorColor: Int = 0xFF4D5D6E.toInt(),
    val checkMarkColor: Int = 0xFF8BE39A.toInt(),
    val submenuArrowColor: Int = 0xFFC7D4E1.toInt()
)
