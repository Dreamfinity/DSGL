package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.useState
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.cssCascadeCombinatorsSection(onLogHook: (String, Event, String?) -> Unit) {
    var cascadeParentDark by useState(false)
    var cascadeRuleAEnabled by useState(true)
    var cascadeAdjacentSourceEnabled by useState(true)
    var cascadeAdjacentSwapOrder by useState(false)
    var cascadeGeneralWarningIndex by useState(1L)
    var cascadeGeneralInsertExtra by useState(false)
    var cascadeMixedSpacerEnabled by useState(false)

    val parentThemeClass = if (cascadeParentDark) "dark" else "light"
    val ruleBlockClass = if (cascadeRuleAEnabled) "rule-a" else "rule-b"
    val adjacentOrder = if (cascadeAdjacentSwapOrder) {
        listOf("adj-target-1", "adj-source", "adj-target-2")
    } else {
        listOf("adj-source", "adj-target-1", "adj-target-2")
    }
    val generalItems = buildList {
        add("gen-0")
        if (cascadeGeneralInsertExtra) {
            add("gen-extra")
        }
        add("gen-1")
        add("gen-2")
        add("gen-3")
    }
    val effectiveWarningIndex = if (generalItems.isEmpty()) 0 else {
        (cascadeGeneralWarningIndex.toInt().coerceAtLeast(0)) % generalItems.size
    }

    div({
        key = "section.cssCascade"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("CSS-like cascade demo: descendant/child/sibling selectors, specificity, source order, !important, inheritance.")
        text(
            "Use the controls to toggle classes, swap siblings, and insert/remove items.",
            { style = { color = DEMO_MUTED } }
        )

        div({
            key = "section.cssCascade.controls"
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(
                if (cascadeParentDark) "Parent class: dark" else "Parent class: light",
                {
                    key = "section.cssCascade.toggleParentClass"
                    onMouseClick = { event ->
                        cascadeParentDark = !cascadeParentDark
                        onLogHook("css.cascade.toggle.parentClass", event, "dark=$cascadeParentDark")
                    }
                }
            )
            button(if (cascadeRuleAEnabled) "Rule block: A" else "Rule block: B", {
                key = "section.cssCascade.toggleRuleBlock"
                onMouseClick = { event ->
                    cascadeRuleAEnabled = !cascadeRuleAEnabled
                    onLogHook("css.cascade.toggle.ruleBlock", event, "ruleA=$cascadeRuleAEnabled")
                }
            })
        }

        div({
            key = "section.cssCascade.demoRoot"
            className = "cascade-demo-root $parentThemeClass $ruleBlockClass"
            style = {
                gap = 3.px
                display = Display.Flex
                flexDirection = FlexDirection.Column
            }
        }) {
            text("Inheritance target: this text should inherit parent color class '$parentThemeClass'.")
            text(
                "Descendant vs child: direct item should be green, nested item blue, outside item inherited.",
                { style = { color = DEMO_MUTED } }
            )

            div({
                key = "section.cssCascade.panel"
                className = "panel"
                style = {
                    gap = 2.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("panel > .item (direct)", {
                    key = "section.cssCascade.directItem"
                    className = "item direct-item"
                })
                div({
                    key = "section.cssCascade.nestedWrap"
                    style = {
                        gap = 1.px
                        display = Display.Flex
                        flexDirection = FlexDirection.Column
                    }
                }) {
                    text("panel .item (nested descendant)", {
                        key = "section.cssCascade.nestedItem"
                        className = "item nested-item"
                    })
                }
            }

            text("outside .panel item (inherit only)", {
                key = "section.cssCascade.outsideItem"
                className = "item outside-item"
            })

            button("Specificity target #primary.btn", {
                key = "section.cssCascade.primaryBtn"
                id = "primary"
                className = "btn"
                onMouseClick = { event ->
                    onLogHook("css.cascade.specificity.target", event, null)
                }
            })

            text("Source order target: later '.order-target' rule should win (green).", {
                key = "section.cssCascade.sourceOrder"
                className = "order-target"
            })
            text("Important target: !important should win (orange).", {
                key = "section.cssCascade.important"
                className = "important-target"
            })
            text("Rule block target: toggles between A/B classes on parent.", {
                key = "section.cssCascade.blockToggle"
                className = "toggle-target"
            })
        }

        div({
            key = "section.cssCascade.siblings"
            style = {
                gap = 3.px
                display = Display.Flex
                flexDirection = FlexDirection.Column
            }
        }) {
            text(
                "Adjacent sibling (+): only immediate .adj-target after .adj-source should change.",
                { style = { color = DEMO_MUTED } }
            )
            div({
                key = "section.cssCascade.adj.controls"
                style = {
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                button(
                    if (cascadeAdjacentSourceEnabled) "Source class: ON" else "Source class: OFF",
                    {
                        key = "section.cssCascade.adj.toggleSource"
                        onMouseClick = { event ->
                            cascadeAdjacentSourceEnabled = !cascadeAdjacentSourceEnabled
                            onLogHook(
                                "css.cascade.adj.toggleSource",
                                event,
                                "enabled=$cascadeAdjacentSourceEnabled"
                            )
                        }
                    }
                )
                button(if (cascadeAdjacentSwapOrder) "Order: swapped" else "Order: default", {
                    key = "section.cssCascade.adj.swap"
                    onMouseClick = { event ->
                        cascadeAdjacentSwapOrder = !cascadeAdjacentSwapOrder
                        onLogHook("css.cascade.adj.swap", event, "swap=$cascadeAdjacentSwapOrder")
                    }
                })
            }
            div({
                key = "section.cssCascade.adj.demo"
                className = "cascade-sibling-adj"
                style = {
                    gap = 3.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                adjacentOrder.forEach { item ->
                    val classNames = buildString {
                        append("adj-item ")
                        when (item) {
                            "adj-source" -> {
                                if (cascadeAdjacentSourceEnabled) append("adj-source")
                                else append("adj-neutral")
                            }

                            else -> append("adj-target")
                        }
                    }
                    text(item, {
                        key = "section.cssCascade.$item"
                        className = classNames
                    })
                }
            }

            text(
                "General sibling (~): all .gen-target after .warning should change.",
                { style = { color = DEMO_MUTED } }
            )
            div({
                key = "section.cssCascade.gen.controls"
                style = {
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                button("Move warning", {
                    key = "section.cssCascade.gen.moveWarning"
                    onMouseClick = { event ->
                        val size = generalItems.size.coerceAtLeast(1)
                        cascadeGeneralWarningIndex = (cascadeGeneralWarningIndex + 1L) % size
                        onLogHook(
                            "css.cascade.gen.moveWarning",
                            event,
                            "index=$cascadeGeneralWarningIndex"
                        )
                    }
                })
                button(if (cascadeGeneralInsertExtra) "Extra sibling: ON" else "Extra sibling: OFF", {
                    key = "section.cssCascade.gen.toggleExtra"
                    onMouseClick = { event ->
                        cascadeGeneralInsertExtra = !cascadeGeneralInsertExtra
                        onLogHook(
                            "css.cascade.gen.toggleExtra",
                            event,
                            "extra=$cascadeGeneralInsertExtra"
                        )
                    }
                })
            }
            div({
                key = "section.cssCascade.gen.demo"
                className = "cascade-sibling-general"
                style = {
                    gap = 3.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                generalItems.forEachIndexed { index, keySuffix ->
                    val classNames = if (index == effectiveWarningIndex) "warning gen-item" else "gen-target gen-item"
                    text(keySuffix, {
                        key = "section.cssCascade.$keySuffix"
                        className = classNames
                    })
                }
            }

            text(
                "Mixed chain: .cascade-mixed > .header + .body .title",
                { style = { color = DEMO_MUTED } }
            )
            button(if (cascadeMixedSpacerEnabled) "Spacer: ON (break +)" else "Spacer: OFF (adjacent)", {
                key = "section.cssCascade.mixed.toggleSpacer"
                onMouseClick = { event ->
                    cascadeMixedSpacerEnabled = !cascadeMixedSpacerEnabled
                    onLogHook(
                        "css.cascade.mixed.toggleSpacer",
                        event,
                        "spacer=$cascadeMixedSpacerEnabled"
                    )
                }
            })
            div({
                key = "section.cssCascade.mixed.demo"
                className = "cascade-mixed"
                style = {
                    gap = 2.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                text("header", {
                    key = "section.cssCascade.mixed.header"
                    className = "header"
                })
                if (cascadeMixedSpacerEnabled) {
                    text("spacer", {
                        key = "section.cssCascade.mixed.spacer"
                        className = "spacer"
                    })
                }
                div({
                    key = "section.cssCascade.mixed.body"
                    className = "body"
                    style = {
                        gap = 1.px
                        display = Display.Flex
                        flexDirection = FlexDirection.Column
                    }
                }) {
                    text("title", {
                        key = "section.cssCascade.mixed.title"
                        className = "title"
                    })
                }
            }
        }
    }
}
