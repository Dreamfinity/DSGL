
package org.dreamfinity.dsgl.mcForge1710.demo.sections

import org.dreamfinity.dsgl.core.contextmenu.ContextMenuPortalServices
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuStyle
import org.dreamfinity.dsgl.core.contextmenu.contextMenu
import org.dreamfinity.dsgl.core.dnd.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.dom.onContextMenu
import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.style.AlignItems
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.JustifyContent
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_MUTED
import java.util.ArrayDeque

private const val TILE_WIDTH = 86
private const val TILE_ICON_SIZE = 30
private const val TILE_GHOST_SIZE = 30
private const val ICON_FOLDER = "file://demo/folder.png"
private const val ICON_DOCUMENT = "file://demo/document.png"
private const val CONTEXT_MENU_ROOT_ID = "fs.root"
private const val CONTEXT_MENU_DOUBLE_CLICK_MS = 320L

private data class ContextMenuDemoFile(
    val id: String,
    val parentId: String?,
    val name: String,
    val sizeKb: Int,
    val isDirectory: Boolean,
    val locked: Boolean,
    val updatedAtOrder: Long,
)

private data class ContextMenuBreadcrumb(
    val id: String,
    val label: String,
)

private data class ContextMenuSectionState(
    val lastAction: String = "none",
    val lastTarget: String = "none",
    val actionCount: Int = 0,
    val openAnchored: Boolean = false,
    val showCursorDebug: Boolean = false,
    val clipboardHasData: Boolean = false,
    val clipboardEntryName: String = "clipboard.txt",
    val clipboardEntryId: String? = null,
    val sortMode: String = "Name",
    val cursorX: Int = -1,
    val cursorY: Int = -1,
    val cursorOwner: String = "none",
    val cursorLocalX: Int = 0,
    val cursorLocalY: Int = 0,
    val pinned: Boolean = false,
    val fileSelection: String = "README.md",
    val currentDirectoryId: String = CONTEXT_MENU_ROOT_ID,
    val backHistory: List<String> = emptyList(),
    val forwardHistory: List<String> = emptyList(),
    val renameTargetId: String? = null,
    val renameDraft: String = "",
    val dragHoverDirectoryId: String? = null,
    val files: List<ContextMenuDemoFile> = defaultContextMenuFiles(),
    val fileSequence: Long = 100L,
    val lastClickEntryId: String? = null,
    val lastClickMs: Long = 0L,
)

