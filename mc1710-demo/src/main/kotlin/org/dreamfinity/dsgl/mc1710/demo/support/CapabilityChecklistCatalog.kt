package org.dreamfinity.dsgl.mc1710.demo.support

enum class CapabilityGroup(val title: String) {
    DSL_BUILDERS("DSL Builders"),
    INPUT_TYPES("Input Types"),
    EVENT_HOOKS("Event Hooks"),
    SHOWCASE_FEATURES("Showcase Features"),
    MC_ADAPTER_FEATURES("MC Adapter Features")
}

enum class CapabilityId(
    val label: String,
    val group: CapabilityGroup
) {
    BUILDER_DIV("builder: div", CapabilityGroup.DSL_BUILDERS),
    BUILDER_OVERLAY("builder: overlay", CapabilityGroup.DSL_BUILDERS),
    BUILDER_TEXT("builder: text", CapabilityGroup.DSL_BUILDERS),
    BUILDER_TEXT_LAMBDA("builder: text(lambda)", CapabilityGroup.DSL_BUILDERS),
    BUILDER_BUTTON("builder: button", CapabilityGroup.DSL_BUILDERS),
    BUILDER_IMG("builder: img", CapabilityGroup.DSL_BUILDERS),
    BUILDER_ITEM_STACK("builder: itemStack", CapabilityGroup.DSL_BUILDERS),
    BUILDER_INPUT("builder: input", CapabilityGroup.DSL_BUILDERS),
    BUILDER_TEXTAREA("builder: textarea", CapabilityGroup.DSL_BUILDERS),

    INPUT_TEXT("input: Text", CapabilityGroup.INPUT_TYPES),
    INPUT_PASSWORD("input: Password", CapabilityGroup.INPUT_TYPES),
    INPUT_NUMBER("input: Number", CapabilityGroup.INPUT_TYPES),
    INPUT_RANGE("input: Range", CapabilityGroup.INPUT_TYPES),
    INPUT_CHECKBOX("input: Checkbox", CapabilityGroup.INPUT_TYPES),
    INPUT_RADIO("input: Radio", CapabilityGroup.INPUT_TYPES),
    INPUT_DATE("input: Date", CapabilityGroup.INPUT_TYPES),

    HOOK_MOUSE_ENTER("hook: onMouseEnter", CapabilityGroup.EVENT_HOOKS),
    HOOK_MOUSE_LEAVE("hook: onMouseLeave", CapabilityGroup.EVENT_HOOKS),
    HOOK_MOUSE_OVER("hook: onMouseOver", CapabilityGroup.EVENT_HOOKS),
    HOOK_MOUSE_MOVE("hook: onMouseMove", CapabilityGroup.EVENT_HOOKS),
    HOOK_MOUSE_DOWN("hook: onMouseDown", CapabilityGroup.EVENT_HOOKS),
    HOOK_MOUSE_UP("hook: onMouseUp", CapabilityGroup.EVENT_HOOKS),
    HOOK_MOUSE_CLICK("hook: onMouseClick", CapabilityGroup.EVENT_HOOKS),
    HOOK_MOUSE_DRAG("hook: onMouseDrag", CapabilityGroup.EVENT_HOOKS),
    HOOK_MOUSE_WHEEL("hook: onMouseWheel", CapabilityGroup.EVENT_HOOKS),
    HOOK_KEY_DOWN("hook: onKeyDown", CapabilityGroup.EVENT_HOOKS),
    HOOK_KEY_UP("hook: onKeyUp", CapabilityGroup.EVENT_HOOKS),
    HOOK_KEY_PRESSED("hook: onKeyPressed", CapabilityGroup.EVENT_HOOKS),
    HOOK_KEY_RELEASED("hook: onKeyReleased", CapabilityGroup.EVENT_HOOKS),
    HOOK_FOCUS("hook: onFocus", CapabilityGroup.EVENT_HOOKS),
    HOOK_BLUR("hook: onBlur", CapabilityGroup.EVENT_HOOKS),
    HOOK_INPUT("hook: onInput", CapabilityGroup.EVENT_HOOKS),
    HOOK_CHANGE("hook: onChange", CapabilityGroup.EVENT_HOOKS),
    HOOK_DRAG_START("hook: onDragStart", CapabilityGroup.EVENT_HOOKS),
    HOOK_DRAG("hook: onDrag", CapabilityGroup.EVENT_HOOKS),
    HOOK_DRAG_END("hook: onDragEnd", CapabilityGroup.EVENT_HOOKS),
    HOOK_DRAG_ENTER("hook: onDragEnter", CapabilityGroup.EVENT_HOOKS),
    HOOK_DRAG_OVER("hook: onDragOver", CapabilityGroup.EVENT_HOOKS),
    HOOK_DRAG_LEAVE("hook: onDragLeave", CapabilityGroup.EVENT_HOOKS),
    HOOK_DROP("hook: onDrop", CapabilityGroup.EVENT_HOOKS),

    EVENT_INSPECTOR("Event Inspector panel", CapabilityGroup.SHOWCASE_FEATURES),
    CAPABILITY_CHECKLIST("Capability Checklist panel", CapabilityGroup.SHOWCASE_FEATURES),
    EVENT_CANCELLATION("Event cancellation/bubbling demo", CapabilityGroup.SHOWCASE_FEATURES),
    LAYOUT_VALIDATOR("Layout validator strict bounds", CapabilityGroup.SHOWCASE_FEATURES),
    DISPLAY_BLOCK("Display: block", CapabilityGroup.SHOWCASE_FEATURES),
    DISPLAY_INLINE("Display: inline", CapabilityGroup.SHOWCASE_FEATURES),
    DISPLAY_NONE("Display: none", CapabilityGroup.SHOWCASE_FEATURES),
    DISPLAY_FLEX("Display: flex", CapabilityGroup.SHOWCASE_FEATURES),
    DISPLAY_GRID("Display: grid", CapabilityGroup.SHOWCASE_FEATURES),
    TEXT_WRAP("Text wrap style demo", CapabilityGroup.SHOWCASE_FEATURES),
    MSDF_FONT_SWITCH("MSDF font switching", CapabilityGroup.SHOWCASE_FEATURES),
    MSDF_OPACITY("MSDF opacity rendering", CapabilityGroup.SHOWCASE_FEATURES),
    MSDF_WRAP("MSDF wrapping + measurement", CapabilityGroup.SHOWCASE_FEATURES),
    ANIMATION_TRANSFORM("Transform animation demo", CapabilityGroup.SHOWCASE_FEATURES),
    ANIMATION_OPACITY("Opacity animation demo", CapabilityGroup.SHOWCASE_FEATURES),
    ANIMATION_KEYFRAMES("Keyframes animation demo", CapabilityGroup.SHOWCASE_FEATURES),
    MODAL_HOST("Modal host overlay", CapabilityGroup.SHOWCASE_FEATURES),
    MODAL_STACKING("Modal deterministic stacking", CapabilityGroup.SHOWCASE_FEATURES),
    MODAL_BACKDROP("Modal backdrop behaviors", CapabilityGroup.SHOWCASE_FEATURES),
    MODAL_ESCAPE("Modal ESC close behavior", CapabilityGroup.SHOWCASE_FEATURES),
    MODAL_FOCUS_TRAP("Modal focus trap + restore", CapabilityGroup.SHOWCASE_FEATURES),
    CONTEXT_MENU_OVERLAY("Context menu overlay rendering", CapabilityGroup.SHOWCASE_FEATURES),
    CONTEXT_MENU_NESTED("Context menu nested submenus", CapabilityGroup.SHOWCASE_FEATURES),
    CONTEXT_MENU_ANCHORED("Context menu anchored + cursor open", CapabilityGroup.SHOWCASE_FEATURES),
    CONTEXT_MENU_SCROLL("Context menu overflow + wheel scroll", CapabilityGroup.SHOWCASE_FEATURES),
    LAYOUT_GAP_FIXED("Gap + fixed-size demo", CapabilityGroup.SHOWCASE_FEATURES),
    STYLE_MARGIN_PADDING_BORDER("Style margin/padding/border toggles", CapabilityGroup.SHOWCASE_FEATURES),
    OVERLAY_BEHAVIOR("Overlay behavior demo", CapabilityGroup.SHOWCASE_FEATURES),
    STYLESHEET_SELECTORS("Stylesheet selectors demo", CapabilityGroup.SHOWCASE_FEATURES),
    STYLESHEET_COMBINATORS("Stylesheet descendant/child/sibling combinators demo", CapabilityGroup.SHOWCASE_FEATURES),
    STYLESHEET_CASCADE("Stylesheet cascade/specificity/important/inheritance demo", CapabilityGroup.SHOWCASE_FEATURES),
    STYLESHEET_PSEUDO_STATES("Stylesheet pseudo-states demo", CapabilityGroup.SHOWCASE_FEATURES),
    STYLESHEET_VARIABLES("Stylesheet variables demo", CapabilityGroup.SHOWCASE_FEATURES),
    STYLESHEET_INLINE_OVERRIDE("Inline style override demo", CapabilityGroup.SHOWCASE_FEATURES),
    STYLESHEET_PROGRAMMATIC_RELOAD("Programmatic stylesheet reload", CapabilityGroup.SHOWCASE_FEATURES),
    REFS_OBJECT("Object ref (.current) demo", CapabilityGroup.SHOWCASE_FEATURES),
    REFS_CALLBACK("Callback ref attach/detach demo", CapabilityGroup.SHOWCASE_FEATURES),
    REFS_IMPERATIVE_FOCUS("Imperative focus via ref", CapabilityGroup.SHOWCASE_FEATURES),
    DND_SMOOTH_GHOST("Smooth drag ghost", CapabilityGroup.SHOWCASE_FEATURES),
    DND_DATA_TRANSFER("DataTransfer payload demo", CapabilityGroup.SHOWCASE_FEATURES),
    DND_DROP_EFFECT("Drop effect demo", CapabilityGroup.SHOWCASE_FEATURES),
    FOCUS_RETENTION("Focus retention with stable keys", CapabilityGroup.SHOWCASE_FEATURES),
    STATE_REBUILD("State-driven rebuild demo", CapabilityGroup.SHOWCASE_FEATURES),
    MANUAL_INVALIDATE("Manual invalidate demo", CapabilityGroup.SHOWCASE_FEATURES),

    IMAGE_RESOURCE("Image: resource path", CapabilityGroup.MC_ADAPTER_FEATURES),
    IMAGE_FILE("Image: file:// path", CapabilityGroup.MC_ADAPTER_FEATURES),
    IMAGE_HTTP("Image: http(s):// path", CapabilityGroup.MC_ADAPTER_FEATURES),
    ITEMSTACK_2D("Item stack: 2D item", CapabilityGroup.MC_ADAPTER_FEATURES),
    ITEMSTACK_3D("Item stack: 3D block", CapabilityGroup.MC_ADAPTER_FEATURES),
    ITEMSTACK_ROTATION("Item stack rotation controls", CapabilityGroup.MC_ADAPTER_FEATURES)
}

