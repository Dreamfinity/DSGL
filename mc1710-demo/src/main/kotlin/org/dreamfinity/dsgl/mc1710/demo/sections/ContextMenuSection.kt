package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuRuntime
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuStyle
import org.dreamfinity.dsgl.core.contextmenu.contextMenu
import org.dreamfinity.dsgl.core.dnd.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.dom.onContextMenu
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.style.AlignItems
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.JustifyContent
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private const val TILE_WIDTH = 86
private const val TILE_ICON_SIZE = 30
private const val TILE_GHOST_SIZE = 30
private const val ICON_FOLDER = "file://demo/folder.png"
private const val ICON_DOCUMENT = "file://demo/document.png"

fun UiScope.contextMenuSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val entries = window.contextMenuVisibleFiles()
    val listWidth = (contentWidth - 16).coerceAtLeast(120)
    val gridColumns = (listWidth / (TILE_WIDTH + 10)).coerceAtLeast(1)

    ContextMenuRuntime.engine.setStyle(
        ContextMenuStyle(
            panelPaddingX = 4,
            panelPaddingY = 4,
            rowPaddingX = 6,
            rowPaddingY = 2,
            rowGap = 1,
            iconColumnMinWidth = 13,
            minPanelWidth = 158,
            panelBackgroundColor = 0xFF212833.toInt(),
            panelBorderColor = 0xFF5F7387.toInt(),
            panelShadowColor = 0x7C0E1520,
            itemHoverBackgroundColor = 0xFF33506B.toInt(),
            itemSelectedBackgroundColor = 0xFF2A4155.toInt(),
            itemTextColor = 0xFFF1F6FC.toInt(),
            disabledTextColor = 0xFF8D98A4.toInt(),
            hintTextColor = 0xFFC5D2E1.toInt(),
            separatorColor = 0xFF4C6074.toInt(),
            checkMarkColor = 0xFF8BD59D.toInt(),
            submenuArrowColor = 0xFFC9D7E6.toInt()
        )
    )

    div({
        key = "section.contextMenu"
        width = contentWidth
        height = contentHeight
        gap = 4
        asFlexColumn()
    }) {
        text("Pseudo filesystem: tile view + context menu + drag/drop")
        text(
            "path=${window.contextMenuCurrentPath()} sort=${window.contextMenuSortMode} selected=${window.contextMenuFileSelection}",
            { color = DEMO_MUTED }
        )
        text(
            "lastAction=${window.contextMenuLastAction} target=${window.contextMenuLastTarget} actions=${window.contextMenuActionCount}",
            { color = DEMO_MUTED }
        )

        div({ gap = 4; asFlexRow() }) {
            button("New File", {
                onMouseClick = { window.contextMenuCreateFile() }
            })
            button("New Folder", {
                onMouseClick = { window.contextMenuCreateFolder() }
            })
        }

        div({
            key = "section.contextMenu.window"
            width = contentWidth - 8
            height = (contentHeight - 58).coerceAtLeast(80)
            padding = 3
            gap = 2
            backgroundColor = 0xFF2A313B.toInt()
            style = { border(1, 0xFF5B6A7A.toInt()) }
            asFlexColumn()
        }) {
            div({
                key = "section.contextMenu.pathbar"
                width = contentWidth - 16
                padding = 2
                gap = 2
                backgroundColor = 0xFF25303A.toInt()
                style = { display = Display.Flex; flexDirection = FlexDirection.Row; border(1, 0xFF4F6175.toInt()) }
            }) {
                button("<", {
                    key = "section.contextMenu.path.back"
                    onMouseClick = { window.contextMenuNavigateBack() }
                    disabled = !window.contextMenuCanGoBack()
                })
                button(">", {
                    key = "section.contextMenu.path.forward"
                    onMouseClick = { window.contextMenuNavigateForward() }
                    disabled = !window.contextMenuCanGoForward()
                })
                val breadcrumbs = window.contextMenuBreadcrumbs()
                breadcrumbs.forEachIndexed { index, breadcrumb ->
                    if (index > 0) {
                        text("/", { color = DEMO_MUTED })
                    }
                    val breadcrumbKey = "section.contextMenu.path.${breadcrumb.id}"
                    val breadcrumbDrop = window.useDroppable(
                        id = "context.fs.path.${breadcrumb.id}",
                        nodeKey = breadcrumbKey,
                        accepts = { active ->
                            val activeId = active.id ?: return@useDroppable false
                            window.contextMenuCanDropIntoDirectory(activeId, breadcrumb.id)
                        },
                        onDragEnter = { event, active ->
                            if (event.target?.key != breadcrumbKey) return@useDroppable
                            val activeId = active?.id ?: return@useDroppable
                            if (window.contextMenuCanDropIntoDirectory(activeId, breadcrumb.id)) {
                                window.contextMenuDragHoverDirectoryId = breadcrumb.id
                                event.cancelled = true
                            }
                        },
                        onDragOver = { event, active ->
                            if (event.target?.key != breadcrumbKey) return@useDroppable
                            val activeId = active?.id ?: return@useDroppable
                            if (window.contextMenuCanDropIntoDirectory(activeId, breadcrumb.id)) {
                                window.contextMenuDragHoverDirectoryId = breadcrumb.id
                                event.cancelled = true
                            }
                        },
                        onDragLeave = { event, _ ->
                            if (event.target?.key != breadcrumbKey) return@useDroppable
                            if (window.contextMenuDragHoverDirectoryId == breadcrumb.id) {
                                window.contextMenuDragHoverDirectoryId = null
                            }
                            event.cancelled = true
                        },
                        onDrop = { event, active ->
                            if (event.target?.key != breadcrumbKey) return@useDroppable
                            val moving = window.contextMenuEntryById(active?.id) ?: return@useDroppable
                            window.contextMenuMoveFile(moving, breadcrumb.id)
                            event.cancelled = true
                        }
                    )
                    val isCurrent = breadcrumb.id == window.contextMenuCurrentDirectoryId
                    val isDropHover = window.contextMenuDragHoverDirectoryId == breadcrumb.id
                    button(breadcrumb.label, {
                        key = breadcrumbKey
                        backgroundColor = when {
                            isDropHover -> 0xFF40617F.toInt()
                            isCurrent -> 0xFF364A5E.toInt()
                            else -> 0xFF2B3A4A.toInt()
                        }
                        onMouseClick = {
                            window.contextMenuOpenDirectory(breadcrumb.id, pushHistory = true)
                        }
                        style = {
                            border(1, if (isDropHover) 0xFF9BC2E9.toInt() else 0xFF5B6F84.toInt())
                        }
                        applyDroppable(breadcrumbDrop)
                    })
                }
            }

            val listDroppable = window.useDroppable(
                id = "context.fs.current.${window.contextMenuCurrentDirectoryId}",
                nodeKey = "section.contextMenu.list",
                accepts = { active ->
                    val activeId = active.id ?: return@useDroppable false
                    window.contextMenuCanDropIntoDirectory(activeId, window.contextMenuCurrentDirectoryId)
                },
                onDragEnter = { event, active ->
                    if (event.target?.key != "section.contextMenu.list") return@useDroppable
                    val activeId = active?.id
                    if (activeId != null && window.contextMenuCanDropIntoDirectory(
                            activeId,
                            window.contextMenuCurrentDirectoryId
                        )
                    ) {
                        window.contextMenuDragHoverDirectoryId = window.contextMenuCurrentDirectoryId
                    }
                },
                onDragOver = { event, active ->
                    if (event.target?.key != "section.contextMenu.list") return@useDroppable
                    val activeId = active?.id
                    if (activeId != null && window.contextMenuCanDropIntoDirectory(
                            activeId,
                            window.contextMenuCurrentDirectoryId
                        )
                    ) {
                        window.contextMenuDragHoverDirectoryId = window.contextMenuCurrentDirectoryId
                    }
                },
                onDragLeave = { event, _ ->
                    if (event.target?.key != "section.contextMenu.list") return@useDroppable
                    if (window.contextMenuDragHoverDirectoryId == window.contextMenuCurrentDirectoryId) {
                        window.contextMenuDragHoverDirectoryId = null
                    }
                },
                onDrop = { event, active ->
                    if (event.target?.key != "section.contextMenu.list") return@useDroppable
                    val moving = window.contextMenuEntryById(active?.id) ?: return@useDroppable
                    window.contextMenuMoveFile(moving, window.contextMenuCurrentDirectoryId)
                }
            )

            val listNode = div({
                key = "section.contextMenu.list"
                width = listWidth
                gap = 8
                padding = 4
                backgroundColor = if (window.contextMenuDragHoverDirectoryId == window.contextMenuCurrentDirectoryId) {
                    0xFF2F4358.toInt()
                } else {
                    0xFF2B343F.toInt()
                }
                style = {
                    border(1, 0xFF4F6175.toInt())
                    display = Display.Grid
                    this.gridColumns = gridColumns
                    gap = 4
                }
                applyDroppable(listDroppable)
            }) {
                if (entries.isEmpty()) {
                    div({
                        width = listWidth - 8
                        padding = 2

                    }) {
                        text(
                            "Folder is empty. Right-click to create file/folder.",
                            { color = DEMO_MUTED }
                        )
                    }
                } else {
                    entries.forEach { file ->
                        contextMenuEntryTile(window, file)
                    }
                }
            }
            listNode.onContextMenu {
                val anchorX = anchorRect?.x ?: mouseX
                val anchorY = anchorRect?.y ?: mouseY
                window.recordContextMenuCursor(
                    owner = "background",
                    mouseX = mouseX,
                    mouseY = mouseY,
                    localX = mouseX - anchorX,
                    localY = mouseY - anchorY
                )
                openMenu(buildBackgroundMenu(window))
            }
        }
    }
}

