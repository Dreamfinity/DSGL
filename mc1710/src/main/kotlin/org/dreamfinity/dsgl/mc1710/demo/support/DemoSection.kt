package org.dreamfinity.dsgl.mc1710.demo.support

enum class DemoSection(
    val title: String,
    val subtitle: String
) {
    OVERVIEW("Overview", "How to use the showcase"),
    LAYOUT_STYLE("Layout & Style", "Containers, gaps, fixed sizes, style DSL"),
    INPUTS("Inputs Gallery", "All input factory variants and textarea"),
    INTERACTIONS("Interactions", "Mouse/key hooks, bubbling, cancellation"),
    FOCUS_REBUILD("Focus & Rebuild", "Focus retention and invalidation"),
    MC_FEATURES("MC Features", "Image sources and item stack rendering")
}