fun UiScope.contextMenuSection(onInfo: (String) -> Unit) {
    var state by useState(ContextMenuSectionState())

    fun recordContextMenuAction(target: String, action: String) {
        state =
            state.copy(
                lastTarget = target,
                lastAction = action,
                actionCount = state.actionCount + 1,
            )
        onInfo("Context menu [$target]: $action")
    }

    fun recordContextMenuCursor(
        owner: String,
        mouseX: Int,
        mouseY: Int,
        localX: Int,
        localY: Int,
    ) {
        state =
            state.copy(
                cursorOwner = owner,
                cursorX = mouseX,
                cursorY = mouseY,
                cursorLocalX = localX,
                cursorLocalY = localY,
            )
    }

    fun contextMenuEntryById(entryId: String?): ContextMenuDemoFile? {
        if (entryId == null) return null
        return state.files.firstOrNull { it.id == entryId }
    }

    fun contextMenuCurrentPath(): String {
        val byId = state.files.associateBy { it.id }
        val path = ArrayDeque<String>()
        var currentId: String? = state.currentDirectoryId
        while (currentId != null) {
            val entry = byId[currentId] ?: break
            if (entry.id != CONTEXT_MENU_ROOT_ID) {
                path.addFirst(entry.name)
            }
            currentId = entry.parentId
        }
        return if (path.isEmpty()) "/" else "/" + path.joinToString("/")
    }

    fun contextMenuBreadcrumbs(): List<ContextMenuBreadcrumb> {
        val byId = state.files.associateBy { it.id }
        val breadcrumbs = ArrayDeque<ContextMenuBreadcrumb>()
        var currentId: String? = state.currentDirectoryId
        while (currentId != null) {
            val entry = byId[currentId] ?: break
            val label = if (entry.id == CONTEXT_MENU_ROOT_ID) "Workspace" else entry.name
            breadcrumbs.addFirst(ContextMenuBreadcrumb(entry.id, label))
            currentId = entry.parentId
        }
        return breadcrumbs.toList()
    }

    fun contextMenuCanGoBack(): Boolean = state.backHistory.isNotEmpty()

    fun contextMenuCanGoForward(): Boolean = state.forwardHistory.isNotEmpty()

    fun contextMenuCanGoUp(): Boolean {
        val current = contextMenuEntryById(state.currentDirectoryId) ?: return false
        return current.parentId != null
    }

    fun contextMenuOpenDirectory(directoryId: String, pushHistory: Boolean) {
        val currentState = state
        val directory = currentState.files.firstOrNull { it.id == directoryId && it.isDirectory } ?: return
        var backHistory = currentState.backHistory
        var forwardHistory = currentState.forwardHistory
        if (pushHistory && directory.id != currentState.currentDirectoryId) {
            backHistory = backHistory + currentState.currentDirectoryId
            forwardHistory = emptyList()
        }
        state =
            currentState.copy(
                currentDirectoryId = directory.id,
                backHistory = backHistory,
                forwardHistory = forwardHistory,
                renameTargetId = null,
                dragHoverDirectoryId = null,
            )
        recordContextMenuAction("navigator", "open ${contextMenuCurrentPath()}")
    }

    fun contextMenuNavigateBack() {
        if (!contextMenuCanGoBack()) return
        val previous = state.backHistory.last()
        state =
            state.copy(
                backHistory = state.backHistory.dropLast(1),
                forwardHistory = listOf(state.currentDirectoryId) + state.forwardHistory,
                currentDirectoryId = previous,
                renameTargetId = null,
                dragHoverDirectoryId = null,
            )
        recordContextMenuAction("navigator", "back to ${contextMenuCurrentPath()}")
    }

    fun contextMenuNavigateForward() {
        val target = state.forwardHistory.firstOrNull() ?: return
        state =
            state.copy(
                forwardHistory = state.forwardHistory.drop(1),
                backHistory = state.backHistory + state.currentDirectoryId,
                currentDirectoryId = target,
                renameTargetId = null,
                dragHoverDirectoryId = null,
            )
        recordContextMenuAction("navigator", "forward to ${contextMenuCurrentPath()}")
    }

    fun contextMenuNavigateUp() {
        val current = contextMenuEntryById(state.currentDirectoryId) ?: return
        val parentId = current.parentId ?: return
        contextMenuOpenDirectory(parentId, pushHistory = true)
    }

    fun contextMenuSetSortMode(mode: String) {
        state = state.copy(sortMode = mode)
        recordContextMenuAction("background", "sort by ${mode.lowercase()}")
    }

    fun contextMenuVisibleFiles(): List<ContextMenuDemoFile> =
        sortedChildrenForDirectory(
            files = state.files,
            directoryId = state.currentDirectoryId,
            sortMode = state.sortMode,
        )

    fun contextMenuHandleEntryClick(file: ContextMenuDemoFile) {
        val now = System.currentTimeMillis()
        val isDoubleClick =
            state.lastClickEntryId == file.id && (now - state.lastClickMs) <= CONTEXT_MENU_DOUBLE_CLICK_MS
        state =
            state.copy(
                fileSelection = file.name,
                lastClickEntryId = file.id,
                lastClickMs = now,
            )
        if (file.isDirectory && isDoubleClick) {
            contextMenuOpenDirectory(file.id, pushHistory = true)
        }
    }

    fun contextMenuCreateFolder(parentId: String = state.currentDirectoryId) {
        val currentState = state
        val parent = currentState.files.firstOrNull { it.id == parentId && it.isDirectory } ?: return
        var sequence = currentState.fileSequence
        sequence += 1L
        val created =
            ContextMenuDemoFile(
                id = "fs.$sequence",
                parentId = parent.id,
                name = uniqueContextMenuName(currentState.files, parent.id, "New Folder"),
                sizeKb = 0,
                isDirectory = true,
                locked = false,
                updatedAtOrder = sequence,
            )
        state =
            currentState.copy(
                files = currentState.files + created,
                fileSelection = created.name,
                fileSequence = sequence,
            )
        recordContextMenuAction("background", "new folder ${created.name}")
    }

    fun contextMenuCreateFile(parentId: String = state.currentDirectoryId) {
        val currentState = state
        val parent = currentState.files.firstOrNull { it.id == parentId && it.isDirectory } ?: return
        var updatedState = currentState
        if (parent.id != currentState.currentDirectoryId) {
            val backHistory = currentState.backHistory + currentState.currentDirectoryId
            updatedState =
                currentState.copy(
                    currentDirectoryId = parent.id,
                    backHistory = backHistory,
                    forwardHistory = emptyList(),
                    renameTargetId = null,
                    dragHoverDirectoryId = null,
                )
        }

        var sequence = updatedState.fileSequence
        sequence += 1L
        val name = uniqueContextMenuName(updatedState.files, parent.id, "new-file.txt")
        val created =
            ContextMenuDemoFile(
                id = "fs.$sequence",
                parentId = parent.id,
                name = name,
                sizeKb = 1,
                isDirectory = false,
                locked = false,
                updatedAtOrder = sequence,
            )
        state =
            updatedState.copy(
                files = updatedState.files + created,
                fileSelection = created.name,
                renameTargetId = created.id,
                renameDraft = created.name,
                fileSequence = sequence,
            )
        recordContextMenuAction("background", "new file $name")
    }

    fun contextMenuOpenFile(file: ContextMenuDemoFile) {
        if (file.isDirectory) {
            contextMenuOpenDirectory(file.id, pushHistory = true)
            return
        }
        state = state.copy(fileSelection = file.name)
        recordContextMenuAction(file.name, "open")
    }

    fun contextMenuBeginRename(file: ContextMenuDemoFile) {
        if (file.locked) return
        state =
            state.copy(
                renameTargetId = file.id,
                renameDraft = file.name,
                fileSelection = file.name,
            )
    }

    fun contextMenuCancelRename() {
        state = state.copy(renameTargetId = null, renameDraft = "")
    }

    fun contextMenuApplyRename() {
        val targetId = state.renameTargetId ?: return
        val target =
            contextMenuEntryById(targetId) ?: run {
                state = state.copy(renameTargetId = null)
                return
            }
        if (target.locked) {
            state = state.copy(renameTargetId = null)
            return
        }
        val draft = state.renameDraft.trim()
        if (draft.isEmpty()) return

        val resolved =
            if (draft == target.name) {
                draft
            } else {
                uniqueContextMenuName(state.files, target.parentId ?: CONTEXT_MENU_ROOT_ID, draft)
            }

        val sequence = state.fileSequence + 1L
        state =
            state.copy(
                files =
                    state.files.map { current ->
                        if (current.id == target.id) {
                            current.copy(name = resolved, updatedAtOrder = sequence)
                        } else {
                            current
                        }
                    },
                fileSelection = resolved,
                renameTargetId = null,
                renameDraft = "",
                fileSequence = sequence,
            )
        recordContextMenuAction(target.name, "rename to $resolved")
    }

    fun contextMenuCopyFile(file: ContextMenuDemoFile) {
        state =
            state.copy(
                clipboardHasData = true,
                clipboardEntryName = file.name,
                clipboardEntryId = file.id,
                fileSelection = file.name,
            )
        recordContextMenuAction(file.name, "copied to clipboard")
    }

    fun contextMenuDuplicateFile(
        file: ContextMenuDemoFile,
        targetParentId: String = file.parentId ?: state.currentDirectoryId,
    ) {
        val currentState = state
        val targetParent = currentState.files.firstOrNull { it.id == targetParentId && it.isDirectory } ?: return
        val byId = currentState.files.associateBy { it.id }
        val descendants = collectSubtree(file.id, byId)
        if (descendants.isEmpty()) return

        val idRemap = linkedMapOf<String, String>()
        var sequence = currentState.fileSequence

        fun nextId(): String {
            sequence += 1L
            return "fs.$sequence"
        }

        fun nextOrder(): Long {
            sequence += 1L
            return sequence
        }

        val rootCopyId = nextId()
        idRemap[file.id] = rootCopyId
        val rootName = uniqueContextMenuName(currentState.files, targetParent.id, file.name, "copy")
        val copies = ArrayList<ContextMenuDemoFile>(descendants.size)
        copies +=
            file.copy(
                id = rootCopyId,
                parentId = targetParent.id,
                name = rootName,
                locked = false,
                updatedAtOrder = nextOrder(),
            )
        descendants.drop(1).forEach { child ->
            val parentCopyId = idRemap[child.parentId] ?: return@forEach
            val childCopyId = nextId()
            idRemap[child.id] = childCopyId
            copies +=
                child.copy(
                    id = childCopyId,
                    parentId = parentCopyId,
                    locked = false,
                    updatedAtOrder = nextOrder(),
                )
        }

        state =
            currentState.copy(
                files = currentState.files + copies,
                fileSelection = rootName,
                fileSequence = sequence,
            )
        recordContextMenuAction(file.name, "duplicate as $rootName")
    }

    fun contextMenuCanDropIntoDirectory(entryId: String, destinationDirectoryId: String): Boolean {
        val entry = contextMenuEntryById(entryId) ?: return false
        val destination = contextMenuEntryById(destinationDirectoryId) ?: return false
        if (!destination.isDirectory) return false
        if (entry.id == destination.id) return false
        if (entry.parentId == destination.id) return false
        if (isDescendantDirectory(state.files, ancestorId = entry.id, candidateId = destination.id)) return false
        return true
    }

    fun contextMenuMoveFile(file: ContextMenuDemoFile, destinationDirectoryId: String) {
        if (!contextMenuCanDropIntoDirectory(file.id, destinationDirectoryId)) return
        val destination = contextMenuEntryById(destinationDirectoryId) ?: return
        val resolvedName = uniqueContextMenuName(state.files, destination.id, file.name)
        val sequence = state.fileSequence + 1L
        state =
            state.copy(
                files =
                    state.files.map { current ->
                        if (current.id == file.id) {
                            current.copy(
                                parentId = destination.id,
                                name = resolvedName,
                                updatedAtOrder = sequence,
                            )
                        } else {
                            current
                        }
                    },
                fileSelection = resolvedName,
                dragHoverDirectoryId = null,
                fileSequence = sequence,
            )
        recordContextMenuAction(file.name, "move to ${destination.name}")
    }

    fun contextMenuDeleteFile(file: ContextMenuDemoFile) {
        if (file.locked) return
        val subtreeIds = collectSubtreeIds(state.files, file.id)
        var nextCurrentDirectory = state.currentDirectoryId
        var nextBackHistory = state.backHistory
        var nextForwardHistory = state.forwardHistory
        if (nextCurrentDirectory == file.id || nextCurrentDirectory in subtreeIds) {
            nextCurrentDirectory = CONTEXT_MENU_ROOT_ID
            nextBackHistory = emptyList()
            nextForwardHistory = emptyList()
        } else {
            nextBackHistory = nextBackHistory.filterNot { subtreeIds.contains(it) }
            nextForwardHistory = nextForwardHistory.filterNot { subtreeIds.contains(it) }
        }

        var nextClipboardHasData = state.clipboardHasData
        var nextClipboardEntryId = state.clipboardEntryId
        if (nextClipboardEntryId in subtreeIds) {
            nextClipboardHasData = false
            nextClipboardEntryId = null
        }

        val nextFiles = state.files.filterNot { subtreeIds.contains(it.id) }
        val nextRenameTarget = state.renameTargetId?.takeUnless { subtreeIds.contains(it) }
        var nextSelection = state.fileSelection
        if (nextSelection != "none" &&
            sortedChildrenForDirectory(
                files = nextFiles,
                directoryId = nextCurrentDirectory,
                sortMode = state.sortMode,
            ).none { it.name == nextSelection }
        ) {
            nextSelection = sortedChildrenForDirectory(
                files = nextFiles,
                directoryId = nextCurrentDirectory,
                sortMode = state.sortMode,
            ).firstOrNull()?.name ?: "none"
        }

        state =
            state.copy(
                files = nextFiles,
                currentDirectoryId = nextCurrentDirectory,
                backHistory = nextBackHistory,
                forwardHistory = nextForwardHistory,
                clipboardHasData = nextClipboardHasData,
                clipboardEntryId = nextClipboardEntryId,
                renameTargetId = nextRenameTarget,
                fileSelection = nextSelection,
            )
        recordContextMenuAction(file.name, "delete")
    }

    fun contextMenuPasteIntoWorkspace() {
        if (!state.clipboardHasData) return
        val sourceId = state.clipboardEntryId ?: return
        val source = contextMenuEntryById(sourceId) ?: return
        contextMenuDuplicateFile(source, targetParentId = state.currentDirectoryId)
        recordContextMenuAction("background", "paste ${source.name}")
    }

    fun contextMenuRefreshWorkspace() {
        state =
            state.copy(
                files =
                    state.files.map { file ->
                        file.copy(updatedAtOrder = file.updatedAtOrder + 1L)
                    },
            )
        recordContextMenuAction("background", "refresh")
    }

    fun buildBackgroundMenu() =
        contextMenu(id = "demo.context.background") {
            submenu("Create", id = "create") {
                icon("+")
                item("File", id = "create.file") {
                    icon("FI")
                    onClick { contextMenuCreateFile() }
                }
                item("Directory", id = "create.dir") {
                    icon("FD")
                    onClick { contextMenuCreateFolder() }
                }
            }

            item("Paste", id = "paste") {
                icon("CL")
                hint("Ctrl+V")
                enabledIf { state.clipboardHasData }
                onClick { contextMenuPasteIntoWorkspace() }
            }

            submenu("Sort by", id = "sort") {
                icon("AZ")
                item("Name", id = "sort.name") {
                    checkedIf { state.sortMode == "Name" }
                    onClick { contextMenuSetSortMode("Name") }
                }
                item("Date", id = "sort.date") {
                    checkedIf { state.sortMode == "Date" }
                    onClick { contextMenuSetSortMode("Date") }
                }
                item("Size", id = "sort.size") {
                    checkedIf { state.sortMode == "Size" }
                    onClick { contextMenuSetSortMode("Size") }
                }
            }

            separator("main.sep")

            item("Refresh", id = "refresh") {
                icon("RF")
                onClick { contextMenuRefreshWorkspace() }
            }
        }

    fun buildEntryMenu(file: ContextMenuDemoFile) =
        contextMenu(id = "demo.context.entry.${file.id}") {
            if (file.isDirectory) {
                item("Open", id = "entry.open") {
                    icon("OP")
                    onClick { contextMenuOpenDirectory(file.id, pushHistory = true) }
                }
                submenu("Create Inside", id = "entry.createInside") {
                    icon("+")
                    item("File", id = "entry.createInside.file") {
                        icon("FI")
                        onClick { contextMenuCreateFile(file.id) }
                    }
                    item("Directory", id = "entry.createInside.dir") {
                        icon("FD")
                        onClick { contextMenuCreateFolder(file.id) }
                    }
                }
                separator("entry.sep.open")
            }

            item("Duplicate", id = "entry.duplicate") {
                icon("CP")
                onClick { contextMenuDuplicateFile(file) }
            }

            item("Rename", id = "entry.rename") {
                icon("RN")
                enabledIf { !file.locked }
                onClick { contextMenuBeginRename(file) }
            }

            item("Delete", id = "entry.delete") {
                icon("DL")
                enabledIf { !file.locked }
                onClick { contextMenuDeleteFile(file) }
            }

            separator("entry.sep.copy")

            item("Copy", id = "entry.copy") {
                icon("CY")
                onClick { contextMenuCopyFile(file) }
            }
        }

    fun UiScope.contextMenuEntryTile(file: ContextMenuDemoFile) {
        val tileKey = "context.fs.tile.${file.id}"
        val iconURL = iconFor(file)
        val draggable =
            useDraggable(
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
                },
            )
        val droppable =
            if (file.isDirectory) {
                useDroppable(
                    id = "context.fs.dir.${file.id}",
                    nodeKey = tileKey,
                    accepts = { active ->
                        val activeId = active.id ?: return@useDroppable false
                        contextMenuCanDropIntoDirectory(activeId, file.id)
                    },
                    onDragEnter = { event, active ->
                        val activeId = active?.id
                        if (activeId != null && contextMenuCanDropIntoDirectory(activeId, file.id)) {
                            state = state.copy(dragHoverDirectoryId = file.id)
                            event.cancelled = true
                        }
                    },
                    onDragOver = { event, active ->
                        val activeId = active?.id
                        if (activeId != null && contextMenuCanDropIntoDirectory(activeId, file.id)) {
                            state = state.copy(dragHoverDirectoryId = file.id)
                            event.cancelled = true
                        }
                    },
                    onDragLeave = { event, _ ->
                        if (state.dragHoverDirectoryId == file.id) {
                            state = state.copy(dragHoverDirectoryId = null)
                        }
                        event.cancelled = true
                    },
                    onDrop = { event, active ->
                        val moving = contextMenuEntryById(active?.id) ?: return@useDroppable
                        contextMenuMoveFile(moving, file.id)
                        event.cancelled = true
                    },
                )
            } else {
                null
            }
        val isEditingName = state.renameTargetId == file.id
        val isSelected = state.fileSelection == file.name
        val isDropHover = state.dragHoverDirectoryId == file.id
        val tileNode =
            div({
                key = tileKey
                onMouseClick = { event ->
                    if (!isEditingName && event.mouseButton == MouseButton.LEFT) {
                        contextMenuHandleEntryClick(file)
                    }
                }
                style = {
                    width = TILE_WIDTH.px
                    minHeight = (TILE_WIDTH + 18).px
                    backgroundColor =
                        when {
                            isDropHover -> 0xFF43607A.toInt()
                            isSelected -> 0xFF3A5168.toInt()
                            else -> 0xFF33414E.toInt()
                        }
                    border {
                        width = 1.px
                        color = if (isDropHover) 0xFF9BC2E9.toInt() else 0xFF596B7D.toInt()
                    }
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                    alignItems = AlignItems.Center
                    justifyContent = JustifyContent.Center
                }
                applyDraggable(draggable)
                if (droppable != null) {
                    applyDroppable(droppable)
                }
            }) {
                img(iconURL, {
                    style = {
                        width = TILE_ICON_SIZE.px
                        height = TILE_ICON_SIZE.px
                    }
                })
                if (isEditingName) {
                    input(
                        InputType.Text(
                            value = state.renameDraft,
                            placeholder = "Name",
                        ),
                        {
                            key = "contextMenu.rename.inline.${file.id}"
                            onInput = { event ->
                                state = state.copy(renameDraft = event.value)
                            }
                            onKeyDown = { event ->
                                when (event.keyCode) {
                                    KeyCodes.ENTER -> contextMenuApplyRename()
                                    KeyCodes.ESCAPE -> contextMenuCancelRename()
                                }
                            }
                        },
                    )
                } else {
                    text(file.name, {
                        style = { color = if (file.locked) 0xFFE9A56E.toInt() else 0xFFEAF2FD.toInt() }
                    })
                }
            }
        tileNode.onContextMenu {
            val anchorX = anchorRect?.x ?: mouseX
            val anchorY = anchorRect?.y ?: mouseY
            recordContextMenuCursor(
                owner = "file:${file.id}",
                mouseX = mouseX,
                mouseY = mouseY,
                localX = mouseX - anchorX,
                localY = mouseY - anchorY,
            )
            openMenu(buildEntryMenu(file))
        }
    }

    val entries = contextMenuVisibleFiles()
    ContextMenuPortalServices.engine.setStyle(
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
            submenuArrowColor = 0xFFC9D7E6.toInt(),
        ),
    )

    div({
        key = "section.contextMenu"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Pseudo filesystem: tile view + context menu + drag/drop")
        text(
            "path=${contextMenuCurrentPath()} sort=${state.sortMode} selected=${state.fileSelection}",
            { style = { color = DEMO_MUTED } },
        )
        text(
            "lastAction=${state.lastAction} target=${state.lastTarget} actions=${state.actionCount}",
            { style = { color = DEMO_MUTED } },
        )

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("New File", {
                onMouseClick = { contextMenuCreateFile() }
            })
            button("New Folder", {
                onMouseClick = { contextMenuCreateFolder() }
            })
            button("Up", {
                onMouseClick = { contextMenuNavigateUp() }
                disabled = !contextMenuCanGoUp()
            })
        }

        div({
            key = "section.contextMenu.window"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Column
                flexGrow = 1f
                minHeight = 150.px
                padding = 3.px
                gap = 2.px
                backgroundColor = 0xFF2A313B.toInt()
                border {
                    width = 1.px
                    color = 0xFF5B6A7A.toInt()
                }
            }
        }) {
            div({
                key = "section.contextMenu.pathbar"
                style = {
                    padding = 2.px
                    gap = 2.px
                    backgroundColor = 0xFF25303A.toInt()
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                    border {
                        width = 1.px
                        color = 0xFF4F6175.toInt()
                    }
                }
            }) {
                button("<", {
                    key = "section.contextMenu.path.back"
                    onMouseClick = { contextMenuNavigateBack() }
                    disabled = !contextMenuCanGoBack()
                })
                button(">", {
                    key = "section.contextMenu.path.forward"
                    onMouseClick = { contextMenuNavigateForward() }
                    disabled = !contextMenuCanGoForward()
                })
                val breadcrumbs = contextMenuBreadcrumbs()
                breadcrumbs.forEachIndexed { index, breadcrumb ->
                    if (index > 0) {
                        text("/", { style = { color = DEMO_MUTED } })
                    }
                    val breadcrumbKey = "section.contextMenu.path.${breadcrumb.id}"
                    val breadcrumbDrop =
                        useDroppable(
                            id = "context.fs.path.${breadcrumb.id}",
                            nodeKey = breadcrumbKey,
                            accepts = { active ->
                                val activeId = active.id ?: return@useDroppable false
                                contextMenuCanDropIntoDirectory(activeId, breadcrumb.id)
                            },
                            onDragEnter = { event, active ->
                                if (event.target?.key != breadcrumbKey) return@useDroppable
                                val activeId = active?.id ?: return@useDroppable
                                if (contextMenuCanDropIntoDirectory(activeId, breadcrumb.id)) {
                                    state = state.copy(dragHoverDirectoryId = breadcrumb.id)
                                    event.cancelled = true
                                }
                            },
                            onDragOver = { event, active ->
                                if (event.target?.key != breadcrumbKey) return@useDroppable
                                val activeId = active?.id ?: return@useDroppable
                                if (contextMenuCanDropIntoDirectory(activeId, breadcrumb.id)) {
                                    state = state.copy(dragHoverDirectoryId = breadcrumb.id)
                                    event.cancelled = true
                                }
                            },
                            onDragLeave = { event, _ ->
                                if (event.target?.key != breadcrumbKey) return@useDroppable
                                if (state.dragHoverDirectoryId == breadcrumb.id) {
                                    state = state.copy(dragHoverDirectoryId = null)
                                }
                                event.cancelled = true
                            },
                            onDrop = { event, active ->
                                if (event.target?.key != breadcrumbKey) return@useDroppable
                                val moving = contextMenuEntryById(active?.id) ?: return@useDroppable
                                contextMenuMoveFile(moving, breadcrumb.id)
                                event.cancelled = true
                            },
                        )
                    val isCurrent = breadcrumb.id == state.currentDirectoryId
                    val isDropHover = state.dragHoverDirectoryId == breadcrumb.id
                    button(breadcrumb.label, {
                        key = breadcrumbKey
                        onMouseClick = {
                            contextMenuOpenDirectory(breadcrumb.id, pushHistory = true)
                        }
                        style = {
                            backgroundColor =
                                when {
                                    isDropHover -> 0xFF40617F.toInt()
                                    isCurrent -> 0xFF364A5E.toInt()
                                    else -> 0xFF2B3A4A.toInt()
                                }
                            border {
                                width = 1.px
                                color = if (isDropHover) 0xFF9BC2E9.toInt() else 0xFF5B6F84.toInt()
                            }
                        }
                        applyDroppable(breadcrumbDrop)
                    })
                }
            }

            val listDroppable =
                useDroppable(
                    id = "context.fs.current.${state.currentDirectoryId}",
                    nodeKey = "section.contextMenu.list",
                    accepts = { active ->
                        val activeId = active.id ?: return@useDroppable false
                        contextMenuCanDropIntoDirectory(activeId, state.currentDirectoryId)
                    },
                    onDragEnter = { event, active ->
                        if (event.target?.key != "section.contextMenu.list") return@useDroppable
                        val activeId = active?.id
                        if (activeId != null && contextMenuCanDropIntoDirectory(activeId, state.currentDirectoryId)) {
                            state = state.copy(dragHoverDirectoryId = state.currentDirectoryId)
                        }
                    },
                    onDragOver = { event, active ->
                        if (event.target?.key != "section.contextMenu.list") return@useDroppable
                        val activeId = active?.id
                        if (activeId != null && contextMenuCanDropIntoDirectory(activeId, state.currentDirectoryId)) {
                            state = state.copy(dragHoverDirectoryId = state.currentDirectoryId)
                        }
                    },
                    onDragLeave = { event, _ ->
                        if (event.target?.key != "section.contextMenu.list") return@useDroppable
                        if (state.dragHoverDirectoryId == state.currentDirectoryId) {
                            state = state.copy(dragHoverDirectoryId = null)
                        }
                    },
                    onDrop = { event, active ->
                        if (event.target?.key != "section.contextMenu.list") return@useDroppable
                        val moving = contextMenuEntryById(active?.id) ?: return@useDroppable
                        contextMenuMoveFile(moving, state.currentDirectoryId)
                    },
                )

            val listNode =
                div({
                    key = "section.contextMenu.list"
                    style = {
                        gap = 4.px
                        padding = 4.px
                        backgroundColor =
                            if (state.dragHoverDirectoryId ==
                                state.currentDirectoryId
                            ) {
                                0xFF2F4358.toInt()
                            } else {
                                0xFF2B343F.toInt()
                            }
                        border {
                            width = 1.px
                            color = 0xFF4F6175.toInt()
                        }
                        display = Display.Grid
                        gridColumns = 4
                        flexGrow = 1f
                    }
                    applyDroppable(listDroppable)
                }) {
                    if (entries.isEmpty()) {
                        div({ style = { padding = 2.px } }) {
                            text(
                                "Folder is empty. Right-click to create file/folder.",
                                { style = { color = DEMO_MUTED } },
                            )
                        }
                    } else {
                        entries.forEach { file ->
                            contextMenuEntryTile(file)
                        }
                    }
                }
            listNode.onContextMenu {
                val anchorX = anchorRect?.x ?: mouseX
                val anchorY = anchorRect?.y ?: mouseY
                recordContextMenuCursor(
                    owner = "background",
                    mouseX = mouseX,
                    mouseY = mouseY,
                    localX = mouseX - anchorX,
                    localY = mouseY - anchorY,
                )
                openMenu(buildBackgroundMenu())
            }
        }
    }
}