private fun UiScope.contextMenuEntryTile(
    window: ShowcaseWindow,
    file: ShowcaseWindow.ContextMenuDemoFile
) {
    val tileKey = "context.fs.tile.${file.id}"
    val iconURL = iconFor(file)
    val draggable = window.useDraggable(
        id = file.id,
        nodeKey = tileKey,
        type = "context.fs.entry",
        data = file.id,
        previewMode = DragPreviewMode.GHOST,
        hideSourceWhileDragging = false,
        renderPreview = {
            val offset = TILE_GHOST_SIZE / 2
            image(iconURL, -offset, -offset, TILE_GHOST_SIZE, TILE_GHOST_SIZE)
            rect(-offset, -offset, TILE_GHOST_SIZE, TILE_GHOST_SIZE, 0x66000000)
        },
        onDragStart = { event ->
            event.dataTransfer.setDragImage(tileKey, 0, 0)
        }
    )
    val droppable = if (file.isDirectory) {
        window.useDroppable(
            id = "context.fs.dir.${file.id}",
            nodeKey = tileKey,
            accepts = { active ->
                val activeId = active.id ?: return@useDroppable false
                window.contextMenuCanDropIntoDirectory(activeId, file.id)
            },
            onDragEnter = { event, active ->
                val activeId = active?.id
                if (activeId != null && window.contextMenuCanDropIntoDirectory(activeId, file.id)) {
                    window.contextMenuDragHoverDirectoryId = file.id
                    event.cancelled = true
                }
            },
            onDragOver = { event, active ->
                val activeId = active?.id
                if (activeId != null && window.contextMenuCanDropIntoDirectory(activeId, file.id)) {
                    window.contextMenuDragHoverDirectoryId = file.id
                    event.cancelled = true
                }
            },
            onDragLeave = { event, _ ->
                if (window.contextMenuDragHoverDirectoryId == file.id) {
                    window.contextMenuDragHoverDirectoryId = null
                }
                event.cancelled = true
            },
            onDrop = { event, active ->
                val moving = window.contextMenuEntryById(active?.id) ?: return@useDroppable
                window.contextMenuMoveFile(moving, file.id)
                event.cancelled = true
            }
        )
    } else {
        null
    }
    val isEditingName = window.contextMenuRenameTargetId == file.id
    val isSelected = window.contextMenuFileSelection == file.name
    val isDropHover = window.contextMenuDragHoverDirectoryId == file.id
    val tileNode = div({
        key = tileKey
        backgroundColor = when {
            isDropHover -> 0xFF43607A.toInt()
            isSelected -> 0xFF3A5168.toInt()
            else -> 0xFF33414E.toInt()
        }
        onMouseClick = { event ->
            if (!isEditingName && event.mouseButton == MouseButton.LEFT) {
                window.contextMenuHandleEntryClick(file)
            }
        }
        style = {
            border(1, if (isDropHover) 0xFF9BC2E9.toInt() else 0xFF596B7D.toInt())
            alignItems = AlignItems.Center
            justifyContent = JustifyContent.Center
        }
        asFlexColumn()
        applyDraggable(draggable)
        if (droppable != null) {
            applyDroppable(droppable)
        }
    }) {
        img(iconURL, {
            width = TILE_ICON_SIZE
            height = TILE_ICON_SIZE
        })
        if (isEditingName) {
            input(
                InputType.Text(
                    value = window.contextMenuRenameDraft,
                    placeholder = "Name"
                ),
                {
                    key = "contextMenu.rename.inline.${file.id}"
                    onInput = { event ->
                        window.contextMenuRenameDraft = event.value
                    }
                    onKeyDown = { event ->
                        when (event.keyCode) {
                            KeyCodes.ENTER -> window.contextMenuApplyRename()
                            KeyCodes.ESCAPE -> window.contextMenuCancelRename()
                        }
                    }
                }
            )
        } else {
            text(file.name, {
                color = if (file.locked) 0xFFE9A56E.toInt() else 0xFFEAF2FD.toInt()
            })
        }
    }
    tileNode.onContextMenu {
        val anchorX = anchorRect?.x ?: mouseX
        val anchorY = anchorRect?.y ?: mouseY
        window.recordContextMenuCursor(
            owner = "file:${file.id}",
            mouseX = mouseX,
            mouseY = mouseY,
            localX = mouseX - anchorX,
            localY = mouseY - anchorY
        )
        openMenu(buildEntryMenu(window, file))
    }
}

