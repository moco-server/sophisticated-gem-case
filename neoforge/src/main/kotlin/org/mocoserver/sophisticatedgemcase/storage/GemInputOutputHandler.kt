package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.world.item.ItemStack
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemHandler
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.integration.GemOperationContext
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
    ): ItemStack =
        if (!simulate && Thread.currentThread().threadGroup != SidedThreadGroups.SERVER) {
            stack
        } else if (GemOperationContext.routingSuppressed(storage)) {
            original.insertItem(slot, stack, simulate)
        } else {
            val permitted = slot in 0 until original.slots && original.isItemValid(slot, stack)
            val wrapper =
                if (permitted) {
                    GemCaseUpgradeItem.find(storage)?.takeIf {
                        it.settingsSnapshot() is GemSettingsState.Ready
                    }
                } else {
                    null
                }
            val remainder = wrapper?.let { GemTransfers.insertDedicated(it, stack, stack.count, simulate) } ?: stack
            if (remainder.isEmpty) {
                ItemStack.EMPTY
            } else if (wrapper != null) {
                GemOperationContext.withoutRouting(storage) { original.insertItem(slot, remainder, simulate) }
            } else {
                original.insertItem(slot, remainder, simulate)
            }
        }

    override fun insertItem(
        stack: ItemStack,
        simulate: Boolean,
    ): ItemStack =
        if (!simulate && Thread.currentThread().threadGroup != SidedThreadGroups.SERVER) {
            stack
        } else if (GemOperationContext.routingSuppressed(storage)) {
            original.insertItem(stack, simulate)
        } else {
            val permitted = (0 until original.slots).any { original.isItemValid(it, stack) }
            val wrapper =
                if (permitted) {
                    GemCaseUpgradeItem.find(storage)?.takeIf {
                        it.settingsSnapshot() is GemSettingsState.Ready
                    }
                } else {
                    null
                }
            val remainder = wrapper?.let { GemTransfers.insertDedicated(it, stack, stack.count, simulate) } ?: stack
            if (remainder.isEmpty) {
                ItemStack.EMPTY
            } else if (wrapper != null) {
                GemOperationContext.withoutRouting(storage) { original.insertItem(remainder, simulate) }
            } else {
                original.insertItem(remainder, simulate)
            }
        }

    override fun isInsertBlocked(): Boolean {
        if (GemOperationContext.routingSuppressed(storage)) return original.isInsertBlocked
        val wrapper = GemCaseUpgradeItem.find(storage)?.takeIf { it.settingsSnapshot() is GemSettingsState.Ready }
        val state = wrapper?.snapshot() as? GemStorageState.Ready
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
