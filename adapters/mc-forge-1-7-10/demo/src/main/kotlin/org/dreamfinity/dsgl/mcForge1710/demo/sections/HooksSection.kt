package org.dreamfinity.dsgl.mcForge1710.demo.sections

import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.hooks.createContext
import org.dreamfinity.dsgl.core.hooks.provideContext
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget
import org.dreamfinity.dsgl.core.hooks.ref.useRef
import org.dreamfinity.dsgl.core.hooks.useCallback
import org.dreamfinity.dsgl.core.hooks.useContext
import org.dreamfinity.dsgl.core.hooks.useEffect
import org.dreamfinity.dsgl.core.hooks.useMemo
import org.dreamfinity.dsgl.core.hooks.useReducer
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_SURFACE_ALT

private val hooksThemeContext = createContext(defaultValue = "System", name = "HooksTheme")

fun UiScope.hooksSection(onInfo: (String) -> Unit, onLogHook: (String, Event, String?) -> Unit) {
    div({
        key = "section.hooks"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Hooks showcase: useRef, useState, useMemo, useCallback, useReducer, useContext, useEffect.")
        text("Keep blocks small and behavior-focused; use this section for manual hook verification.", {
            style = { color = DEMO_MUTED }
        })

        overviewUseRef(onInfo = onInfo, onLogHook = onLogHook)
        overviewUseState()
        overviewUseMemo()
        overviewUseCallback()
        overviewUseReducer()
        overviewUseContext()
        overviewUseEffect()
    }
}

private fun UiScope.overviewUseRef(onInfo: (String) -> Unit, onLogHook: (String, Event, String?) -> Unit) {
    var refsInputValue by useState("Ref demo input")
    var refsRebuildCount by useState(0)
    var refsCallbackMounted by useState(true)
    var refsCallbackAttachCount by useState(0)
    var refsCallbackDetachCount by useState(0)
    var refsCallbackLast by useState("none")

    val inputRef by useRef<ElementHandle>()
    val panelRef by useRef<ElementHandle>()
    val refsCallbackRef by useMemo {
        RefTarget<ElementHandle> { handle ->
            if (handle == null) {
                refsCallbackDetachCount += 1
                refsCallbackLast = "detach"
                onInfo("Hooks/useRef callback detached")
                return@RefTarget
            }
            refsCallbackAttachCount += 1
            refsCallbackLast = "attach key=${handle.key}"
            onInfo("Hooks/useRef callback attached key=${handle.key}")
        }
    }

    hookCard("useRef", "Object ref + callback ref + imperative handle checks") {
        input(
            InputType.Text(
                value = refsInputValue,
                placeholder = "Focusable input with stable key",
            ),
            {
                key = "refs.input.primary"
                onInput = { event ->
                    refsInputValue = event.value
                    onLogHook("refs.input.onInput", event, "value=${event.value}")
                }
            },
            ref = inputRef,
        )

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Focus via ref", {
                onMouseClick = {
                    inputRef.current?.requestFocus()
                    onInfo("Hooks/useRef: requestFocus() via object ref")
                }
            })
            button("Rebuild", {
                onMouseClick = {
                    refsRebuildCount += 1
                }
            })
            button(if (refsCallbackMounted) "Unmount callback target" else "Mount callback target", {
                onMouseClick = {
                    refsCallbackMounted = !refsCallbackMounted
                    onInfo("Hooks/useRef callback target mounted=$refsCallbackMounted")
                }
            })
        }

        text({
            val hasRef = inputRef.current != null
            value = "objectRef.current set=$hasRef rebuilds=$refsRebuildCount"
            style = { color = DEMO_MUTED }
        })

        div(
            {
                key = "refs.bounds.panel"
                style = {
                    padding = 4.px
                    backgroundColor = 0xFF313844.toInt()
                }
            },
            ref = panelRef,
        ) {
            text("Bounds target panel", { style = { color = 0xFFE2EAFF.toInt() } })
        }

        text({
            val bounds = panelRef.current?.bounds
            value =
                if (bounds == null) {
                    "panelRef.current: null"
                } else {
                    "panelRef.bounds: x=${bounds.x} y=${bounds.y} w=${bounds.width} h=${bounds.height}"
                }
            style = { color = DEMO_MUTED }
        })

        if (refsCallbackMounted) {
            div(
                {
                    key = "refs.callback.target"
                    style = {
                        backgroundColor = 0xFF2F3C2F.toInt()
                        padding = 4.px
                    }
                },
                ref = refsCallbackRef,
            ) {
                text("Callback ref target", { style = { color = 0xFFC5E8C5.toInt() } })
            }
        }

        text(
            "callback attaches=$refsCallbackAttachCount detaches=$refsCallbackDetachCount last=$refsCallbackLast",
            { style = { color = DEMO_MUTED } },
        )
    }
}

