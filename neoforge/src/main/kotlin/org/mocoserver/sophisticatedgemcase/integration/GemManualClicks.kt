package org.mocoserver.sophisticatedgemcase.integration

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.mixin.GemMergeInvoker
import org.mocoserver.sophisticatedgemcase.storage.GemTransfers
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem

/** vanillaの予測で専用収納を変更せず、serverだけがcursorとslotを確定する。 */
internal object GemManualClicks {
    fun insert(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
        button: Int,
        player: Player,
    ): Boolean {
        val slot = menu.getSlot(slotId)
        val allowed = slot.mayPlace(menu.carried) && button in 0..1
        if (allowed && Thread.currentThread().threadGroup == SidedThreadGroups.SERVER && menu.stillValid(player)) {
            val inventory = menu.storageWrapper.inventoryHandler
            val incoming = menu.carried.copyWithCount(if (button == 1) 1 else menu.carried.count)
            val wrapper = requireNotNull(GemCaseUpgradeItem.find(menu.storageWrapper))
            val dedicated = GemTransfers.insertDedicated(wrapper, incoming, incoming.count, false)
            val remaining =
                if (dedicated.isEmpty) {
                    ItemStack.EMPTY
                } else {
                    inventory.insertItem(
                        slot.containerSlot,
                        dedicated,
                        false,
                    )
                }
            menu.carried = menu.carried.copyWithCount(menu.carried.count - (incoming.count - remaining.count))
            menu.broadcastChanges()
        }
        return allowed
    }

    fun merge(
        menu: StorageContainerMenuBase<*>,
        input: GemMergeInput,
        guard: ThreadLocal<Boolean>,
    ): ItemStack? {
        val wrapper = GemCaseUpgradeItem.find(menu.storageWrapper)
        val player = (menu as GemMergeInvoker).gemCasePlayer()
        val allowed = (input.start until input.end).any { menu.getSlot(it).mayPlace(input.stack) }
        return if (wrapper == null || !allowed) {
            null
        } else if (Thread.currentThread().threadGroup != SidedThreadGroups.SERVER || !menu.stillValid(player)) {
            input.stack.copy()
        } else {
            val limit = if (input.maxStack) minOf(input.stack.count, input.stack.maxStackSize) else input.stack.count
            val remainder = GemTransfers.insertDedicated(wrapper, input.stack, limit, false)
            val taken = input.stack.count - remainder.count
            if (remainder.isEmpty || taken == limit) {
                remainder
            } else {
                guard.set(true)
                try {
                    val next = remainder.copyWithCount(minOf(remainder.count, limit - taken))
                    val after =
                        (menu as GemMergeInvoker).gemCaseMerge(
                            next,
                            input.start,
                            input.end,
                            input.reverse,
                            false,
                            input.overflow,
                        )
                    remainder.copyWithCount(remainder.count - (next.count - after.count))
                } finally {
                    guard.remove()
                }
            }
        }
    }
}
