package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

/** copyやデコード前に、深さ・走査ノード数・保守的なサイズ上界で処理量を制限する。 */
internal object GemNbtBudget {
    private const val MAX_DEPTH = 8
    private const val MAX_NODES = GemStorageCodec.MAX_ENTRIES * 5 + 32
    private const val COMPOUND_END_BYTES = 1L
    private const val ENTRY_HEADER_BYTES = 3L
    private const val MAX_UTF_CHAR_BYTES = 3L
    private const val LIST_HEADER_BYTES = 5L

    fun check(root: Tag) {
        val pending = ArrayDeque<Pair<Tag, Int>>()
        pending.add(root to 1)
        var nodes = 0
        var bytes = 0L
        while (pending.isNotEmpty()) {
            val (tag, depth) = pending.removeLast()
            nodes++
            verify(depth <= MAX_DEPTH && nodes <= MAX_NODES)
            bytes += visit(tag, depth, pending)
            verify(bytes <= GemStorageCodec.MAX_BYTES)
        }
    }

    private fun visit(
        tag: Tag,
        depth: Int,
        pending: ArrayDeque<Pair<Tag, Int>>,
    ): Long =
        when (tag) {
            is CompoundTag -> compound(tag, depth, pending)
            is ListTag -> {
                verify(tag.size <= GemStorageCodec.MAX_ENTRIES)
                tag.forEach { enqueue(it, depth, pending) }
                LIST_HEADER_BYTES
            }
            else -> tag.sizeInBytes().toLong().also { verify(it >= 0) }
        }

    private fun compound(
        tag: CompoundTag,
        depth: Int,
        pending: ArrayDeque<Pair<Tag, Int>>,
    ): Long {
        verify(tag.size() <= MAX_NODES)
        var bytes = COMPOUND_END_BYTES
        for (key in tag.allKeys) {
            verify(key.length <= GemStorageCodec.MAX_BYTES)
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
        verify(pending.size < MAX_NODES)
        pending.add(tag to depth + 1)
    }

    private fun verify(condition: Boolean) {
        if (!condition) throw InvalidGemStorage("size")
    }
}
