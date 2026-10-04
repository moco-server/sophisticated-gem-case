package org.mocoserver.sophisticatedgemcase.integration

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.Slot
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase

internal object GemCaseConversionSession {
    fun isUpgradeSlot(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
    ): Boolean {
        val slot = slotAt(menu, slotId)
        return slot != null && (slot as Slot).index == slotId && menu.upgradeSlots.any { it === slot }
    }

    fun context(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
        player: ServerPlayer,
    ): GemCaseConversionContext? {
        val storage = menu.storageWrapper as? IBackpackWrapper
        val storageId = storage?.contentsUuid?.orElse(null)
        val slot = slotAt(menu, slotId)
        val handler = storage?.upgradeHandler
        val hasStorage = storage != null && storageId != null
        val hasSlot = slot != null && handler != null
        if (!hasStorage || !hasSlot) return null
        requireNotNull(storage)
        requireNotNull(storageId)
        requireNotNull(slot)
        requireNotNull(handler)
        val slotIndex = slot.slotIndex
        val globalSlotId = (slot as Slot).index
        return if (matchesNativeSlot(menu, slotId, slot, handler)) {
            GemCaseConversionContext(menu, slot, globalSlotId, slotIndex, storage, storageId, player)
        } else {
            null
        }
    }

    fun isCurrent(context: GemCaseConversionContext): Boolean =
        isServerMenuCurrent(context) && isStorageCurrent(context) && isSlotCurrent(context)

    fun isMenuStorageCurrent(context: GemCaseConversionContext): Boolean =
        isServerMenuCurrent(context) && isStorageCurrent(context)

    private fun slotAt(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
    ): StorageContainerMenuBase<*>.StorageUpgradeSlot? {
        if (slotId !in 0 until menu.totalSlotsNumber) return null
        return menu.getSlot(slotId) as? StorageContainerMenuBase<*>.StorageUpgradeSlot
    }

    private fun matchesNativeSlot(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
        slot: StorageContainerMenuBase<*>.StorageUpgradeSlot,
        handler: net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler,
    ): Boolean =
        slot.slotIndex in 0 until handler.slots &&
            slot.getItemHandler() === handler &&
            (slot as Slot).index == slotId &&
            slotId - menu.firstUpgradeSlot == slot.slotIndex

    private fun isServerMenuCurrent(context: GemCaseConversionContext): Boolean =
        Thread.currentThread().threadGroup == net.neoforged.fml.util.thread.SidedThreadGroups.SERVER &&
            context.player.containerMenu === context.menu &&
            context.menu.stillValid(context.player)

    private fun isStorageCurrent(context: GemCaseConversionContext): Boolean =
        context.menu.storageWrapper === context.storage &&
            context.storage.contentsUuid.orElse(null) == context.storageId

    private fun isSlotCurrent(context: GemCaseConversionContext): Boolean =
        context.menu.getSlot(context.globalSlotId) === context.slot &&
            context.menu.upgradeSlots.any { it === context.slot } &&
            context.slot.getItemHandler() === context.storage.upgradeHandler &&
            context.slot.slotIndex == context.slotIndex &&
            context.globalSlotId >= context.menu.firstUpgradeSlot &&
            context.globalSlotId - context.menu.firstUpgradeSlot == context.slotIndex
}
