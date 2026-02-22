package org.dreamfinity.dsgl.mc1710.demo.support

enum class DemoSection(
    val title: String,
    val subtitle: String
) {
    OVERVIEW("Overview", "How to use the showcase"),
    LAYOUT_STYLE("Layout & Style", "Containers, gaps, fixed sizes, style DSL"),
    STYLESHEETS("Stylesheets", "Selectors, variables, pseudo-states, inline override"),
    INPUTS("Inputs Gallery", "All input factory variants and textarea"),
    INPUT_EVENTS("Input Events", "HTML-like onFocus/onBlur/onInput/onChange"),
    TEXT_EDITING("Text Editing", "Caret blink, selection and clipboard shortcuts"),
    REFS("Refs", "Object refs + callback refs + imperative handles"),
    DRAG_DROP("Drag & Drop", "HTML-like drag events, DataTransfer and smooth ghost"),
    INTERACTIONS("Interactions", "Mouse/key hooks, bubbling, cancellation"),
    FOCUS_REBUILD("Focus & Rebuild", "Focus retention and invalidation"),
    MC_FEATURES("MC Features", "Image sources and item stack rendering")
}