private fun UiScope.overviewUseState() {
    var hooksStateDemoMounted by useState(true)
    hookCard("useState", "Local state + disappearance/reappearance reset") {
        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(if (hooksStateDemoMounted) "Hide local state sample" else "Show local state sample", {
                onMouseClick = { hooksStateDemoMounted = !hooksStateDemoMounted }
            })
        }

        if (hooksStateDemoMounted) {
            var hooksStateLocalCount by useState(0)
            var hooksStateLocalText by useState("fresh")
            button("Increment local count ($hooksStateLocalCount)", {
                onMouseClick = { hooksStateLocalCount += 1 }
            })
            input(
                InputType.Text(
                    value = hooksStateLocalText,
                    placeholder = "Local useState text",
                ),
                {
                    key = "hooks.useState.localText"
                    onInput = { event -> hooksStateLocalText = event.value }
                },
            )
            text("mounted state: count=$hooksStateLocalCount text=$hooksStateLocalText", {
                style = { color = DEMO_MUTED }
            })
        } else {
            text("Local state sample is hidden. Show it again: values should reset to initial.", {
                style = { color = DEMO_MUTED }
            })
        }
    }
}

private fun UiScope.overviewUseMemo() {
    var hooksMemoBase by useState(2)
    var hooksMemoMultiplier by useState(3)
    var hooksMemoNoise by useState(0)
    var hooksMemoRecomputeCount by useState(0)
    val hooksMemoDerived by useMemo(hooksMemoBase, hooksMemoMultiplier) {
        expensiveDerivedValue(hooksMemoBase, hooksMemoMultiplier)
    }
    useEffect(hooksMemoBase, hooksMemoMultiplier) {
        hooksMemoRecomputeCount += 1
    }

    hookCard("useMemo", "Derived value recomputes only when deps change") {
        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("base +1 ($hooksMemoBase)", {
                onMouseClick = { hooksMemoBase += 1 }
            })
            button("mult +1 ($hooksMemoMultiplier)", {
                onMouseClick = { hooksMemoMultiplier += 1 }
            })
            button("rerender only ($hooksMemoNoise)", {
                onMouseClick = { hooksMemoNoise += 1 }
            })
        }
        text("derived=$hooksMemoDerived recomputeCount=$hooksMemoRecomputeCount", {
            style = { color = DEMO_MUTED }
        })
        text("Expected: 'rerender only' changes noise but not recomputeCount.", {
            style = { color = DEMO_MUTED }
        })
    }
}

private fun UiScope.overviewUseCallback() {
    var hooksCallbackDep by useState(0)
    var hooksCallbackNoise by useState(0)
    var hooksCallbackInvokeCount by useState(0)
    var hooksCallbackLastInvoke by useState("none")
    val hooksCallback by useCallback(hooksCallbackDep) {
        val capturedDep = hooksCallbackDep
        {
            hooksCallbackInvokeCount += 1
            hooksCallbackLastInvoke = "invoke dep=$capturedDep"
        }
    }
    val hooksCallbackIdentityRef by useRef<Int>()
    val hooksCallbackIdentity = System.identityHashCode(hooksCallback as Any)
    val hooksCallbackPreviousIdentity = hooksCallbackIdentityRef.current
    val hooksCallbackIdentityStatus =
        if (hooksCallbackPreviousIdentity == null) {
            "first render"
        } else if (hooksCallbackIdentity == hooksCallbackPreviousIdentity) {
            "stable"
        } else {
            "changed"
        }
    hooksCallbackIdentityRef.current = hooksCallbackIdentity
    hookCard("useCallback", "Function identity is stable until dependency changes") {
        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Invoke callback", {
                onMouseClick = { hooksCallback() }
            })
            button("rerender only ($hooksCallbackNoise)", {
                onMouseClick = { hooksCallbackNoise += 1 }
            })
            button("dep +1 ($hooksCallbackDep)", {
                onMouseClick = { hooksCallbackDep += 1 }
            })
        }
        text("callback identity=$hooksCallbackIdentity ($hooksCallbackIdentityStatus)", {
            style = { color = DEMO_MUTED }
        })
        text("invokeCount=$hooksCallbackInvokeCount last=$hooksCallbackLastInvoke", {
            style = { color = DEMO_MUTED }
        })
        text("Expected: rerender-only keeps identity stable; dep change marks identity changed.", {
            style = { color = DEMO_MUTED }
        })
    }
}

