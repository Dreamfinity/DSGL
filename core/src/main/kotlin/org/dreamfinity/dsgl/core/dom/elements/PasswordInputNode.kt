package org.dreamfinity.dsgl.core.dom.elements

/**
 * Password input node with masking.
 */
class PasswordInputNode(
    text: String = "",
    placeholder: String = "",
    minLength: Int? = null,
    maxLength: Int? = null,
    key: Any? = null,
) : SingleLineInputNode(text, placeholder, key) {
    init {
        this.minLength = minLength
        this.maxLength = maxLength
    }

    override fun displayText(): String = if (text.isEmpty()) "" else "*".repeat(text.length)

    override fun allowClipboardCopy(): Boolean = false

    override fun allowClipboardCut(): Boolean = false
}
