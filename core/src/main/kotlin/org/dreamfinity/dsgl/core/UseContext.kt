package org.dreamfinity.dsgl.core

class DsglContext<T>(
    val name: String,
    val defaultValue: T
)

fun <T> createContext(defaultValue: T, name: String = "Context"): DsglContext<T> {
    return DsglContext(
        name = name,
        defaultValue = defaultValue
    )
}

fun <T> UiScope.useContext(context: DsglContext<T>): T {
    return readContextValue(context)
}

fun <T, R> UiScope.provideContext(
    context: DsglContext<T>,
    value: T,
    block: UiScope.() -> R
): R {
    return withProvidedContext(context = context, value = value, block = block)
}
