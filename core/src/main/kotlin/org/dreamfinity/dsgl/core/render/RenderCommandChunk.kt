package org.dreamfinity.dsgl.core.render

internal class RenderCommandChunk {
    val prefixCommands: MutableList<RenderCommand> = ArrayList(4)
    val selfCommands: MutableList<RenderCommand> = ArrayList(16)
    val suffixCommands: MutableList<RenderCommand> = ArrayList(4)
    val children: MutableList<RenderCommandChunk> = ArrayList(8)
    var lastNodeSignature: Long = Long.MIN_VALUE
    var lastChildrenSignature: Long = Long.MIN_VALUE
    var subtreeSignature: Long = Long.MIN_VALUE
}

