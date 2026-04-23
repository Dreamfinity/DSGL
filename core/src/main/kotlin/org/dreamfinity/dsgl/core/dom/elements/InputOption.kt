package org.dreamfinity.dsgl.core.dom.elements

/**
 * Option entry for checkbox/radio inputs.
 */
data class InputOption(
    val id: String,
    val label: String = id,
)
