package org.mocoserver.sophisticatedgemcase.integration

import net.minecraft.world.item.ItemStack
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem

internal object GemCaseConversionEligibility {
    fun canReplaceWithAdvanced(
        context: GemCaseConversionContext,
        oldStack: ItemStack,
        target: ItemStack,
    ): Boolean {
        val handler = context.storage.upgradeHandler
        val targetItem = target.item as? GemCaseUpgradeItem ?: return false
        return handler.isItemValid(context.slotIndex, target) &&
            targetItem.supportsProcessing &&
            withinUpgradeLimits(context, targetItem, handler) &&
            hasNoConflicts(context, target, targetItem) &&
            hasValidReplacementCondition(context, oldStack, target, targetItem)
    }

    private fun withinUpgradeLimits(
        context: GemCaseConversionContext,
        target: GemCaseUpgradeItem,
        handler: UpgradeHandler,
    ): Boolean {
        val remaining = otherUpgradeStacks(context, handler)
        val sameItem = remaining.count { it.item === target }
        val sameGroup =
            remaining.count { stack ->
                (stack.item as? IUpgradeItem<*>)?.upgradeGroup ===
                    target.upgradeGroup
            }
        val storage = context.storage
        return sameItem < target.getUpgradesPerStorage(storage.storageType) &&
            sameGroup < target.getUpgradesInGroupPerStorage(storage.storageType)
    }

    private fun hasNoConflicts(
        context: GemCaseConversionContext,
        target: ItemStack,
        targetItem: GemCaseUpgradeItem,
    ): Boolean =
        targetItem
            .checkThisForConflictsWithExistingUpgrades(
                target,
                context.storage,
                context.slotIndex,
            ).successful()

    private fun hasValidReplacementCondition(
        context: GemCaseConversionContext,
        oldStack: ItemStack,
        target: ItemStack,
        targetItem: GemCaseUpgradeItem,
    ): Boolean {
        val oldItem = oldStack.item as? IUpgradeItem<*> ?: return false
        return targetItem
            .checkExtraInsertConditions(
                target,
                context.storage,
                false,
                context.slotIndex,
                oldItem,
            ).successful()
    }

    private fun otherUpgradeStacks(
        context: GemCaseConversionContext,
        handler: UpgradeHandler,
    ): List<ItemStack> =
        (0 until handler.slots)
            .filter { it != context.slotIndex }
            .map { handler.getStackInSlot(it) }
            .filter { !it.isEmpty }
}
