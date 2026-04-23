package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect

internal object InspectorPresentationSupport {
    fun nodeLabel(node: DOMNode): String {
        val key = node.key?.toString() ?: "<no-key>"
        return "${node.styleType}[$key]"
    }

    fun rectLabel(rect: Rect): String = "${rect.x},${rect.y},${rect.width}x${rect.height}"

    fun estimateMaxChars(pixelWidth: Int, fontSize: Int): Int {
        val approxCharWidth = (fontSize * 0.56f).toInt().coerceAtLeast(6)
        return (pixelWidth / approxCharWidth).coerceAtLeast(8)
    }

    fun wrapText(text: String, maxChars: Int): List<String> {
        val limit = maxChars.coerceAtLeast(1)
        if (text.length <= limit) return listOf(text)
        val result = ArrayList<String>()
        var cursor = 0
        while (cursor < text.length) {
            val end = (cursor + limit).coerceAtMost(text.length)
            var cut = end
            if (end < text.length) {
                val ws = text.lastIndexOf(' ', end - 1)
                if (ws >= cursor + 1) {
                    cut = ws
                }
            }
            if (cut <= cursor) {
                cut = end
            }
            result += text.substring(cursor, cut).trimEnd()
            cursor = cut
            while (cursor < text.length && text[cursor] == ' ') cursor++
        }
        return if (result.isEmpty()) listOf("") else result
    }

    fun pathToNode(root: DOMNode, target: DOMNode): List<DOMNode> {
        val path = ArrayList<DOMNode>(8)
        if (collectPath(root, target, path)) {
            return path
        }
        return listOf(target)
    }

    fun wrapPathLines(path: List<DOMNode>, maxChars: Int): List<String> {
        if (path.isEmpty()) return listOf("Path: <none>")
        val tokens = path.map(::pathToken)
        val lines = ArrayList<String>()
        var current = "Path: "
        tokens.forEachIndexed { index, token ->
            val segment = if (index == 0) token else " > $token"
            if (current.length + segment.length <= maxChars) {
                current += segment
                return@forEachIndexed
            }
            if (current.isNotBlank()) {
                lines += current
            }
            val continued = if (index == 0) token else "> $token"
            val wrapped = wrapText(continued, maxChars - 2)
            if (wrapped.isEmpty()) {
                current = "  "
            } else {
                lines += wrapped.dropLast(1).map { "  $it" }
                current = "  ${wrapped.last()}"
            }
        }
        lines += current
        return lines
    }

    fun wrapMinimizedLabel(text: String, maxCharsPerLine: Int, maxLines: Int): List<String> {
        val source = text.trim()
        if (source.isEmpty()) return listOf("")
        if (maxCharsPerLine <= 0 || maxLines <= 0) return listOf("")

        val lines = ArrayList<String>(maxLines)
        var cursor = 0
        while (cursor < source.length && lines.size < maxLines) {
            var end = (cursor + maxCharsPerLine).coerceAtMost(source.length)
            if (end < source.length) {
                val breakAt = source.lastIndexOf(' ', end - 1)
                if (breakAt >= cursor + 1) {
                    end = breakAt
                }
            }
            var line = source.substring(cursor, end).trim()
            if (line.isEmpty()) {
                end = (cursor + maxCharsPerLine).coerceAtMost(source.length)
                line = source.substring(cursor, end)
            }
            lines += line
            cursor = end
            while (cursor < source.length && source[cursor] == ' ') cursor++
        }
        if (cursor < source.length && lines.isNotEmpty()) {
            val last = lines.last()
            val keep = (maxCharsPerLine - 3).coerceAtLeast(0)
            val trimmed = last.take(keep).trimEnd()
            lines[lines.lastIndex] = if (trimmed.isEmpty()) "..." else "$trimmed..."
        }
        return lines
    }

    private fun collectPath(node: DOMNode, target: DOMNode, path: MutableList<DOMNode>): Boolean {
        path += node
        if (node === target) return true
        for (child in node.children) {
            if (collectPath(child, target, path)) {
                return true
            }
        }
        path.removeAt(path.lastIndex)
        return false
    }

    private fun pathToken(node: DOMNode): String {
        val key = node.key?.toString() ?: "?"
        return "${node.styleType}:$key"
    }
}
