package org.dreamfinity.dsgl.mcForge1122.demo.examples.containers

import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.style.AlignItems
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.JustifyContent

internal fun UiScope.centeredFlexWrapper(direction: FlexDirection = FlexDirection.Column, content: UiScope.() -> Unit) =
    div({
        style {
            width = 100.percent
            height = 100.percent
            display = Display.Flex
            flexDirection = direction
            alignItems = AlignItems.Center
            justifyContent = JustifyContent.Center
            backgroundColor = 0xbb1A2230.toInt()
        }
    }) {
        content()
    }