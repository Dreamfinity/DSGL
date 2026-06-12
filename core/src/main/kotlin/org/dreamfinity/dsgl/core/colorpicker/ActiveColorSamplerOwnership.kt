package org.dreamfinity.dsgl.core.colorpicker

sealed interface ActiveColorSamplerOwner {
    data object None : ActiveColorSamplerOwner

    data object Popup : ActiveColorSamplerOwner

    data class Inline(
        val token: Any,
    ) : ActiveColorSamplerOwner
}

/**
 * Tracks the currently authoritative sampler owner across popup/inline pipette sessions.
 */
class ActiveColorSamplerOwnershipRouter {
    private var activeOwner: ActiveColorSamplerOwner = ActiveColorSamplerOwner.None
    private var previousPopupActive: Boolean = false
    private var previousInlineActiveTokens: Set<Any> = emptySet()

    fun owner(): ActiveColorSamplerOwner = activeOwner

    fun reset() {
        activeOwner = ActiveColorSamplerOwner.None
        previousPopupActive = false
        previousInlineActiveTokens = emptySet()
    }

    fun update(popupEyedropperActive: Boolean, inlineActiveTokens: Set<Any>): ActiveColorSamplerOwner {
        val currentInlineTokens = inlineActiveTokens.toSet()
        val popupActivatedNow = popupEyedropperActive && !previousPopupActive
        val inlineActivatedNow =
            currentInlineTokens.firstOrNull { token ->
                !previousInlineActiveTokens.contains(token)
            }

        val next =
            when {
                inlineActivatedNow != null -> ActiveColorSamplerOwner.Inline(inlineActivatedNow)
                activeOwner is ActiveColorSamplerOwner.Inline &&
                    currentInlineTokens.contains((activeOwner as ActiveColorSamplerOwner.Inline).token) -> activeOwner
                popupActivatedNow -> ActiveColorSamplerOwner.Popup
                activeOwner === ActiveColorSamplerOwner.Popup && popupEyedropperActive -> ActiveColorSamplerOwner.Popup
                currentInlineTokens.isNotEmpty() -> ActiveColorSamplerOwner.Inline(currentInlineTokens.first())
                popupEyedropperActive -> ActiveColorSamplerOwner.Popup
                else -> ActiveColorSamplerOwner.None
            }

        activeOwner = next
        previousPopupActive = popupEyedropperActive
        previousInlineActiveTokens = currentInlineTokens
        return next
    }
}
