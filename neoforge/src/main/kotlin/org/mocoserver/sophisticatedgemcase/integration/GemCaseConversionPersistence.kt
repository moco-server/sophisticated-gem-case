package org.mocoserver.sophisticatedgemcase.integration

import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

internal object GemCaseConversionPersistence {
    fun isPersistedUpper(
        context: GemCaseConversionContext,
        target: ItemStack,
    ): Boolean {
        val menu = context.menu
        val slot = menu.getSlot(context.globalSlotId) as? StorageContainerMenuBase<*>.StorageUpgradeSlot
        val handler = context.storage.upgradeHandler
        val wrapper = handler.slotWrappers[context.slotIndex] as? GemCaseUpgradeWrapper
        val container = menu.upgradeContainers[context.slotIndex] as? GemCaseContainer
        return slotMatches(context, slot, handler) &&
            stackMatches(context, target, handler) &&
            wrapperMatches(context, wrapper, container, handler)
    }

    private fun slotMatches(
        context: GemCaseConversionContext,
        slot: StorageContainerMenuBase<*>.StorageUpgradeSlot?,
        handler: UpgradeHandler,
    ): Boolean =
        GemCaseConversionSession.isMenuStorageCurrent(context) &&
            slot != null &&
            hasExpectedIndices(context, slot) &&
            hasExpectedHandler(context, slot, handler)

    private fun hasExpectedIndices(
        context: GemCaseConversionContext,
        slot: StorageContainerMenuBase<*>.StorageUpgradeSlot,
    ): Boolean = (slot as Slot).index == context.globalSlotId && slot.getSlotIndex() == context.slotIndex

    private fun hasExpectedHandler(
        context: GemCaseConversionContext,
        slot: StorageContainerMenuBase<*>.StorageUpgradeSlot,
        handler: UpgradeHandler,
    ): Boolean = slot.getItemHandler() === handler && context.menu.upgradeSlots.any { it === slot }

    private fun stackMatches(
        context: GemCaseConversionContext,
        target: ItemStack,
        handler: UpgradeHandler,
    ): Boolean = ItemStack.matches(target, handler.getStackInSlot(context.slotIndex))

    private fun wrapperMatches(
        context: GemCaseConversionContext,
        wrapper: GemCaseUpgradeWrapper?,
        container: GemCaseContainer?,
        handler: UpgradeHandler,
    ): Boolean =
        wrapper != null &&
            container != null &&
            wrapper.supportsProcessing &&
            wrapper.upgradeStack === handler.getStackInSlot(context.slotIndex) &&
            container.upgradeWrapper === wrapper
}
