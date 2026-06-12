package org.dreamfinity.dsgl.core.hooks

import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dsl.UiScope
import kotlin.reflect.KClass

fun <S, A> UiScope.useReducer(initialState: S, reducer: (S, A) -> S): Pair<S, (A) -> Unit> =
    requireHookOwnerWindow().useReducer(initialState = initialState, reducer = reducer)

internal fun <S, A> DsglWindow.useReducer(initialState: S, reducer: (S, A) -> S): Pair<S, (A) -> Unit> =
    createReducerHookPair(
        window = this,
        initialState = initialState,
        reducer = reducer,
    )

internal fun <S, A> createReducerHookPair(
    window: DsglWindow,
    initialState: S,
    reducer: (S, A) -> S,
): Pair<S, (A) -> Unit> {
    val runtime = window.hookRuntime()
    val signature =
        HookSignatures.reducer(
            stateClass = reducerStateClassOf(initialState),
            initialWasNull = initialState == null,
        )
    val resolved =
        runtime.resolveUnnamedTypedEntry(
            kind = HookEntryKind.Reducer,
            hookName = "useReducer",
            signature = signature,
            expectedRawType = HookStateCell::class.java,
        ) {
            HookStateCell(initialState) {
                window.onHookStateChanged()
            }
        }
    val stateCell = resolved.value
    val dispatch: (A) -> Unit = { action ->
        stateCell.write(reducer(stateCell.read(), action))
    }
    return stateCell.read() to dispatch
}

private fun reducerStateClassOf(value: Any?): KClass<*>? = value?.let { current -> current::class }
