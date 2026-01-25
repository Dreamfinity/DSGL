package org.dreamfinity.dsgl.core.dom.elements

import java.time.Instant
import java.time.ZoneId

/**
 * Input type definitions used by [org.dreamfinity.dsgl.core.DslKt.input].
 */
sealed class InputType {
    /** Single-line text input. */
    data class Text(
        val value: String = "",
        val placeholder: String = "",
        val allowedChars: String? = null,
        val minLength: Int? = null,
        val maxLength: Int? = null
    ) : InputType()

    /** Password input rendered with masking. */
    data class Password(
        val value: String = "",
        val placeholder: String = "",
        val minLength: Int? = null,
        val maxLength: Int? = null
    ) : InputType()

    /** Numeric input. */
    data class Number(
        val value: Long = 0L,
        val placeholder: String = "",
        val min: Long? = null,
        val max: Long? = null
    ) : InputType()

    /** Range slider input. */
    data class Range(
        val value: Long = 0L,
        val min: Long = 0L,
        val max: Long = 100L,
        val step: Long? = null
    ) : InputType()

    /** Checkbox group input. */
    data class Checkbox(
        val variants: List<InputOption>,
        val selected: Set<String> = emptySet(),
        val minSelected: Int? = null,
        val maxSelected: Int? = null
    ) : InputType()

    /** Radio group input. */
    data class Radio(
        val variants: List<InputOption>,
        val selected: String? = null
    ) : InputType()

    /** Date/time input. */
    data class Date(
        val value: Instant? = null,
        val zoneId: ZoneId? = null,
        val placeholder: String = "dd.MM.yyyy HH:mm"
    ) : InputType()
}
