package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.event.KeyCodes
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Date/time input node rendered as formatted text.
 */
class DateInputNode(
    value: Instant,
    zoneId: ZoneId,
    placeholder: String = "dd.MM.yyyy HH:mm",
    key: Any? = null
) : SingleLineInputNode("", placeholder, key) {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    var value: Instant = value
        private set
    var zoneId: ZoneId = zoneId
        private set

    init {
        text = formatInstant(value)
        maxLength = 16
        allowedChars = "0123456789. :"
    }

    override fun canAcceptText(next: String): Boolean {
        if (!super.canAcceptText(next)) return false
        if (next.length > 16) return false
        if (!isValidPrefix(next)) return false
        if (next.length == 16 && parseInstant(next) == null) return false
        return true
    }

    override fun applyText(next: String) {
        text = next
        if (next.length == 16) {
            val parsed = parseInstant(next)
            if (parsed != null) {
                value = parsed
            }
        }
    }

    override fun handleKey(event: org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent) {
        if (event.keyCode == KeyCodes.ENTER) return
        super.handleKey(event)
    }

    private fun formatInstant(instant: Instant): String {
        val dateTime = LocalDateTime.ofInstant(instant, zoneId)
        return formatter.format(dateTime)
    }

    private fun parseInstant(text: String): Instant? {
        return try {
            val dateTime = LocalDateTime.parse(text, formatter)
            dateTime.atZone(zoneId).toInstant()
        } catch (ex: Exception) {
            null
        }
    }

    private fun isValidPrefix(text: String): Boolean {
        if (text.isEmpty()) return true
        for (i in text.indices) {
            val ch = text[i]
            val expected = when (i) {
                2, 5 -> '.'
                10 -> ' '
                13 -> ':'
                else -> null
            }
            if (expected != null) {
                if (ch != expected) return false
            } else if (!ch.isDigit()) {
                return false
            }
        }
        return true
    }
}
