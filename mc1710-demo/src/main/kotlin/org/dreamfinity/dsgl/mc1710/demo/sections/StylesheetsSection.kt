package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.stylesheetsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.stylesheets"
        style = {
            width = contentWidth.px
            height = contentHeight.px
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("This section demonstrates DSS selectors, pseudo-states, vars and inline override.")
        text("Edit <gameDir>/dsgl/styles/*.dss then click Reload stylesheets.", {
            style = { color = DEMO_MUTED }
        })

        div({
            id = "stylesEditorCard"
            key = "styles.editor.card"
            className = "style-card editor"
            style = {
                padding = 4.px
                gap = 3.px
                border(1.px, 0xFF5E6A77.toInt())
            }
        }) {
            text("Demo stylesheet editor: showcase_styles.dss")
            textarea({
                placeholder = "Stylesheet content"
                key = "styles.editor.textarea"
                value = window.stylesheetEditorValue
                style = {
                    width = ((contentWidth - 22).coerceAtLeast(120)).px
                    height = 92.px
                }
                onInput = { event ->
                    window.stylesheetEditorValue = event.value
                }
                onValueChange = { event ->
                    window.stylesheetEditorValue = event.value
                }
            })

            div({
                style = {
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                button("Save", {
                    key = "styles.editor.save"
                    style = { width = 50.px }
                    onMouseClick = { event ->
                        window.saveStylesheetEditorToFile("styles section save")
                        window.logHook("styles.editor.save", event)
                    }
                })
                button("Load", {
                    key = "styles.editor.load"
                    style = { width = 50.px }
                    onMouseClick = { event ->
                        window.loadStylesheetEditorFromFile("styles section load")
                        window.logHook("styles.editor.load", event)
                    }
                })
                button("Reload stylesheets", {
                    key = "styles.reload.button"
                    id = "stylesReloadButton"
                    className = "primary"
                    style = { width = 112.px }
                    onMouseClick = { event ->
                        window.reloadStylesheetsProgrammatically("styles section button")
                        window.logHook("styles.reload.onMouseClick", event)
                    }
                })
            }
            text(
                "status=${window.stylesheetEditorStatus}; reloads=${window.stylesheetReloadCount}; clicks=${window.stylesheetDemoClickCount}",
                { style = { color = DEMO_MUTED } }
            )
        }

        div({
            id = "stylesSelectorsCard"
            key = "styles.selectors.card"
            className = "style-card selectors"
            style = {
                padding = 4.px
                gap = 3.px
                border(1.px, 0xFF5E6A77.toInt())
            }
        }) {
            text("Selector matrix", {
                id = "stylesSelectorsTitle"
            })
            text("Targets: button, .accent, button.primary, #dangerAction", {
                style = { color = DEMO_MUTED }
            })

            div({
                style = {
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
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

            div({
                style = {
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
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
                        borderWidth(1.px)
                    }
                    onMouseClick = { event ->
                        window.stylesheetDemoClickCount += 1
                        window.logHook("styles.selector.inline", event)
                    }
                })
            }
        }

        div({
            id = "stylesStatesCard"
            key = "styles.states.card"
            className = "style-card states"
            style = {
                padding = 4.px
                gap = 3.px
                border(1.px, 0xFF5E6A77.toInt())
            }
        }) {
            text("Pseudo-states: :hover, :active, :focus, :disabled")
            div({
                style = {
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
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
                        style = { width = 98.px }
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
            style = {
                padding = 4.px
                gap = 2.px
                border(1.px, 0xFF5E6A77.toInt())
            }
        }) {
            text("Variable demo uses :root { --primary: ... } and var(--primary)")
            text("Try: .vars-demo { backgroundColor: var(--primary); borderColor: var(--accent); }", {
                style = { color = DEMO_MUTED }
            })
            text("focusInputValue='${window.stylesheetDemoTextValue}'", {
                style = { color = DEMO_MUTED }
            })
        }

        div({
            id = "stylesUnitsCard"
            key = "styles.units.card"
            className = "style-card units-demo"
            style = {
                padding = 4.px
                gap = 3.px
                border(1.px, 0xFF5E6A77.toInt())
            }
        }) {
            text("CSS units demo: px, em, %, vw, vh")
            text("Resize window to see vw/vh change; % is relative to the playground.", {
                style = { color = DEMO_MUTED }
            })

            div({
                key = "styles.units.vwChip"
                className = "units-vw-chip"
                style = { height = 12.px }
            }) {
                text("20vw")
            }

            div({
                key = "styles.units.playground"
                className = "units-playground"
                style = { height = 66.px }
            }) {
                div({
                    key = "styles.units.percentBox"
                    className = "units-percent-box"
                }) {
                    text("50% x 40%")
                }
            }

            text("1.25em text with 1em spacing", {
                className = "units-em-text"
            })

            div({
                key = "styles.units.vhBar"
                className = "units-vh-bar"
            }) {
                text("8vh")
            }
        }
    }
}