private fun iconFor(file: ContextMenuDemoFile): String = if (file.isDirectory) ICON_FOLDER else ICON_DOCUMENT

private fun uniqueContextMenuName(
    files: List<ContextMenuDemoFile>,
    parentId: String,
    baseName: String,
    variant: String = "new",
): String {
    val existing =
        files
            .asSequence()
            .filter { it.parentId == parentId }
            .map { it.name }
            .toHashSet()
    if (!existing.contains(baseName)) {
        return baseName
    }
    val (stem, extension) = splitContextMenuName(baseName)

    fun candidate(index: Int): String =
        when (variant) {
            "copy" -> if (index == 1) "$stem copy$extension" else "$stem copy $index$extension"
            "renamed" -> if (index == 1) "$stem (renamed)$extension" else "$stem (renamed $index)$extension"
            else -> "$stem $index$extension"
        }

    var index = 1
    var next = candidate(index)
    while (existing.contains(next)) {
        index += 1
        next = candidate(index)
    }
    return next
}

private fun splitContextMenuName(name: String): Pair<String, String> {
    val dotIndex = name.lastIndexOf('.')
    return if (dotIndex > 0 && dotIndex < name.length - 1) {
        name.substring(0, dotIndex) to name.substring(dotIndex)
    } else {
        name to ""
    }
}