private fun UiScope.overviewUseReducer() {
    var hooksReducerMounted by useState(true)
    var hooksReducerNoise by useState(0)
    hookCard("useReducer", "Reducer-driven local state + dispatch behavior") {
        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(if (hooksReducerMounted) "Hide reducer sample" else "Show reducer sample", {
                onMouseClick = { hooksReducerMounted = !hooksReducerMounted }
            })
            button("rerender only ($hooksReducerNoise)", {
                onMouseClick = { hooksReducerNoise += 1 }
            })
        }

        if (hooksReducerMounted) {
            val (hooksReducerCount, dispatchReducer) =
                useReducer(
                    initialState = 0,
                    reducer = { old: Int, action: Int -> old + action },
                )
            div({
                style = {
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                button("dispatch +1", {
                    onMouseClick = { dispatchReducer(1) }
                })
                button("dispatch +5", {
                    onMouseClick = { dispatchReducer(5) }
                })
                button("dispatch -1", {
                    onMouseClick = { dispatchReducer(-1) }
                })
            }
            text("state=$hooksReducerCount noise=$hooksReducerNoise", {
                style = { color = DEMO_MUTED }
            })
            text("Expected: dispatch changes reducer state; rerender-only keeps state unchanged.", {
                style = { color = DEMO_MUTED }
            })
        } else {
            text("Reducer sample is hidden. Show it again: reducer state should reinitialize to 0.", {
                style = { color = DEMO_MUTED }
            })
        }
    }
}

private fun UiScope.overviewUseContext() {
    var hooksContextProviderMounted by useState(true)
    var hooksContextNestedOverride by useState(false)
    var hooksContextValue by useState("Light")
    hookCard("useContext", "Nearest provider wins + nested override + default fallback") {
        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("provider=$hooksContextValue", {
                onMouseClick = {
                    hooksContextValue = if (hooksContextValue == "Light") "Dark" else "Light"
                }
            })
            button(if (hooksContextNestedOverride) "Disable nested override" else "Enable nested override", {
                onMouseClick = { hooksContextNestedOverride = !hooksContextNestedOverride }
            })
            button(if (hooksContextProviderMounted) "Hide provider" else "Show provider", {
                onMouseClick = { hooksContextProviderMounted = !hooksContextProviderMounted }
            })
        }

        if (hooksContextProviderMounted) {
            provideContext(hooksThemeContext, hooksContextValue) {
                val outerSeen = useContext(hooksThemeContext)
                text("outer consumer sees=$outerSeen", { style = { color = DEMO_MUTED } })

                if (hooksContextNestedOverride) {
                    provideContext(hooksThemeContext, "Nested") {
                        val nestedSeen = useContext(hooksThemeContext)
                        text("nested consumer sees=$nestedSeen", { style = { color = DEMO_MUTED } })
                    }
                    val afterNestedSeen = useContext(hooksThemeContext)
                    text("after nested block sees=$afterNestedSeen", { style = { color = DEMO_MUTED } })
                } else {
                    text("nested override disabled", { style = { color = DEMO_MUTED } })
                }
            }
        } else {
            val fallbackSeen = useContext(hooksThemeContext)
            text("provider hidden -> default fallback=$fallbackSeen", { style = { color = DEMO_MUTED } })
        }
    }
}

private fun UiScope.overviewUseEffect() {
    var hooksEffectMounted by useState(true)
    var hooksEffectDep by useState(0)
    var hooksEffectNoise by useState(0)
    var hooksEffectLogRevision by useState(0)
    val hooksEffectLogBuffer by useRef(mutableListOf<String>())

    fun appendEffectLog(line: String) {
        val buffer = hooksEffectLogBuffer.current ?: return
        buffer += line
        hooksEffectLogRevision += 1
    }
    if (hooksEffectMounted) {
        useEffect(hooksEffectDep) {
            val runDep = hooksEffectDep
            appendEffectLog("run dep=$runDep")
            onDispose {
                appendEffectLog("cleanup dep=$runDep")
            }
        }
    }

    hookCard("useEffect", "Post-commit run/cleanup log + hide/show cleanup behavior") {
        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("dep +1 ($hooksEffectDep)", {
                onMouseClick = { hooksEffectDep += 1 }
            })
            button(if (hooksEffectMounted) "Hide effect scope" else "Show effect scope", {
                onMouseClick = { hooksEffectMounted = !hooksEffectMounted }
            })
            button("rerender only ($hooksEffectNoise)", {
                onMouseClick = { hooksEffectNoise += 1 }
            })
            button("Clear log", {
                onMouseClick = {
                    hooksEffectLogBuffer.current?.clear()
                    hooksEffectLogRevision += 1
                }
            })
        }

        text("mounted=$hooksEffectMounted dep=$hooksEffectDep logRevision=$hooksEffectLogRevision", {
            style = { color = DEMO_MUTED }
        })
        div({
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Column
                overflowY = Overflow.Auto
                maxHeight = 8.em
            }
        }) {
            val logLines: List<String> = hooksEffectLogBuffer.current?.toList() ?: emptyList()
            if (logLines.isEmpty()) {
                text("log: <empty>", { style = { color = DEMO_MUTED } })
            } else {
                logLines.asReversed().forEach { line ->
                    text("log: $line", { style = { color = DEMO_MUTED } })
                }
            }
        }
    }
}

private fun UiScope.hookCard(title: String, subtitle: String, content: UiScope.() -> Unit) {
    div({
        key = "hooks.card.$title"
        style = {
            backgroundColor = DEMO_SURFACE_ALT
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
            padding = 4.px
        }
    }) {
        text(title)
        text(subtitle, { style = { color = DEMO_MUTED } })
        content()
    }
}

private fun expensiveDerivedValue(base: Int, multiplier: Int): Int {
    var acc = 0
    val iterations = (base.coerceAtLeast(1) * multiplier.coerceAtLeast(1)).coerceAtMost(5000)
    for (index in 1..iterations) {
        acc = (acc + (base * multiplier) + index) % 10007
    }
    return acc
}
