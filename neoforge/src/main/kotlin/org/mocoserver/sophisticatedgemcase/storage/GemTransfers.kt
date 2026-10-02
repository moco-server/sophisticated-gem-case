package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.world.item.ItemStack
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** serverの保存を変更する入口。余りを新しいstackで返し、入力を変更しない。 */
internal object GemTransfers {
    fun insertDedicated(
        wrapper: GemCaseUpgradeWrapper,
        stack: ItemStack,
        limit: Int,
        simulate: Boolean,
    ): ItemStack {
        val key = StandardGem.keyOf(stack)
        val state = wrapper.snapshot() as? GemStorageState.Ready
        return if (key != null && state != null && limit > 0) {
            val change = state.counts.insert(key, minOf(stack.count, limit))
            val accepted = change.moved > 0 && GemStorageCodec.canWrite(change.state)
            if (accepted && (simulate || wrapper.replace(state.counts, change.state))) {
                stack.copyWithCount(stack.count - change.moved)
            } else {
                stack
            }
        } else {
            stack
        }
    }

    fun moveFromNormal(storage: IStorageWrapper): Int {
        val wrapper = GemCaseUpgradeItem.find(storage)
        val before = wrapper?.snapshot() as? GemStorageState.Ready
        return if (wrapper != null && before != null) move(storage, wrapper, before) else 0
    }

    private fun move(
        storage: IStorageWrapper,
        wrapper: GemCaseUpgradeWrapper,
        before: GemStorageState.Ready,
    ): Int {
        val inventory = storage.inventoryHandler
        val changes = linkedMapOf<Int, Pair<ItemStack, ItemStack>>()
        var counts = before.counts
        var moved = 0
        for (slot in 0 until inventory.slots) {
            val source = inventory.getStackInSlot(slot).copy()
            val key = StandardGem.keyOf(source)
            if (key != null) {
                val extractable = inventory.extractItem(slot, source.count, true)
                val amount = if (ItemStack.isSameItemSameComponents(source, extractable)) extractable.count else 0
                val change = counts.insert(key, amount)
                if (change.moved > 0 && GemStorageCodec.canWrite(change.state)) {
                    changes[slot] = source to source.copyWithCount(source.count - change.moved)
                    counts = change.state
                    moved += change.moved
                }
            }
        }
        val currentMatches =
            changes.all { (slot, pair) ->
                ItemStack.matches(inventory.getStackInSlot(slot), pair.first)
            }
        return if (moved > 0 && currentMatches && wrapper.snapshot() == before) {
            changes.forEach { (slot, pair) -> inventory.setStackInSlot(slot, pair.second) }
            if (wrapper.replace(before.counts, counts)) {
                moved
            } else {
                changes.forEach { (slot, pair) -> inventory.setStackInSlot(slot, pair.first) }
                0
            }
        } else {
            0
        }
    }
}