private fun sortedChildrenForDirectory(
    files: List<ContextMenuDemoFile>,
    directoryId: String,
    sortMode: String,
): List<ContextMenuDemoFile> {
    val children = files.filter { it.parentId == directoryId }
    val comparator =
        when (sortMode) {
            "Date" -> compareByDescending<ContextMenuDemoFile> { it.updatedAtOrder }.thenBy { it.name.lowercase() }
            "Size" -> compareByDescending<ContextMenuDemoFile> { it.sizeKb }.thenBy { it.name.lowercase() }
            else -> compareBy<ContextMenuDemoFile> { it.name.lowercase() }
        }
    return children.sortedWith(compareBy<ContextMenuDemoFile> { !it.isDirectory }.then(comparator))
}

private fun collectSubtree(rootId: String, byId: Map<String, ContextMenuDemoFile>): List<ContextMenuDemoFile> {
    val root = byId[rootId] ?: return emptyList()
    val queue = ArrayDeque<String>()
    val orderedIds = ArrayList<String>()
    queue += root.id
    while (queue.isNotEmpty()) {
        val currentId = queue.removeFirst()
        orderedIds += currentId
        byId.values
            .filter { it.parentId == currentId }
            .sortedBy { it.name.lowercase() }
            .forEach { queue += it.id }
    }
    return orderedIds.mapNotNull { byId[it] }
}

