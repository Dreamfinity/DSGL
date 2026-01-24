package org.dreamfinity.dsgl.core.dom.elements

import java.time.Instant
import java.time.ZoneId

sealed class InputType {
    data class Text(
        val value: String = "",
        val placeholder: String = "",
        val allowedChars: String? = null,
        val minLength: Int? = null,
        val maxLength: Int? = null
    ) : InputType()

    data class Password(
        val value: String = "",
        val placeholder: String = "",
        val minLength: Int? = null,
        val maxLength: Int? = null
    ) : InputType()

    data class Number(
        val value: Long = 0L,
        val placeholder: String = "",
        val min: Long? = null,
        val max: Long? = null
    ) : InputType()

    data class Range(
        val value: Long = 0L,
        val min: Long = 0L,
        val max: Long = 100L,
        val step: Long? = null
    ) : InputType()

    data class Checkbox(
        val variants: List<InputOption>,
        val selected: Set<String> = emptySet(),
        val minSelected: Int? = null,
        val maxSelected: Int? = null
    ) : InputType()

    data class Radio(
        val variants: List<InputOption>,
        val selected: String? = null
    ) : InputType()

    data class Date(
        val value: Instant? = null,
        val zoneId: ZoneId? = null,
        val placeholder: String = "dd.MM.yyyy HH:mm"
    ) : InputType()
}
