package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.world.item.ItemStack
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemHandler
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.integration.GemOperationContext
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** Pickupなどnative処理用handlerへ専用受入だけを足し、通常slotの追跡契約をdelegateへ残す。 */
internal class GemProcessingHandler(
    private val storage: IStorageWrapper,
    private val original: ITrackedContentsItemHandler,
) : ITrackedContentsItemHandler by original {
    override fun insertItem(
        slot: Int,
        stack: ItemStack,
        simulate: Boolean,
    ): ItemStack {
        val permitted = slot in 0 until original.slots && original.isItemValid(slot, stack)
        return insert(stack, simulate, permitted) { remainder -> original.insertItem(slot, remainder, simulate) }
    }

    override fun insertItem(
        stack: ItemStack,
        simulate: Boolean,
    ): ItemStack {
        val permitted = (0 until original.slots).any { original.isItemValid(it, stack) }
        return insert(stack, simulate, permitted) { remainder -> original.insertItem(remainder, simulate) }
    }

    override fun isInsertBlocked(): Boolean {
        if (GemOperationContext.isExclusive(storage) ||
            GemOperationContext.routingSuppressed(storage)
        ) {
            return original.isInsertBlocked
        }
        val wrapper = GemCaseUpgradeItem.find(storage)
        return original.isInsertBlocked && !hasDedicatedSpace(wrapper)
    }

    private fun insert(
        stack: ItemStack,
        simulate: Boolean,
        permitted: Boolean,
        delegateInsert: (ItemStack) -> ItemStack,
    ): ItemStack {
        if (GemOperationContext.isExclusive(storage) ||
            (!simulate && Thread.currentThread().threadGroup != SidedThreadGroups.SERVER)
        ) {
            return stack
        }
        val wrapper =
            if (permitted &&
                !GemOperationContext.routingSuppressed(storage)
            ) {
                GemCaseUpgradeItem.find(storage)
            } else {
                null
            }
        val canRoute = wrapper?.settingsSnapshot() is GemSettingsState.Ready
        val routed =
            if (canRoute) {
                GemTransfers.insertDedicated(
                    requireNotNull(wrapper),
                    stack,
                    stack.count,
                    simulate,
                )
            } else {
                stack
            }
        return if (routed.isEmpty) {
            ItemStack.EMPTY
        } else if (canRoute) {
            GemOperationContext.withoutRouting(storage) { delegateInsert(routed) }
        } else {
            delegateInsert(routed)
        }
    }

    private fun hasDedicatedSpace(wrapper: GemCaseUpgradeWrapper?): Boolean =
        wrapper?.let { case ->
            case.settingsSnapshot() is GemSettingsState.Ready &&
                (case.snapshot() as? GemStorageState.Ready)?.let { state ->
                    state.counts.entries().size < GemStorageCodec.MAX_ENTRIES ||
                        state.counts
                            .entries()
                            .values
                            .any { it < GemCounts.CAPACITY }
                } == true
        } == true
}