private fun iconFor(file: ShowcaseWindow.ContextMenuDemoFile): String {
    return if (file.isDirectory) ICON_FOLDER else ICON_DOCUMENT
}

private fun buildBackgroundMenu(window: ShowcaseWindow) = contextMenu(id = "demo.context.background") {
    submenu("Create", id = "create") {
        icon("+")
        item("File", id = "create.file") {
            icon("FI")
            onClick { window.contextMenuCreateFile() }
        }
        item("Directory", id = "create.dir") {
            icon("FD")
            onClick { window.contextMenuCreateFolder() }
        }
    }

    item("Paste", id = "paste") {
        icon("CL")
        hint("Ctrl+V")
        enabledIf { window.contextMenuClipboardHasData }
        onClick { window.contextMenuPasteIntoWorkspace() }
    }

    submenu("Sort by", id = "sort") {
        icon("AZ")
        item("Name", id = "sort.name") {
            checkedIf { window.contextMenuSortMode == "Name" }
            onClick { window.contextMenuSetSortMode("Name") }
        }
        item("Date", id = "sort.date") {
            checkedIf { window.contextMenuSortMode == "Date" }
            onClick { window.contextMenuSetSortMode("Date") }
        }
        item("Size", id = "sort.size") {
            checkedIf { window.contextMenuSortMode == "Size" }
            onClick { window.contextMenuSetSortMode("Size") }
        }
    }

    separator("main.sep")

    item("Refresh", id = "refresh") {
        icon("RF")
        onClick { window.contextMenuRefreshWorkspace() }
    }
}

