package org.dreamfinity.dsgl.mcForge1710.demo.support

enum class DemoSection(
    val title: String,
    val subtitle: String,
) {
    OVERVIEW("Overview", "How to use the showcase"),
    INSPECTOR("Inspector", "Global in-game element/style/layout inspector (F12/F9)"),
    LAYOUT_STYLE("Layout & Style", "Containers, gaps, fixed sizes, style DSL"),
    LAYOUT_DEBUG("Layout Debug", "Strict bounds validator and diagnostics"),
    POSITIONED_LAYOUT(
        "Positioned Layout",
        "static/relative/absolute/fixed/sticky + z-index overlap, scroll and hit-testing",
    ),
    OVERFLOW_SCROLL("Overflow & Scroll", "Viewport clipping, gutters, and cross-axis overflow forcing"),
    DISPLAY("Display", "block/inline/none/flex/grid layout behaviors"),
    TEXT_WRAP("Text Wrap", "wrap/nowrap behavior for text rendering"),
    MSDF_FONTS("MSDF Fonts", "MTSDF atlas fonts: switch font, size, color, opacity, wrapping"),
    ANIMATIONS("Animations & Transforms", "Transform hit-testing, transitions, keyframes, easing"),
    STYLESHEETS("Stylesheets", "Selectors, variables, pseudo-states, inline override"),
    CSS_CASCADE(
        "CSS Cascade & Combinators",
        "Descendant/child/sibling selectors, specificity, source order, !important, inheritance",
    ),
    MODALS("Modals", "Declarative stacked modal host (RB-inspired)"),
    CONTEXT_MENU("Context Menu", "Right-click nested menus with overlay-first hit testing"),
    INPUTS("Inputs Gallery", "All input factory variants and textarea"),
    INPUT_EVENTS("Input Events", "HTML-like onFocus/onBlur/onInput/onChange"),
    COLOR_PICKER("Color Picker", "Reusable inline + popup pane color picker with eyedropper/history"),
    TEXT_EDITING("Text Editing", "Caret blink, selection and clipboard shortcuts"),
    REFS("Hooks", "useRef/useState/useMemo/useCallback/useReducer/useContext/useEffect showcase"),
    DRAG_DROP("Drag & Drop", "HTML-like drag events, DataTransfer and smooth ghost"),
    INTERACTIONS("Interactions", "Mouse/key hooks, bubbling, cancellation"),
    FOCUS_REBUILD("Focus & Rebuild", "Focus retention and invalidation"),
    MC_FEATURES("MC Features", "Pixel viewport rendering, clipping and item stacks"),
}
