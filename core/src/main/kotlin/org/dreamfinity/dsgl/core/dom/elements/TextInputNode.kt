package org.dreamfinity.dsgl.core.dom.elements

/**
 * Text input node.
 */
class TextInputNode(
    text: String = "",
    placeholder: String = "",
    allowedChars: String? = null,
    minLength: Int? = null,
    maxLength: Int? = null,
    key: Any? = null,
) : SingleLineInputNode(text, placeholder, key) {
    init {
        this.allowedChars = allowedChars
        this.minLength = minLength
        this.maxLength = maxLength
    }
}
