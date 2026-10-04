package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.world.item.ItemStack
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import org.mocoserver.sophisticatedgemcase.integration.GemOperationContext
import org.mocoserver.sophisticatedgemcase.integration.GemTransferPermit
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** serverの保存を変更する入口。余りを新しいstackで返し、入力を変更しない。 */
internal object GemTransfers {
    fun insertDedicated(
        wrapper: GemCaseUpgradeWrapper,
        stack: ItemStack,
        limit: Int,
        simulate: Boolean,
        permit: GemTransferPermit? = null,
    ): ItemStack {
        if (!wrapper.mayMutate(permit)) return stack
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
        if (Thread.currentThread().threadGroup != net.neoforged.fml.util.thread.SidedThreadGroups.SERVER ||
            !GemOperationContext.mayMutate(storage, null)
        ) {
            return 0
        }
        val wrapper = GemCaseUpgradeItem.find(storage)
        val before = wrapper?.snapshot() as? GemStorageState.Ready
        return if (wrapper != null && before != null) {
            GemOperationContext.exclusive(storage) { permit -> move(storage, wrapper, before, permit) } ?: 0
        } else {
            0
        }
    }

    private fun move(
        storage: IStorageWrapper,
        wrapper: GemCaseUpgradeWrapper,
        before: GemStorageState.Ready,
        permit: GemTransferPermit,
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
        val plan = GemBulkMovePlan(changes, before, counts, moved)
        return commitMove(storage, wrapper, plan, permit)
    }

    @Suppress("TooGenericExceptionCaught") // native保存callback失敗では全通常slotを戻して元の例外を伝える。
    private fun commitMove(
        storage: IStorageWrapper,
        wrapper: GemCaseUpgradeWrapper,
        plan: GemBulkMovePlan,
        permit: GemTransferPermit,
    ): Int {
        val inventory = storage.inventoryHandler
        val currentMatches =
            plan.changes.all { (slot, pair) ->
                ItemStack.matches(inventory.getStackInSlot(slot), pair.first)
            }
        if (plan.moved <= 0 || !currentMatches || wrapper.snapshot() != plan.before) return 0
        return try {
            plan.changes.forEach { (slot, pair) -> inventory.setStackInSlot(slot, pair.second) }
            if (wrapper.replace(plan.before.counts, plan.updatedCounts, permit)) {
                plan.moved
            } else {
                restoreMove(inventory, plan.changes, null)
                0
            }
        } catch (failure: RuntimeException) {
            restoreMove(inventory, plan.changes, failure)
            throw failure
        }
    }

    @Suppress("TooGenericExceptionCaught") // 一つの復元callbackが失敗しても残りslotを復元し、失敗を集約する。
    private fun restoreMove(
        inventory: net.neoforged.neoforge.items.IItemHandlerModifiable,
        changes: Map<Int, Pair<ItemStack, ItemStack>>,
        failure: RuntimeException?,
    ) {
        var primary = failure
        for ((slot, pair) in changes.entries.toList().asReversed()) {
            try {
                inventory.setStackInSlot(slot, pair.first)
            } catch (recovery: RuntimeException) {
                val previous = primary
                if (previous == null) primary = recovery else previous.addSuppressed(recovery)
            }
        }
        if (failure == null) primary?.let { throw it }
    }
}

private data class GemBulkMovePlan(
    val changes: Map<Int, Pair<ItemStack, ItemStack>>,
    val before: GemStorageState.Ready,
    val updatedCounts: org.mocoserver.sophisticatedgemcase.core.GemCounts,
    val moved: Int,
)
