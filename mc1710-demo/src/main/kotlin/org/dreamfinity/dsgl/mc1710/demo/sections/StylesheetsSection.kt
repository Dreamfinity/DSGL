package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.hooks.useMemo
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.stylesheetsSection(
    onLogHook: (String, Event, String?) -> Unit,
    onInfo: (String) -> Unit,
    loadStylesheetText: () -> String,
    saveStylesheetText: (String) -> Unit,
    onReloadStylesheets: () -> Unit
) {
    val initialLoad by useMemo {
        runCatching { loadStylesheetText() }
    }
    var stylesheetReloadCount by useState(0)
    var stylesheetDemoTextValue by useState("")
    var stylesheetDemoClickCount by useState(0)
    var stylesheetEditorValue by useState(initialLoad.getOrDefault(""))
    var stylesheetEditorStatus by useState(
        if (initialLoad.isSuccess) {
            "loaded"
        } else {
            "load failed: ${initialLoad.exceptionOrNull()?.javaClass?.simpleName ?: "unknown"}"
        }
    )

    div({
        key = "section.stylesheets"
        style = {
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
                value = stylesheetEditorValue
                style = {
                    width = 100.percent
                    height = 92.px
                }
                onInput = { event ->
                    stylesheetEditorValue = event.value
                }
                onValueChange = { event ->
                    stylesheetEditorValue = event.value
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
                    onMouseClick = { event ->
                        runCatching {
                            saveStylesheetText(stylesheetEditorValue)
                        }.onSuccess {
                            stylesheetEditorStatus = "saved"
                            onInfo("Stylesheet saved")
                        }.onFailure { ex ->
                            stylesheetEditorStatus = "save failed: ${ex.javaClass.simpleName}"
                        }
                        onLogHook("styles.editor.save", event, null)
                    }
                })
                button("Load", {
                    key = "styles.editor.load"
                    onMouseClick = { event ->
                        runCatching {
                            loadStylesheetText()
                        }.onSuccess { loaded ->
                            stylesheetEditorValue = loaded
                            stylesheetEditorStatus = "loaded"
                            onInfo("Stylesheet loaded")
                        }.onFailure { ex ->
                            stylesheetEditorStatus = "load failed: ${ex.javaClass.simpleName}"
                        }
                        onLogHook("styles.editor.load", event, null)
                    }
                })
                button("Reload stylesheets", {
                    key = "styles.reload.button"
                    id = "stylesReloadButton"
                    className = "primary"
                    onMouseClick = { event ->
                        onReloadStylesheets()
                        stylesheetReloadCount += 1
                        stylesheetEditorStatus = "reloaded #$stylesheetReloadCount"
                        onLogHook("styles.reload.onMouseClick", event, null)
                    }
                })
            }
            text(
                "status=$stylesheetEditorStatus; reloads=$stylesheetReloadCount; clicks=$stylesheetDemoClickCount",
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
                        stylesheetDemoClickCount += 1
                        onLogHook("styles.selector.type", event, null)
                    }
                })
                button(".accent", {
                    key = "styles.selector.class"
                    className = "accent"
                    onMouseClick = { event ->
                        stylesheetDemoClickCount += 1
                        onLogHook("styles.selector.class", event, null)
                    }
                })
                button("button.primary", {
                    key = "styles.selector.typeClass"
                    className = "primary"
                    onMouseClick = { event ->
                        stylesheetDemoClickCount += 1
                        onLogHook("styles.selector.typeClass", event, null)
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
                        stylesheetDemoClickCount += 1
                        onLogHook("styles.selector.id", event, null)
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
                        stylesheetDemoClickCount += 1
                        onLogHook("styles.selector.inline", event, null)
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
                    flexDirection = FlexDirection.Column
                }
            }) {
                button("Hover / Active target", {
                    key = "styles.state.hoverActive"
                    id = "hoverActiveTarget"
                    className = "interactive-demo"
                    onMouseClick = { event ->
                        stylesheetDemoClickCount += 1
                        onLogHook("styles.state.hoverActive", event, null)
                    }
                })
                input(
                    InputType.Text(
                        value = stylesheetDemoTextValue,
                        placeholder = "Focus target"
                    ),
                    {
                        key = "styles.state.focusInput"
                        id = "focusInput"
                        className = "interactive-demo"
                        style = { width = 100.percent }
                        onInput = { event ->
                            stylesheetDemoTextValue = event.value
                            onLogHook("styles.state.focusInput.onInput", event, "value=${event.value}")
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
            text("focusInputValue='$stylesheetDemoTextValue'", {
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
