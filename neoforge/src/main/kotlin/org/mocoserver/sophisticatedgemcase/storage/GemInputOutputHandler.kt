package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.world.item.ItemStack
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemHandler
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem

/** slotや抽出を増やさず、公開handlerの投入制限と余りを維持する。 */
internal class GemInputOutputHandler(
    private val storage: IStorageWrapper,
    private val original: ITrackedContentsItemHandler,
) : ITrackedContentsItemHandler by original {
    override fun insertItem(
        slot: Int,
        stack: ItemStack,
        simulate: Boolean,
    ): ItemStack {
        if (!simulate && Thread.currentThread().threadGroup != SidedThreadGroups.SERVER) return stack
        val permitted = slot in 0 until original.slots && original.isItemValid(slot, stack)
        val wrapper = if (permitted) GemCaseUpgradeItem.find(storage) else null
        val remainder =
            if (wrapper !=
                null
            ) {
                GemTransfers.insertDedicated(wrapper, stack, stack.count, simulate)
            } else {
                stack
            }
        return if (remainder.isEmpty) ItemStack.EMPTY else original.insertItem(slot, remainder, simulate)
    }

    override fun insertItem(
        stack: ItemStack,
        simulate: Boolean,
    ): ItemStack {
        if (!simulate && Thread.currentThread().threadGroup != SidedThreadGroups.SERVER) return stack
        val permitted = (0 until original.slots).any { original.isItemValid(it, stack) }
        val wrapper = if (permitted) GemCaseUpgradeItem.find(storage) else null
        val remainder =
            if (wrapper !=
                null
            ) {
                GemTransfers.insertDedicated(wrapper, stack, stack.count, simulate)
            } else {
                stack
            }
        return if (remainder.isEmpty) ItemStack.EMPTY else original.insertItem(remainder, simulate)
    }

    override fun isInsertBlocked(): Boolean {
        val state = GemCaseUpgradeItem.find(storage)?.snapshot() as? GemStorageState.Ready
        val hasSpace =
            state != null &&
                (
                    state.counts.entries().size < GemStorageCodec.MAX_ENTRIES ||
                        state.counts
                            .entries()
                            .values
                            .any { it < GemCounts.CAPACITY }
                )
        return original.isInsertBlocked && !hasSpace
    }
}
