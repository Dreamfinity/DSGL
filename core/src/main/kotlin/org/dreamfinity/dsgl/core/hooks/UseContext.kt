@file:Suppress("MatchingDeclarationName")

package org.dreamfinity.dsgl.core.hooks

import org.dreamfinity.dsgl.core.dsl.UiScope

class DsglContext<T>(
    val name: String,
    val defaultValue: T,
)

fun <T> createContext(defaultValue: T, name: String = "Context"): DsglContext<T> =
    DsglContext(
        name = name,
        defaultValue = defaultValue,
    )

fun <T> UiScope.useContext(context: DsglContext<T>): T = readContextValue(context)

fun <T, R> UiScope.provideContext(context: DsglContext<T>, value: T, block: UiScope.() -> R): R =
    withProvidedContext(context = context, value = value, block = block)
