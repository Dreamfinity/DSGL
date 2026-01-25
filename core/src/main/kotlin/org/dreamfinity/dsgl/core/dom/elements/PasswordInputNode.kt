package org.dreamfinity.dsgl.core.dom.elements

class PasswordInputNode(
    text: String = "",
    placeholder: String = "",
    minLength: Int? = null,
    maxLength: Int? = null,
    key: Any? = null
) : SingleLineInputNode(text, placeholder, key) {
    init {
        this.minLength = minLength
        this.maxLength = maxLength
    }

    override fun displayText(): String {
        return if (text.isEmpty()) "" else "*".repeat(text.length)
    }
}
