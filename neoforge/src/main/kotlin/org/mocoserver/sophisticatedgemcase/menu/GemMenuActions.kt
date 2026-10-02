package org.mocoserver.sophisticatedgemcase.menu

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.network.GemAction
import org.mocoserver.sophisticatedgemcase.network.GemRequest
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.GemTransfers
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** serverの現在menuと保存状態からだけ操作を確定する。古い要求や出力拒否は無変更。 */
internal object GemMenuActions {
    fun perform(
        player: ServerPlayer,
        request: GemRequest,
    ): Boolean {
        val menu = player.containerMenu as? StorageContainerMenuBase<*>
        val container = menu?.upgradeContainers?.get(request.upgradeSlot) as? GemCaseContainer
        val permitted =
            menu != null && container != null && menu.containerId == request.menuId && menu.stillValid(player)
        return if (permitted && Thread.currentThread().threadGroup == SidedThreadGroups.SERVER) {
            val currentMenu = requireNotNull(menu)
            val current = requireNotNull(container)
            val session = current.sessionFor(currentMenu)
            val valid =
                session.id == request.sessionId && session.revision == request.revision && session.storageId != null
            if (valid && current.isCurrent(currentMenu)) dispatch(player, currentMenu, current, request) else false
        } else {
            false
        }
    }

    private fun dispatch(
        player: ServerPlayer,
        menu: StorageContainerMenuBase<*>,
        container: GemCaseContainer,
        request: GemRequest,
    ): Boolean {
        val withdrawal =
            request.action in setOf(GemAction.WITHDRAW_ONE, GemAction.WITHDRAW_STACK, GemAction.WITHDRAW_TO_PLAYER)
        val inputShape =
            (withdrawal == (request.key != null)) && (request.action == GemAction.REQUEST_PAGE || request.page == 0)
        val changed =
            inputShape &&
                when (request.action) {
                    GemAction.REQUEST_PAGE -> container.sessionFor(menu).validPage(request.page)
                    GemAction.MOVE_NORMAL -> GemTransfers.moveFromNormal(menu.storageWrapper) > 0
                    GemAction.INSERT_CURSOR -> insertCursor(menu, container.upgradeWrapper)
                    else -> withdraw(player, menu, container.upgradeWrapper, request)
                }
        container.sessionFor(menu)
        if (changed && request.action != GemAction.REQUEST_PAGE) menu.broadcastChanges()
        return changed
    }

    private fun insertCursor(
        menu: StorageContainerMenuBase<*>,
        wrapper: GemCaseUpgradeWrapper,
    ): Boolean {
        val before = menu.carried.copy()
        val after = GemTransfers.insertDedicated(wrapper, before, before.count, false)
        val changed = after.count != before.count
        if (changed) menu.carried = after
        return changed
    }

    private fun withdraw(
        player: ServerPlayer,
        menu: StorageContainerMenuBase<*>,
        wrapper: GemCaseUpgradeWrapper,
        request: GemRequest,
    ): Boolean {
        val before = wrapper.snapshot() as? GemStorageState.Ready
        val key = request.key
        val present = before != null && key != null && before.counts.count(key) > 0
        return if (present) {
            val stack = StandardGem.stackOf(requireNotNull(key))
            if (stack == null) {
                false
            } else {
                val limit = if (request.action == GemAction.WITHDRAW_ONE) 1 else stack.maxStackSize
                val amount = minOf(requireNotNull(before).counts.count(key), limit)
                val targets =
                    if (request.action ==
                        GemAction.WITHDRAW_TO_PLAYER
                    ) {
                        playerTargets(player, stack, amount)
                    } else {
                        cursorTarget(menu, stack, amount)
                    }
                commitWithdrawal(wrapper, before, key, targets)
            }
        } else {
            false
        }
    }

    private data class Target(
        val original: ItemStack,
        val updated: ItemStack,
        val current: () -> ItemStack,
        val set: (ItemStack) -> Unit,
    )

    private fun cursorTarget(
        menu: StorageContainerMenuBase<*>,
        stack: ItemStack,
        amount: Int,
    ): List<Target> {
        val before = menu.carried.copy()
        val compatible = before.isEmpty || ItemStack.isSameItemSameComponents(before, stack)
        val moved = if (compatible) minOf(amount, stack.maxStackSize - before.count) else 0
        return if (moved >
            0
        ) {
            listOf(
                Target(before, stack.copyWithCount(before.count + moved), { menu.carried }, { menu.carried = it }),
            )
        } else {
            emptyList()
        }
    }

    private fun playerTargets(
        player: ServerPlayer,
        stack: ItemStack,
        amount: Int,
    ): List<Target> {
        val targets = mutableListOf<Target>()
        var remaining = amount
        for (slot in 0 until PLAYER_INVENTORY_SLOTS) {
            val before = player.inventory.getItem(slot).copy()
            val compatible = before.isEmpty || ItemStack.isSameItemSameComponents(before, stack)
            val moved = if (compatible) minOf(remaining, stack.maxStackSize - before.count) else 0
            if (moved > 0) {
                targets.add(
                    Target(before, stack.copyWithCount(before.count + moved), {
                        player.inventory.getItem(slot)
                    }, { player.inventory.setItem(slot, it) }),
                )
                remaining -= moved
            }
        }
        return targets
    }

    private fun commitWithdrawal(
        wrapper: GemCaseUpgradeWrapper,
        before: GemStorageState.Ready,
        key: org.mocoserver.sophisticatedgemcase.core.GemKey,
        targets: List<Target>,
    ): Boolean {
        val moved = targets.sumOf { it.updated.count - it.original.count }
        val stable = targets.all { ItemStack.matches(it.current(), it.original) }
        val change = before.counts.extract(key, moved)
        val accepted = moved > 0 && stable && wrapper.replace(before.counts, change.state)
        if (accepted) targets.forEach { it.set(it.updated) }
        return accepted
    }

    private const val PLAYER_INVENTORY_SLOTS = 36
}