private fun buildEntryMenu(window: ShowcaseWindow, file: ShowcaseWindow.ContextMenuDemoFile) =
    contextMenu(id = "demo.context.entry.${file.id}") {
        if (file.isDirectory) {
            item("Open", id = "entry.open") {
                icon("OP")
                onClick { window.contextMenuOpenDirectory(file.id, pushHistory = true) }
            }
            submenu("Create Inside", id = "entry.createInside") {
                icon("+")
                item("File", id = "entry.createInside.file") {
                    icon("FI")
                    onClick { window.contextMenuCreateFile(file.id) }
                }
                item("Directory", id = "entry.createInside.dir") {
                    icon("FD")
                    onClick { window.contextMenuCreateFolder(file.id) }
                }
            }
            separator("entry.sep.open")
        }

        item("Duplicate", id = "entry.duplicate") {
            icon("CP")
            onClick { window.contextMenuDuplicateFile(file) }
        }

        item("Rename", id = "entry.rename") {
            icon("RN")
            enabledIf { !file.locked }
            onClick { window.contextMenuBeginRename(file) }
        }

        item("Delete", id = "entry.delete") {
            icon("DL")
            enabledIf { !file.locked }
            onClick { window.contextMenuDeleteFile(file) }
        }

        separator("entry.sep.copy")

        item("Copy", id = "entry.copy") {
            icon("CY")
            onClick { window.contextMenuCopyFile(file) }
        }
    }
