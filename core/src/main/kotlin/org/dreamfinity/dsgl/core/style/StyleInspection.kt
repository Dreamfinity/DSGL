package org.dreamfinity.dsgl.core.style

enum class StyleSourceKind {
    Default,
    Inherited,
    Selector,
    Inline,
    InspectorOverride,
}

data class StylePropertySource(
    val property: StyleProperty,
    val kind: StyleSourceKind,
    val source: String,
)

data class StyleInspection(
    val computed: ComputedStyle,
    val propertySources: Map<StyleProperty, StylePropertySource>,
    val matchedRules: List<String>,
)
