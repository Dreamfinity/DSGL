package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.stylesheetsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.stylesheets"
        width = contentWidth
        height = contentHeight
        gap = 4
        asFlexColumn()
    }) {
        text("This section demonstrates DSS selectors, pseudo-states, vars and inline override.")
        text("Edit <gameDir>/dsgl/styles/*.dss then click Reload stylesheets.", {
            color = DEMO_MUTED
        })

        div({
            key = "styles.editor.card"
            id = "stylesEditorCard"
            className = "style-card editor"
            padding = 4
            gap = 3
            style = { border(1, 0xFF5E6A77.toInt()) }
        }) {
            text("Demo stylesheet editor: showcase_styles.dss")
            textarea({
                placeholder = "Stylesheet content"
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
            })

            div({ gap = 4; asFlexRow() }) {
                button("Save", {
                    key = "styles.editor.save"
                    width = 50
                    onMouseClick = { event ->
                        window.saveStylesheetEditorToFile("styles section save")
                        window.logHook("styles.editor.save", event)
                    }
                })
                button("Load", {
                    key = "styles.editor.load"
                    width = 50
                    onMouseClick = { event ->
                        window.loadStylesheetEditorFromFile("styles section load")
                        window.logHook("styles.editor.load", event)
                    }
                })
                button("Reload stylesheets", {
                    key = "styles.reload.button"
                    id = "stylesReloadButton"
                    className = "primary"
                    width = 112
                    onMouseClick = { event ->
                        window.reloadStylesheetsProgrammatically("styles section button")
                        window.logHook("styles.reload.onMouseClick", event)
                    }
                })
            }
            text(
                "status=${window.stylesheetEditorStatus}; reloads=${window.stylesheetReloadCount}; clicks=${window.stylesheetDemoClickCount}",
                { color = DEMO_MUTED }
            )
        }

        div({
            key = "styles.selectors.card"
            id = "stylesSelectorsCard"
            className = "style-card selectors"
            padding = 4
            gap = 3
            style = { border(1, 0xFF5E6A77.toInt()) }
        }) {
            text("Selector matrix", {
                id = "stylesSelectorsTitle"
            })
            text("Targets: button, .accent, button.primary, #dangerAction", {
                color = DEMO_MUTED
            })

            div({ gap = 4; asFlexRow() }) {
                button("button", {
                    key = "styles.selector.type"
                    onMouseClick = { event ->
                        window.stylesheetDemoClickCount += 1
                        window.logHook("styles.selector.type", event)
                    }
                })
                button(".accent", {
                    key = "styles.selector.class"
                    className = "accent"
                    onMouseClick = { event ->
                        window.stylesheetDemoClickCount += 1
                        window.logHook("styles.selector.class", event)
                    }
                })
                button("button.primary", {
                    key = "styles.selector.typeClass"
                    className = "primary"
                    onMouseClick = { event ->
                        window.stylesheetDemoClickCount += 1
                        window.logHook("styles.selector.typeClass", event)
                    }
                })
            }

            div({ gap = 4; asFlexRow() }) {
                button("#dangerAction", {
                    key = "styles.selector.id"
                    id = "dangerAction"
                    onMouseClick = { event ->
                        window.stylesheetDemoClickCount += 1
                        window.logHook("styles.selector.id", event)
                    }
                })
                button("Inline > stylesheet", {
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
                })
            }
        }

        div({
            key = "styles.states.card"
            id = "stylesStatesCard"
            className = "style-card states"
            padding = 4
            gap = 3
            style = { border(1, 0xFF5E6A77.toInt()) }
        }) {
            text("Pseudo-states: :hover, :active, :focus, :disabled")
            div({ gap = 4; asFlexRow() }) {
                button("Hover / Active target", {
                    key = "styles.state.hoverActive"
                    id = "hoverActiveTarget"
                    className = "interactive-demo"
                    onMouseClick = { event ->
                        window.stylesheetDemoClickCount += 1
                        window.logHook("styles.state.hoverActive", event)
                    }
                })
                input(
                    InputType.Text(
                        value = window.stylesheetDemoTextValue,
                        placeholder = "Focus target"
                    ),
                    {
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
                button("Disabled", {
                    key = "styles.state.disabled"
                    id = "disabledTarget"
                    className = "interactive-demo"
                    disabled = true
                })
            }
        }

        div({
            key = "styles.variables.card"
            id = "stylesVarsCard"
            className = "style-card vars-demo"
            padding = 4
            gap = 2
            style = { border(1, 0xFF5E6A77.toInt()) }
        }) {
            text("Variable demo uses :root { --primary: ... } and var(--primary)")
            text("Try: .vars-demo { backgroundColor: var(--primary); borderColor: var(--accent); }", {
                color = DEMO_MUTED
            })
            text("focusInputValue='${window.stylesheetDemoTextValue}'", {
                color = DEMO_MUTED
            })
        }
    }
}