package com.progallery.app.data.tree

import com.progallery.app.data.model.MediaItem

/**
 * One line in the file tree.
 *
 * A folder carries what is **under** it as well as what is in it, because that is the question a
 * file manager is asked — "how big is this folder" means the whole of it, not the part that happens
 * to sit directly inside.
 */
sealed interface TreeNode {

    val name: String

    /** The path relative to the root it was built from, with `/` separators. */
    val path: String

    /** How many files are at or under this node. */
    val itemCount: Int

    /** How many bytes are at or under this node. */
    val sizeBytes: Long

    data class Folder(
        override val name: String,
        override val path: String,
        override val itemCount: Int,
        override val sizeBytes: Long,
        /** Folders and files directly inside, already ordered. */
        val children: List<TreeNode>,
    ) : TreeNode

    data class File(
        override val name: String,
        override val path: String,
        val item: MediaItem,
    ) : TreeNode {
        override val itemCount: Int get() = 1
        override val sizeBytes: Long get() = item.sizeBytes
    }
}

/** A node and how deep it sits, for a list that is drawn flat and indented. */
data class TreeRow(val node: TreeNode, val depth: Int)

/**
 * Builds the folder tree from the files the app can actually see.
 *
 * **Built from the items, not from the filesystem.** That is the honest input: the app's own index
 * and the system's media list are what it was allowed to read, and walking the filesystem for a
 * tree would show folders it cannot open — as empty ones, which is a lie about what is in them. A
 * folder that exists here has files in it, and a folder the app was refused is reported separately,
 * by the screen, from the policy that knows about the refusal.
 *
 * **A path with no `/` is at the root.** `relativePath` is `DCIM/Camera/IMG_001.jpg`, so the tree
 * is `DCIM` → `Camera` → the file. A path the platform did not give (`null`) goes to the root
 * rather than into a made-up folder.
 *
 * Pure, so the whole shape is unit tested rather than eyeballed on a device.
 */
object FileTree {

    /**
     * Every row of the tree, depth-first, folders before files at each level.
     *
     * Flat rather than nested for drawing: a file manager's list is one long indented column, and a
     * nested structure would be flattened by the UI anyway — with the ordering decided there instead
     * of here, where it can be tested.
     */
    fun rows(items: List<MediaItem>, separator: Char = '/'): List<TreeRow> =
        flatten(build(items, separator), depth = 0)

    /**
     * The tree itself, for a caller that wants the structure rather than the rows.
     */
    fun build(items: List<MediaItem>, separator: Char = '/'): List<TreeNode> {
        val root = MutableFolder(name = "", path = "")
        items.forEach { item ->
            val segments = segmentsOf(item, separator)
            var current = root
            // Every segment but the last is a folder; the last is the file itself.
            segments.dropLast(1).forEach { segment ->
                current = current.folder(segment)
            }
            val fileName = segments.lastOrNull() ?: item.displayName
            current.files += item to fileName
        }
        return root.sortedChildren()
    }

    /**
     * The path segments of an item, with a name always present.
     *
     * A `relativePath` of `null` or blank means the platform did not say where it is — the file goes
     * at the root rather than into a folder named "null". An empty segment (`a//b`) is dropped rather
     * than becoming a folder with no name.
     */
    private fun segmentsOf(item: MediaItem, separator: Char): List<String> {
        val path = item.relativePath?.takeIf { it.isNotBlank() } ?: return listOf(item.displayName)
        val parts = path.split(separator).filter { it.isNotBlank() }
        return if (parts.isEmpty()) listOf(item.displayName) else parts
    }

    private fun flatten(nodes: List<TreeNode>, depth: Int): List<TreeRow> = buildList {
        nodes.forEach { node ->
            add(TreeRow(node, depth))
            if (node is TreeNode.Folder) addAll(flatten(node.children, depth + 1))
        }
    }

    /** A folder while it is being built, so the totals can be worked out once at the end. */
    private class MutableFolder(val name: String, val path: String) {
        val folders = LinkedHashMap<String, MutableFolder>()
        val files = mutableListOf<Pair<MediaItem, String>>()

        fun folder(segment: String): MutableFolder =
            folders.getOrPut(segment) {
                MutableFolder(name = segment, path = if (path.isEmpty()) segment else "$path/$segment")
            }

        /** Folders first, then files, each alphabetically and case-insensitively. */
        fun sortedChildren(): List<TreeNode> {
            val childFolders = folders.values
                .sortedBy { it.name.lowercase() }
                .map { it.toNode() }
            val childFiles = files
                .sortedBy { (_, name) -> name.lowercase() }
                .map { (item, name) ->
                    TreeNode.File(
                        name = name,
                        path = if (path.isEmpty()) name else "$path/$name",
                        item = item,
                    )
                }
            return childFolders + childFiles
        }

        fun toNode(): TreeNode.Folder {
            val children = sortedChildren()
            return TreeNode.Folder(
                name = name,
                path = path,
                // Rolled up, because "how big is this folder" means all of it.
                itemCount = children.sumOf { it.itemCount },
                sizeBytes = children.sumOf { it.sizeBytes },
                children = children,
            )
        }
    }
}
