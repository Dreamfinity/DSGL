package org.dreamfinity.dsgl.core.dom.elements

sealed interface TextSource {
    fun resolve(): String

    data class Static(private val value: String) : TextSource {
        override fun resolve(): String = value
    }

    data class Dynamic(private val supplier: () -> String) : TextSource {
        override fun resolve(): String = supplier()
    }
}