package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.InputProps
import org.dreamfinity.dsgl.core.TextAreaProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.renderStylesheetsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div(
        ComponentProps(
            key = "section.stylesheets",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("This section demonstrates DSS selectors, pseudo-states, vars and inline override."))
        text(TextProps("Edit <gameDir>/dsgl/styles/*.dss then click Reload stylesheets.").apply {
            color = DEMO_MUTED
        })

        div(
            ComponentProps(
                key = "styles.editor.card",
                id = "stylesEditorCard",
                className = "style-card editor",
                padding = 4,
                gap = 3
            ).apply {
                style = { border(1, 0xFF5E6A77.toInt()) }
            }
        ) {
            text(TextProps("Demo stylesheet editor: showcase_styles.dss"))
            textarea(
                TextAreaProps(placeholder = "Stylesheet content").apply {
                    key = "styles.editor.textarea"
                    value = window.stylesheetEditorValue
                    width = (contentWidth - 22).coerceAtLeast(120)
                    height = 92
                    onInput = { event ->
                        window.stylesheetEditorValue = event.value
                    }
                    onValueChange = { event ->
                        window.stylesheetEditorValue = event.value
                    }
                }
            )

            div(ComponentProps(gap = 4).asFlexRow()) {
                button(
                    ButtonProps("Save").apply {
                        key = "styles.editor.save"
                        width = 50
                        onMouseClick = { event ->
                            window.saveStylesheetEditorToFile("styles section save")
                            window.logHook("styles.editor.save", event)
                        }
                    }
                )
                button(
                    ButtonProps("Load").apply {
                        key = "styles.editor.load"
                        width = 50
                        onMouseClick = { event ->
                            window.loadStylesheetEditorFromFile("styles section load")
                            window.logHook("styles.editor.load", event)
                        }
                    }
                )
                button(
                    ButtonProps("Reload stylesheets").apply {
                        key = "styles.reload.button"
                        id = "stylesReloadButton"
                        className = "primary"
                        width = 112
                        onMouseClick = { event ->
                            window.reloadStylesheetsProgrammatically("styles section button")
                            window.logHook("styles.reload.onMouseClick", event)
                        }
                    }
                )
            }
            text(
                TextProps {
                    "status=${window.stylesheetEditorStatus}; reloads=${window.stylesheetReloadCount}; clicks=${window.stylesheetDemoClickCount}"
                }.apply { color = DEMO_MUTED }
            )
        }

        div(
            ComponentProps(
                key = "styles.selectors.card",
                id = "stylesSelectorsCard",
                className = "style-card selectors",
                padding = 4,
                gap = 3
            ).apply {
                style = { border(1, 0xFF5E6A77.toInt()) }
            }
        ) {
            text(TextProps("Selector matrix").apply {
                id = "stylesSelectorsTitle"
            })
            text(TextProps("Targets: button, .accent, button.primary, #dangerAction").apply {
                color = DEMO_MUTED
            })

            div(ComponentProps(gap = 4).asFlexRow()) {
                button(
                    ButtonProps("button").apply {
                        key = "styles.selector.type"
                        onMouseClick = { event ->
                            window.stylesheetDemoClickCount += 1
                            window.logHook("styles.selector.type", event)
                        }
                    }
                )
                button(
                    ButtonProps(".accent").apply {
                        key = "styles.selector.class"
                        className = "accent"
                        onMouseClick = { event ->
                            window.stylesheetDemoClickCount += 1
                            window.logHook("styles.selector.class", event)
                        }
                    }
                )
                button(
                    ButtonProps("button.primary").apply {
                        key = "styles.selector.typeClass"
                        className = "primary"
                        onMouseClick = { event ->
                            window.stylesheetDemoClickCount += 1
                            window.logHook("styles.selector.typeClass", event)
                        }
                    }
                )
            }

            div(ComponentProps(gap = 4).asFlexRow()) {
                button(
                    ButtonProps("#dangerAction").apply {
                        key = "styles.selector.id"
                        id = "dangerAction"
                        onMouseClick = { event ->
                            window.stylesheetDemoClickCount += 1
                            window.logHook("styles.selector.id", event)
                        }
                    }
                )
                button(
                    ButtonProps("Inline > stylesheet").apply {
                        key = "styles.selector.inline"
                        className = "primary"
                        style = {
                            backgroundColor(0xFF7A3A3A.toInt())
                            foregroundColor(0xFFFFFFFF.toInt())
                            borderColor(0xFFAA6666.toInt())
                            borderWidth(1)
                        }
                        onMouseClick = { event ->
                            window.stylesheetDemoClickCount += 1
                            window.logHook("styles.selector.inline", event)
                        }
                    }
                )
            }
        }

        div(
            ComponentProps(
                key = "styles.states.card",
                id = "stylesStatesCard",
                className = "style-card states",
                padding = 4,
                gap = 3
            ).apply {
                style = { border(1, 0xFF5E6A77.toInt()) }
            }
        ) {
            text(TextProps("Pseudo-states: :hover, :active, :focus, :disabled"))
            div(ComponentProps(gap = 4).asFlexRow()) {
                button(
                    ButtonProps("Hover / Active target").apply {
                        key = "styles.state.hoverActive"
                        id = "hoverActiveTarget"
                        className = "interactive-demo"
                        onMouseClick = { event ->
                            window.stylesheetDemoClickCount += 1
                            window.logHook("styles.state.hoverActive", event)
                        }
                    }
                )
                input(
                    InputProps(
                        InputType.Text(
                            value = window.stylesheetDemoTextValue,
                            placeholder = "Focus target"
                        )
                    ).apply {
                        key = "styles.state.focusInput"
                        id = "focusInput"
                        className = "interactive-demo"
                        width = 98
                        onInput = { event ->
                            window.stylesheetDemoTextValue = event.value
                            window.logHook("styles.state.focusInput.onInput", event, "value=${event.value}")
                        }
                    }
                )
                button(
                    ButtonProps("Disabled").apply {
                        key = "styles.state.disabled"
                        id = "disabledTarget"
                        className = "interactive-demo"
                        disabled = true
                    }
                )
            }
        }

        div(
            ComponentProps(
                key = "styles.variables.card",
                id = "stylesVarsCard",
                className = "style-card vars-demo",
                padding = 4,
                gap = 2
            ).apply {
                style = { border(1, 0xFF5E6A77.toInt()) }
            }
        ) {
            text(TextProps("Variable demo uses :root { --primary: ... } and var(--primary)"))
            text(TextProps("Try: .vars-demo { backgroundColor: var(--primary); borderColor: var(--accent); }").apply {
                color = DEMO_MUTED
            })
            text(
                TextProps {
                    "focusInputValue='${window.stylesheetDemoTextValue}'"
                }.apply { color = DEMO_MUTED }
            )
        }
    }
}