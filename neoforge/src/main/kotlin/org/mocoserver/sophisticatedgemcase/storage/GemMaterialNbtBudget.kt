package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

/** 材料component用に深さ16・1MiBとstack単位32KiBを独立して制限する。 */
internal object GemMaterialNbtBudget {
    const val MAX_DEPTH: Int = 16
    const val MAX_BYTES: Int = 1_048_576
    const val MAX_STACK_BYTES: Int = 32 * 1024
    private const val MAX_NODES: Int = 65_536
    private const val COMPOUND_END_BYTES = 1L
    private const val ENTRY_HEADER_BYTES = 3L
    private const val MAX_UTF_CHAR_BYTES = 3L
    private const val LIST_HEADER_BYTES = 5L

    fun check(root: Tag) {
        verify(estimatedSize(root) <= MAX_BYTES, "size")
    }

    fun checkStack(root: Tag) {
        verify(estimatedSize(root) <= MAX_STACK_BYTES, "stack_size")
    }

    private fun estimatedSize(root: Tag): Long {
        val pending = ArrayDeque<Pair<Tag, Int>>()
        pending.add(root to 1)
        var nodes = 0
        var bytes = 0L
        while (pending.isNotEmpty()) {
            val (tag, depth) = pending.removeLast()
            nodes++
            verify(depth <= MAX_DEPTH && nodes <= MAX_NODES, "depth")
            bytes += visit(tag, depth, pending)
            verify(bytes <= MAX_BYTES, "size")
        }
        return bytes
    }

    private fun visit(
        tag: Tag,
        depth: Int,
        pending: ArrayDeque<Pair<Tag, Int>>,
    ): Long =
        when (tag) {
            is CompoundTag -> compound(tag, depth, pending)
            is ListTag -> {
                verify(tag.size <= MAX_NODES, "size")
                tag.forEach { enqueue(it, depth, pending) }
                LIST_HEADER_BYTES
            }
            else -> tag.sizeInBytes().toLong().also { verify(it >= 0, "size") }
        }

    private fun compound(
        tag: CompoundTag,
        depth: Int,
        pending: ArrayDeque<Pair<Tag, Int>>,
    ): Long {
        verify(tag.size() <= MAX_NODES, "size")
        var bytes = COMPOUND_END_BYTES
        for (key in tag.allKeys) {
            verify(key.length <= MAX_BYTES, "size")
            bytes += ENTRY_HEADER_BYTES + key.length * MAX_UTF_CHAR_BYTES
            tag.get(key)?.let { enqueue(it, depth, pending) }
        }
        return bytes
    }

    private fun enqueue(
        tag: Tag,
        depth: Int,
        pending: ArrayDeque<Pair<Tag, Int>>,
    ) {
        verify(pending.size < MAX_NODES, "size")
        pending.add(tag to depth + 1)
    }

    private fun verify(
        condition: Boolean,
        reason: String,
    ) {
        if (!condition) throw InvalidGemMaterial(reason)
    }
}

internal class InvalidGemMaterial(
    reason: String,
) : IllegalArgumentException(reason)
