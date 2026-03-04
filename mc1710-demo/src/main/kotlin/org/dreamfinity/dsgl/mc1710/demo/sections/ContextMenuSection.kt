package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuRuntime
import org.dreamfinity.dsgl.core.contextmenu.contextMenu
import org.dreamfinity.dsgl.core.dom.onContextMenu
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_SURFACE_ALT

fun UiScope.renderContextMenuSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val files = listOf(
        "README.md",
        "build.gradle.kts",
        "mods.toml",
        "TexturesAtlas.kt",
        "notes.txt",
        "roadmap.md"
    )

    div(
        ComponentProps(
            key = "section.contextMenu",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("Right-click the background zone or any file row."))
        text(TextProps("Submenus open with hover intent, ESC pops one level, outside click closes all.").apply {
            color = DEMO_MUTED
        })
        text(
            TextProps {
                "lastTarget=${window.contextMenuLastTarget} lastAction=${window.contextMenuLastAction} actions=${window.contextMenuActionCount} pinned=${window.contextMenuPinned}"
            }.apply { color = DEMO_MUTED }
        )

        div(ComponentProps(gap = 4).asFlexRow()) {
            button(
                ButtonProps("Open programmatic menu").apply {
                    onMouseClick = {
                        ContextMenuRuntime.host.openAtCursor(buildBackgroundMenu(window), 42, 58)
                    }
                }
            )
            button(
                ButtonProps("Close menus").apply {
                    onMouseClick = {
                        ContextMenuRuntime.host.closeAll()
                    }
                }
            )
        }

        val backgroundNode = div(
            ComponentProps(
                key = "section.contextMenu.background",
                width = contentWidth - 8,
                height = 54,
                padding = 4,
                backgroundColor = DEMO_SURFACE_ALT,
                style = { border(1, 0xFF6E7A89.toInt()) }
            )
        ) {
            text(TextProps("Background area menu (cursor placement)"))
            text(TextProps("Includes disabled item, separators, 3+ levels and overflow scroll").apply {
                color = DEMO_MUTED
            })
        }
        backgroundNode.onContextMenu {
            openMenu(buildBackgroundMenu(window))
        }

        div(
            ComponentProps(
                key = "section.contextMenu.files",
                width = contentWidth - 8,
                gap = 2,
                padding = 3,
                backgroundColor = 0xFF303944.toInt(),
                style = { border(1, 0xFF627282.toInt()) }
            ).asFlexColumn()
        ) {
            text(TextProps("File list (anchored to row)"))
            files.forEachIndexed { index, fileName ->
                val rowKey = "section.contextMenu.file.$index"
                val selected = window.contextMenuFileSelection == fileName
                val rowNode = div(
                    ComponentProps(
                        key = rowKey,
                        width = contentWidth - 22,
                        padding = 3,
                        backgroundColor = if (selected) 0xFF3A5168.toInt() else 0xFF3A424D.toInt(),
                        style = { border(1, 0xFF566170.toInt()) }
                    )
                ) {
                    text(TextProps(fileName))
                }
                rowNode.onContextMenu {
                    openMenuAnchored(buildFileMenu(window, fileName), anchor = rowNode.bounds)
                }
            }
        }
    }
}

private fun buildBackgroundMenu(window: ShowcaseWindow) = contextMenu(id = "demo.context.background") {
    item("Refresh workspace", id = "refresh") {
        icon("[R]")
        onClick { window.recordContextMenuAction("background", "refresh workspace") }
    }
    item("Pin toolbar", id = "pin") {
        icon("[*]")
        checkedIf { window.contextMenuPinned }
        closeOnAction(false)
        onClick {
            window.contextMenuPinned = !window.contextMenuPinned
            window.recordContextMenuAction("background", "pin toolbar=${window.contextMenuPinned}")
        }
    }
    item("Paste", id = "paste") {
        enabledIf { window.contextMenuPinned }
        hint("Ctrl+V")
        onClick { window.recordContextMenuAction("background", "paste") }
    }
    separator("line.main")
    submenu("Create", id = "create") {
        item("Text file", id = "create.txt") {
            icon("[+]")
            onClick { window.recordContextMenuAction("background", "create text file") }
        }
        item("Folder", id = "create.folder") {
            icon("[+]")
            onClick { window.recordContextMenuAction("background", "create folder") }
        }
        submenu("Advanced", id = "create.advanced") {
            item("Module", id = "create.module") {
                onClick { window.recordContextMenuAction("background", "create module") }
            }
            submenu("Deep", id = "create.deep") {
                submenu("Level 3", id = "create.deep.l3") {
                    item("Script template", id = "create.script") {
                        onClick { window.recordContextMenuAction("background", "create script template") }
                    }
                }
            }
        }
    }
    separator("line.overflow")
    submenu("Recent files", id = "recent") {
        for (index in 1..26) {
            item("Recent item $index", id = "recent.$index") {
                hint("Alt+$index")
                onClick { window.recordContextMenuAction("background", "recent item $index") }
            }
        }
    }
}

private fun buildFileMenu(window: ShowcaseWindow, fileName: String) = contextMenu(id = "demo.context.file.$fileName") {
    item("Open", id = "file.open") {
        icon("[>]")
        onClick {
            window.contextMenuFileSelection = fileName
            window.recordContextMenuAction(fileName, "open")
        }
    }
    item("Rename", id = "file.rename") {
        closeOnAction(false)
        onClick {
            window.contextMenuFileSelection = fileName
            window.recordContextMenuAction(fileName, "rename")
        }
    }
    item("Delete", id = "file.delete") {
        enabledIf { fileName != "README.md" }
        onClick {
            window.contextMenuFileSelection = fileName
            window.recordContextMenuAction(fileName, "delete")
        }
    }
    separator("file.sep")
    submenu("Share", id = "file.share") {
        item("Copy path", id = "file.share.path") {
            hint("Ctrl+C")
            onClick { window.recordContextMenuAction(fileName, "copy path") }
        }
        submenu("Permissions", id = "file.share.perm") {
            item("Read-only", id = "file.perm.readonly") {
                checkedIf { window.contextMenuPinned }
                closeOnAction(false)
                onClick {
                    window.contextMenuPinned = !window.contextMenuPinned
                    window.recordContextMenuAction(fileName, "toggle read-only=${window.contextMenuPinned}")
                }
            }
            item("Public link", id = "file.perm.link") {
                onClick { window.recordContextMenuAction(fileName, "create public link") }
            }
        }
    }
}
