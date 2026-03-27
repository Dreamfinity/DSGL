package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.ref.ElementHandle
import org.dreamfinity.dsgl.core.ref.useRef
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_SURFACE_ALT

fun UiScope.refsSection(window: ShowcaseWindow) {
    val inputRef by useRef<ElementHandle>()
    val panelRef by useRef<ElementHandle>()

    var hooksStateDemoMounted by useState(true)
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
    val hooksCallbackIdentityStatus = if (hooksCallbackPreviousIdentity == null) {
        "first render"
    } else if (hooksCallbackIdentity == hooksCallbackPreviousIdentity) {
        "stable"
    } else {
        "changed"
    }
    hooksCallbackIdentityRef.current = hooksCallbackIdentity

    var hooksEffectMounted by useState(true)
    var hooksEffectDep by useState(0)
    var hooksEffectNoise by useState(0)
    var hooksEffectLogRevision by useState(0)
    val hooksEffectLogBuffer by useRef(mutableListOf<String>())
    fun appendEffectLog(line: String) {
        val buffer = hooksEffectLogBuffer.current ?: return
        buffer += line
//        while (buffer.size > 8) {
//            buffer.removeAt(0)
//        }
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

    div({
        key = "section.refs"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Hooks showcase: useRef, useState, useMemo, useCallback, useEffect.")
        text("Keep blocks small and behavior-focused; use this section for manual hook verification.", {
            style = { color = DEMO_MUTED }
        })

        hookCard("useRef", "Object ref + callback ref + imperative handle checks") {
            input(
                InputType.Text(
                    value = window.refsInputValue,
                    placeholder = "Focusable input with stable key"
                ),
                {
                    key = "refs.input.primary"
                    onInput = { event ->
                        window.refsInputValue = event.value
                        window.logHook("refs.input.onInput", event, "value=${event.value}")
                    }
                },
                ref = inputRef
            )

            div({
                style = {
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                button("Focus via ref", {
//                    style = { width = 82.px }
                    onMouseClick = {
                        inputRef.current?.requestFocus()
                        window.appendInfo("Hooks/useRef: requestFocus() via object ref")
                    }
                })
                button("Rebuild", {
//                    style = { width = 56.px }
                    onMouseClick = {
                        window.refsRebuildCount += 1
                        window.requestManualInvalidate("hooks useRef rebuild")
                    }
                })
                button(if (window.refsCallbackMounted) "Unmount callback target" else "Mount callback target", {
//                    style = { width = 136.px }
                    onMouseClick = {
                        window.refsCallbackMounted = !window.refsCallbackMounted
                        window.appendInfo("Hooks/useRef callback target mounted=${window.refsCallbackMounted}")
                    }
                })
            }

            text({
                val hasRef = inputRef.current != null
                value = "objectRef.current set=$hasRef rebuilds=${window.refsRebuildCount}"
                style = { color = DEMO_MUTED }
            })

            div(
                {
                    key = "refs.bounds.panel"
                    style = {
//                        width = (contentWidth - 26).px
//                        height = 34.px
                        padding = 4.px
                        backgroundColor = 0xFF313844.toInt()
                    }
                },
                ref = panelRef
            ) {
                text("Bounds target panel", { style = { color = 0xFFE2EAFF.toInt() } })
            }

            text({
                val bounds = panelRef.current?.bounds
                value = if (bounds == null) {
                    "panelRef.current: null"
                } else {
                    "panelRef.bounds: x=${bounds.x} y=${bounds.y} w=${bounds.width} h=${bounds.height}"
                }
                style = { color = DEMO_MUTED }
            })

            if (window.refsCallbackMounted) {
                div(
                    {
                        key = "refs.callback.target"
                        style = {
//                            width = (contentWidth - 26).px
//                            height = 24.px
                            backgroundColor = 0xFF2F3C2F.toInt()
                            padding = 4.px
                        }
                    },
                    ref = window.refsCallbackRef
                ) {
                    text("Callback ref target", { style = { color = 0xFFC5E8C5.toInt() } })
                }
            }

            text(
                "callback attaches=${window.refsCallbackAttachCount} detaches=${window.refsCallbackDetachCount} last=${window.refsCallbackLast}",
                { style = { color = DEMO_MUTED } }
            )
        }

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
                        placeholder = "Local useState text"
                    ),
                    {
                        key = "hooks.useState.localText"
                        onInput = { event -> hooksStateLocalText = event.value }
                    }
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
}

private fun UiScope.hookCard(
    title: String,
    subtitle: String,
    content: UiScope.() -> Unit
) {
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


