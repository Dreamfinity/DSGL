package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.renderCssCascadeCombinatorsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val parentThemeClass = if (window.cascadeParentDark) "dark" else "light"
    val ruleBlockClass = if (window.cascadeRuleAEnabled) "rule-a" else "rule-b"
    val adjacentOrder = if (window.cascadeAdjacentSwapOrder) {
        listOf("adj-target-1", "adj-source", "adj-target-2")
    } else {
        listOf("adj-source", "adj-target-1", "adj-target-2")
    }
    val generalItems = buildList {
        add("gen-0")
        if (window.cascadeGeneralInsertExtra) {
            add("gen-extra")
        }
        add("gen-1")
        add("gen-2")
        add("gen-3")
    }
    val effectiveWarningIndex = if (generalItems.isEmpty()) 0 else {
        (window.cascadeGeneralWarningIndex.toInt().coerceAtLeast(0)) % generalItems.size
    }

    div(
        ComponentProps(
            key = "section.cssCascade",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("CSS-like cascade demo: descendant/child/sibling selectors, specificity, source order, !important, inheritance."))
        text(TextProps("Use the controls to toggle classes, swap siblings, and insert/remove items.").apply {
            color = DEMO_MUTED
        })

        div(ComponentProps(key = "section.cssCascade.controls", gap = 4).asFlexRow()) {
            button(
                ButtonProps(if (window.cascadeParentDark) "Parent class: dark" else "Parent class: light").apply {
                    key = "section.cssCascade.toggleParentClass"
                    width = 132
                    onMouseClick = { event ->
                        window.cascadeParentDark = !window.cascadeParentDark
                        window.logHook("css.cascade.toggle.parentClass", event, "dark=${window.cascadeParentDark}")
                    }
                }
            )
            button(
                ButtonProps(if (window.cascadeRuleAEnabled) "Rule block: A" else "Rule block: B").apply {
                    key = "section.cssCascade.toggleRuleBlock"
                    width = 108
                    onMouseClick = { event ->
                        window.cascadeRuleAEnabled = !window.cascadeRuleAEnabled
                        window.logHook("css.cascade.toggle.ruleBlock", event, "ruleA=${window.cascadeRuleAEnabled}")
                    }
                }
            )
        }

        div(
            ComponentProps(
                key = "section.cssCascade.demoRoot",
                className = "cascade-demo-root $parentThemeClass $ruleBlockClass",
                gap = 3
            ).asFlexColumn()
        ) {
            text(TextProps("Inheritance target: this text should inherit parent color class '$parentThemeClass'."))
            text(TextProps("Descendant vs child: direct item should be green, nested item blue, outside item inherited.").apply {
                color = DEMO_MUTED
            })

            div(ComponentProps(key = "section.cssCascade.panel", className = "panel", gap = 2).asFlexColumn()) {
                text(TextProps("panel > .item (direct)").apply {
                    key = "section.cssCascade.directItem"
                    className = "item direct-item"
                })
                div(ComponentProps(key = "section.cssCascade.nestedWrap", gap = 1).asFlexColumn()) {
                    text(TextProps("panel .item (nested descendant)").apply {
                        key = "section.cssCascade.nestedItem"
                        className = "item nested-item"
                    })
                }
            }

            text(TextProps("outside .panel item (inherit only)").apply {
                key = "section.cssCascade.outsideItem"
                className = "item outside-item"
            })

            button(
                ButtonProps("Specificity target #primary.btn").apply {
                    key = "section.cssCascade.primaryBtn"
                    id = "primary"
                    className = "btn"
                    onMouseClick = { event ->
                        window.logHook("css.cascade.specificity.target", event)
                    }
                }
            )

            text(TextProps("Source order target: later '.order-target' rule should win (green).").apply {
                key = "section.cssCascade.sourceOrder"
                className = "order-target"
            })
            text(TextProps("Important target: !important should win (orange).").apply {
                key = "section.cssCascade.important"
                className = "important-target"
            })
            text(TextProps("Rule block target: toggles between A/B classes on parent.").apply {
                key = "section.cssCascade.blockToggle"
                className = "toggle-target"
            })
        }

        div(
            ComponentProps(
                key = "section.cssCascade.siblings",
                gap = 3
            ).asFlexColumn()
        ) {
            text(TextProps("Adjacent sibling (+): only immediate .adj-target after .adj-source should change.").apply {
                color = DEMO_MUTED
            })
            div(ComponentProps(key = "section.cssCascade.adj.controls", gap = 4).asFlexRow()) {
                button(
                    ButtonProps(if (window.cascadeAdjacentSourceEnabled) "Source class: ON" else "Source class: OFF").apply {
                        key = "section.cssCascade.adj.toggleSource"
                        width = 118
                        onMouseClick = { event ->
                            window.cascadeAdjacentSourceEnabled = !window.cascadeAdjacentSourceEnabled
                            window.logHook("css.cascade.adj.toggleSource", event, "enabled=${window.cascadeAdjacentSourceEnabled}")
                        }
                    }
                )
                button(
                    ButtonProps(if (window.cascadeAdjacentSwapOrder) "Order: swapped" else "Order: default").apply {
                        key = "section.cssCascade.adj.swap"
                        width = 108
                        onMouseClick = { event ->
                            window.cascadeAdjacentSwapOrder = !window.cascadeAdjacentSwapOrder
                            window.logHook("css.cascade.adj.swap", event, "swap=${window.cascadeAdjacentSwapOrder}")
                        }
                    }
                )
            }
            div(
                ComponentProps(
                    key = "section.cssCascade.adj.demo",
                    className = "cascade-sibling-adj",
                    gap = 3
                ).asFlexRow()
            ) {
                adjacentOrder.forEach { item ->
                    val classNames = buildString {
                        append("adj-item ")
                        when (item) {
                            "adj-source" -> {
                                if (window.cascadeAdjacentSourceEnabled) append("adj-source")
                                else append("adj-neutral")
                            }
                            else -> append("adj-target")
                        }
                    }
                    text(TextProps(item).apply {
                        key = "section.cssCascade.$item"
                        className = classNames
                    })
                }
            }

            text(TextProps("General sibling (~): all .gen-target after .warning should change.").apply {
                color = DEMO_MUTED
            })
            div(ComponentProps(key = "section.cssCascade.gen.controls", gap = 4).asFlexRow()) {
                button(
                    ButtonProps("Move warning").apply {
                        key = "section.cssCascade.gen.moveWarning"
                        width = 96
                        onMouseClick = { event ->
                            val size = generalItems.size.coerceAtLeast(1)
                            window.cascadeGeneralWarningIndex =
                                (window.cascadeGeneralWarningIndex + 1L) % size
                            window.logHook("css.cascade.gen.moveWarning", event, "index=${window.cascadeGeneralWarningIndex}")
                        }
                    }
                )
                button(
                    ButtonProps(if (window.cascadeGeneralInsertExtra) "Extra sibling: ON" else "Extra sibling: OFF").apply {
                        key = "section.cssCascade.gen.toggleExtra"
                        width = 118
                        onMouseClick = { event ->
                            window.cascadeGeneralInsertExtra = !window.cascadeGeneralInsertExtra
                            window.logHook("css.cascade.gen.toggleExtra", event, "extra=${window.cascadeGeneralInsertExtra}")
                        }
                    }
                )
            }
            div(
                ComponentProps(
                    key = "section.cssCascade.gen.demo",
                    className = "cascade-sibling-general",
                    gap = 3
                ).asFlexRow()
            ) {
                generalItems.forEachIndexed { index, keySuffix ->
                    val classNames = if (index == effectiveWarningIndex) "warning gen-item" else "gen-target gen-item"
                    text(TextProps(keySuffix).apply {
                        key = "section.cssCascade.$keySuffix"
                        className = classNames
                    })
                }
            }

            text(TextProps("Mixed chain: .cascade-mixed > .header + .body .title").apply {
                color = DEMO_MUTED
            })
            button(
                ButtonProps(if (window.cascadeMixedSpacerEnabled) "Spacer: ON (break +)" else "Spacer: OFF (adjacent)").apply {
                    key = "section.cssCascade.mixed.toggleSpacer"
                    width = 156
                    onMouseClick = { event ->
                        window.cascadeMixedSpacerEnabled = !window.cascadeMixedSpacerEnabled
                        window.logHook("css.cascade.mixed.toggleSpacer", event, "spacer=${window.cascadeMixedSpacerEnabled}")
                    }
                }
            )
            div(
                ComponentProps(
                    key = "section.cssCascade.mixed.demo",
                    className = "cascade-mixed",
                    gap = 2
                ).asFlexColumn()
            ) {
                text(TextProps("header").apply {
                    key = "section.cssCascade.mixed.header"
                    className = "header"
                })
                if (window.cascadeMixedSpacerEnabled) {
                    text(TextProps("spacer").apply {
                        key = "section.cssCascade.mixed.spacer"
                        className = "spacer"
                    })
                }
                div(ComponentProps(key = "section.cssCascade.mixed.body", className = "body", gap = 1).asFlexColumn()) {
                    text(TextProps("title").apply {
                        key = "section.cssCascade.mixed.title"
                        className = "title"
                    })
                }
            }
        }
    }
}