object CapabilityChecklistCatalog {
    val required: List<CapabilityId> = CapabilityId.entries

    fun capabilitiesForSection(section: DemoSection): Set<CapabilityId> = when (section) {
        DemoSection.OVERVIEW -> setOf(
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_TEXT_LAMBDA,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.EVENT_INSPECTOR,
            CapabilityId.CAPABILITY_CHECKLIST
        )

        DemoSection.INSPECTOR -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.BUILDER_INPUT
        )

        DemoSection.LAYOUT_STYLE -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_OVERLAY,
            CapabilityId.HOOK_MOUSE_CLICK,
            CapabilityId.LAYOUT_GAP_FIXED,
            CapabilityId.STYLE_MARGIN_PADDING_BORDER,
            CapabilityId.OVERLAY_BEHAVIOR
        )

        DemoSection.LAYOUT_DEBUG -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.BUILDER_INPUT,
            CapabilityId.LAYOUT_VALIDATOR
        )

        DemoSection.POSITIONED_LAYOUT -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.BUILDER_INPUT,
            CapabilityId.HOOK_MOUSE_ENTER,
            CapabilityId.HOOK_MOUSE_LEAVE,
            CapabilityId.HOOK_MOUSE_CLICK,
            CapabilityId.HOOK_MOUSE_WHEEL,
            CapabilityId.OVERLAY_BEHAVIOR
        )

        DemoSection.OVERFLOW_SCROLL -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.BUILDER_INPUT,
            CapabilityId.HOOK_MOUSE_CLICK,
        )

        DemoSection.DISPLAY -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_INPUT,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.HOOK_MOUSE_CLICK,
            CapabilityId.DISPLAY_BLOCK,
            CapabilityId.DISPLAY_INLINE,
            CapabilityId.DISPLAY_NONE,
            CapabilityId.DISPLAY_FLEX,
            CapabilityId.DISPLAY_GRID
        )

        DemoSection.TEXT_WRAP -> setOf(
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_TEXT_LAMBDA,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.BUILDER_INPUT,
            CapabilityId.TEXT_WRAP
        )

        DemoSection.MSDF_FONTS -> setOf(
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_TEXT_LAMBDA,
            CapabilityId.BUILDER_INPUT,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.MSDF_FONT_SWITCH,
            CapabilityId.MSDF_OPACITY,
            CapabilityId.MSDF_WRAP
        )

        DemoSection.ANIMATIONS -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_TEXT_LAMBDA,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.BUILDER_INPUT,
            CapabilityId.HOOK_MOUSE_ENTER,
            CapabilityId.HOOK_MOUSE_LEAVE,
            CapabilityId.ANIMATION_TRANSFORM,
            CapabilityId.ANIMATION_OPACITY,
            CapabilityId.ANIMATION_KEYFRAMES
        )

        DemoSection.MODALS -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_INPUT,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.MODAL_HOST,
            CapabilityId.MODAL_STACKING,
            CapabilityId.MODAL_BACKDROP,
            CapabilityId.MODAL_ESCAPE,
            CapabilityId.MODAL_FOCUS_TRAP
        )

        DemoSection.CONTEXT_MENU -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.HOOK_MOUSE_DOWN,
            CapabilityId.CONTEXT_MENU_OVERLAY,
            CapabilityId.CONTEXT_MENU_NESTED,
            CapabilityId.CONTEXT_MENU_ANCHORED,
            CapabilityId.CONTEXT_MENU_SCROLL
        )

        DemoSection.INPUTS -> setOf(
            CapabilityId.BUILDER_INPUT,
            CapabilityId.BUILDER_TEXTAREA,
            CapabilityId.INPUT_TEXT,
            CapabilityId.INPUT_PASSWORD,
            CapabilityId.INPUT_NUMBER,
            CapabilityId.INPUT_RANGE,
            CapabilityId.INPUT_CHECKBOX,
            CapabilityId.INPUT_RADIO,
            CapabilityId.INPUT_DATE
        )

        DemoSection.INPUT_EVENTS -> setOf(
            CapabilityId.BUILDER_INPUT,
            CapabilityId.BUILDER_TEXTAREA,
            CapabilityId.HOOK_FOCUS,
            CapabilityId.HOOK_BLUR,
            CapabilityId.HOOK_INPUT,
            CapabilityId.HOOK_CHANGE
        )

        DemoSection.COLOR_PICKER -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_TEXT_LAMBDA,
            CapabilityId.HOOK_MOUSE_DOWN,
            CapabilityId.HOOK_MOUSE_CLICK
        )

        DemoSection.TEXT_EDITING -> setOf(
            CapabilityId.BUILDER_INPUT,
            CapabilityId.BUILDER_TEXTAREA,
            CapabilityId.HOOK_MOUSE_DOWN,
            CapabilityId.HOOK_MOUSE_DRAG,
            CapabilityId.HOOK_KEY_DOWN,
            CapabilityId.HOOK_INPUT
        )

        DemoSection.REFS -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_INPUT,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.BUILDER_TEXT_LAMBDA,
            CapabilityId.REFS_OBJECT,
            CapabilityId.REFS_CALLBACK,
            CapabilityId.REFS_IMPERATIVE_FOCUS
        )

        DemoSection.DRAG_DROP -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_TEXT_LAMBDA,
            CapabilityId.HOOK_DRAG_START,
            CapabilityId.HOOK_DRAG,
            CapabilityId.HOOK_DRAG_END,
            CapabilityId.HOOK_DRAG_ENTER,
            CapabilityId.HOOK_DRAG_OVER,
            CapabilityId.HOOK_DRAG_LEAVE,
            CapabilityId.HOOK_DROP,
            CapabilityId.DND_SMOOTH_GHOST,
            CapabilityId.DND_DATA_TRANSFER,
            CapabilityId.DND_DROP_EFFECT
        )

        DemoSection.INTERACTIONS -> setOf(
            CapabilityId.HOOK_MOUSE_ENTER,
            CapabilityId.HOOK_MOUSE_LEAVE,
            CapabilityId.HOOK_MOUSE_OVER,
            CapabilityId.HOOK_MOUSE_MOVE,
            CapabilityId.HOOK_MOUSE_DOWN,
            CapabilityId.HOOK_MOUSE_UP,
            CapabilityId.HOOK_MOUSE_DRAG,
            CapabilityId.HOOK_MOUSE_WHEEL,
            CapabilityId.HOOK_KEY_DOWN,
            CapabilityId.HOOK_KEY_UP,
            CapabilityId.HOOK_KEY_PRESSED,
            CapabilityId.HOOK_KEY_RELEASED,
            CapabilityId.EVENT_CANCELLATION
        )

        DemoSection.FOCUS_REBUILD -> setOf(
            CapabilityId.HOOK_KEY_DOWN,
            CapabilityId.HOOK_KEY_UP,
            CapabilityId.FOCUS_RETENTION,
            CapabilityId.STATE_REBUILD,
            CapabilityId.MANUAL_INVALIDATE
        )

        DemoSection.STYLESHEETS -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_TEXT_LAMBDA,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.BUILDER_INPUT,
            CapabilityId.STYLESHEET_SELECTORS,
            CapabilityId.STYLESHEET_PSEUDO_STATES,
            CapabilityId.STYLESHEET_VARIABLES,
            CapabilityId.STYLESHEET_INLINE_OVERRIDE,
            CapabilityId.STYLESHEET_PROGRAMMATIC_RELOAD
        )

        DemoSection.CSS_CASCADE -> setOf(
            CapabilityId.BUILDER_DIV,
            CapabilityId.BUILDER_TEXT,
            CapabilityId.BUILDER_TEXT_LAMBDA,
            CapabilityId.BUILDER_BUTTON,
            CapabilityId.HOOK_MOUSE_CLICK,
            CapabilityId.STYLESHEET_SELECTORS,
            CapabilityId.STYLESHEET_COMBINATORS,
            CapabilityId.STYLESHEET_CASCADE
        )

        DemoSection.MC_FEATURES -> setOf(
            CapabilityId.BUILDER_IMG,
            CapabilityId.BUILDER_ITEM_STACK,
            CapabilityId.IMAGE_RESOURCE,
            CapabilityId.IMAGE_FILE,
            CapabilityId.IMAGE_HTTP,
            CapabilityId.ITEMSTACK_2D,
            CapabilityId.ITEMSTACK_3D,
            CapabilityId.ITEMSTACK_ROTATION
        )
    }

    fun implementedByAllSections(): Set<CapabilityId> {
        return DemoSection.entries
            .flatMap { capabilitiesForSection(it) }
            .toSet()
    }
}