private fun collectSubtreeIds(files: List<ContextMenuDemoFile>, rootId: String): Set<String> =
    collectSubtree(
        rootId,
        files.associateBy {
            it.id
        },
    ).map { it.id }.toSet()

private fun isDescendantDirectory(files: List<ContextMenuDemoFile>, ancestorId: String, candidateId: String): Boolean {
    if (ancestorId == candidateId) return true
    val byId = files.associateBy { it.id }
    var current = byId[candidateId]
    while (current != null) {
        if (current.parentId == ancestorId) return true
        current = current.parentId?.let { byId[it] }
    }
    return false
}

private fun defaultContextMenuFiles(): List<ContextMenuDemoFile> =
    listOf(
        ContextMenuDemoFile(CONTEXT_MENU_ROOT_ID, null, "Workspace", 0, true, true, 1L),
        ContextMenuDemoFile("fs.docs", CONTEXT_MENU_ROOT_ID, "Documents", 0, true, false, 2L),
        ContextMenuDemoFile("fs.downloads", CONTEXT_MENU_ROOT_ID, "Downloads", 0, true, false, 3L),
        ContextMenuDemoFile("fs.projects", CONTEXT_MENU_ROOT_ID, "Projects", 0, true, false, 4L),
        ContextMenuDemoFile("fs.readme", CONTEXT_MENU_ROOT_ID, "README.md", 4, false, true, 5L),
        ContextMenuDemoFile("fs.build", CONTEXT_MENU_ROOT_ID, "build.gradle.kts", 3, false, false, 6L),
        ContextMenuDemoFile("fs.mods", CONTEXT_MENU_ROOT_ID, "mods.toml", 1, false, false, 7L),
        ContextMenuDemoFile("fs.atlas", CONTEXT_MENU_ROOT_ID, "TexturesAtlas.kt", 19, false, false, 8L),
        ContextMenuDemoFile("fs.notes", CONTEXT_MENU_ROOT_ID, "notes.txt", 2, false, false, 9L),
        ContextMenuDemoFile("fs.roadmap", CONTEXT_MENU_ROOT_ID, "roadmap.md", 6, false, false, 10L),
        ContextMenuDemoFile("fs.docs.spec", "fs.docs", "spec.md", 5, false, false, 11L),
        ContextMenuDemoFile("fs.docs.todos", "fs.docs", "todos.txt", 2, false, false, 12L),
        ContextMenuDemoFile("fs.projects.clientA", "fs.projects", "Client A", 0, true, false, 13L),
        ContextMenuDemoFile("fs.projects.archive", "fs.projects", "Archive", 0, true, false, 14L),
        ContextMenuDemoFile("fs.archive.2025", "fs.projects.archive", "2025", 0, true, false, 15L),
        ContextMenuDemoFile("fs.archive.2026", "fs.projects.archive", "2026", 0, true, false, 16L),
        ContextMenuDemoFile("fs.archive.2026.q1", "fs.archive.2026", "Q1-report.md", 7, false, false, 17L),
        ContextMenuDemoFile("fs.downloads.asset", "fs.downloads", "texture-pack.zip", 24, false, false, 18L),
    )
