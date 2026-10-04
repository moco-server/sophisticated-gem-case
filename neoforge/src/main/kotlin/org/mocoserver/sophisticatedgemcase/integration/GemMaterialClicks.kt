package org.mocoserver.sophisticatedgemcase.integration

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.neoforged.neoforge.items.SlotItemHandler
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMaterialSlot
import org.mocoserver.sophisticatedgemcase.processing.GemPurityRecipes
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialContents
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialState
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper
import java.util.UUID

private data class Context(
    val menu: StorageContainerMenuBase<*>,
    val player: ServerPlayer,
    val storage: IBackpackWrapper,
    val storageId: UUID,
    val container: GemCaseContainer,
    val wrapper: GemCaseUpgradeWrapper,
    val targetSlot: GemMaterialSlot?,
    val targetSlotId: Int?,
    val sourceSlot: Slot?,
    val sourceSlotId: Int?,
)

private data class SlotSelection(
    val targetSlot: GemMaterialSlot? = null,
    val targetSlotId: Int? = null,
    val sourceSlot: Slot? = null,
    val sourceSlotId: Int? = null,
)

/** 材料slotのnative clickをcopy計画と一回の保存へ変換する。 */
internal object GemMaterialClicks {
    /** 対象slot操作は拒否も含めconsumeし、通常slotの他操作はnativeへ残す。 */
    @Suppress("ReturnCount") // 対象slot拒否・native委譲の境界を明示して返す。
    fun tryHandle(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
        button: Int,
        clickType: ClickType,
        player: Player,
    ): Boolean {
        val active = ClickRouting.activeContainer(menu)
        if (ClickProtection.rejectUnsafeClick(active, clickType, player, menu.carried)) return true
        val materialTarget = ClickRouting.slotOrNull(menu, slotId) as? GemMaterialSlot
        if (materialTarget != null) {
            return ClickTargetHandler.handle(
                menu,
                SlotSelection(targetSlot = materialTarget, targetSlotId = slotId),
                button,
                clickType,
                player,
            )
        }
        if (clickType != ClickType.QUICK_MOVE || active == null) return false
        return ClickShiftHandler.handle(menu, slotId, player, active)
    }
}

private object ClickProtection {
    fun rejectUnsafeClick(
        active: GemCaseContainer?,
        clickType: ClickType,
        player: Player,
        cursor: ItemStack,
    ): Boolean =
        active != null &&
            ClickRouting.isUnsafeCollectOrDrag(clickType) &&
            (ClickRouting.materialAt(player, cursor) || ClickRouting.storedMaterialMatches(active, cursor))
}

private object ClickTargetHandler {
    @Suppress("ReturnCount") // client・player・対象view境界では対象slot操作を必ずconsumeする。
    fun handle(
        menu: StorageContainerMenuBase<*>,
        selection: SlotSelection,
        button: Int,
        clickType: ClickType,
        player: Player,
    ): Boolean {
        val target = selection.targetSlot ?: return true
        val slotId = selection.targetSlotId ?: return true
        if (player.level().isClientSide) return true
        val serverPlayer = player as? ServerPlayer ?: return true
        when {
            clickType == ClickType.PICKUP && button in 0..1 -> {
                val context = ClickRouting.targetContext(menu, slotId, target, serverPlayer)
                if (context != null) ClickOperations.runPickup(context, button)
            }
            clickType == ClickType.QUICK_MOVE -> {
                val container = ClickRouting.containerFor(menu, target)
                if (container != null) {
                    val context =
                        ContextFactory.create(
                            menu,
                            serverPlayer,
                            container,
                            SlotSelection(targetSlot = target, targetSlotId = slotId),
                        )
                    if (context != null) ClickOperations.runShiftOutOfMaterials(context)
                }
            }
        }
        return true
    }
}

private object ClickShiftHandler {
    @Suppress("ReturnCount") // quick-moveは不正contextをnative処理へ流さない境界を保つ。
    fun handle(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
        player: Player,
        active: GemCaseContainer,
    ): Boolean {
        if (player.level().isClientSide) return ClickRouting.sourceIsMaterial(menu, slotId, player)
        val serverPlayer = player as? ServerPlayer ?: return false
        val context = ClickRouting.sourceContext(menu, slotId, active, serverPlayer) ?: return false
        ClickOperations.runShiftIntoMaterials(context)
        return true
    }
}

private data class ClickPlan(
    val updatedMaterials: GemMaterialContents,
    val updatedCursor: ItemStack,
)

private data class Destination(
    val index: Int,
    val before: ItemStack,
    val after: ItemStack,
)

private data class ShiftOutPlan(
    val updatedMaterials: GemMaterialContents,
    val destinations: List<Destination>,
)

private data class ShiftInPlan(
    val updatedMaterials: GemMaterialContents,
    val movedCount: Int,
)

private object ClickRouting {
    /** レシピ消失後も保存済み材料をnativeのまとめ回収から保護する。 */
    fun storedMaterialMatches(
        container: GemCaseContainer?,
        cursor: ItemStack,
    ): Boolean =
        !cursor.isEmpty &&
            container?.slots?.filterIsInstance<GemMaterialSlot>()?.any {
                ItemStack.isSameItemSameComponents(it.item, cursor)
            } == true

    fun isUnsafeCollectOrDrag(clickType: ClickType): Boolean =
        clickType == ClickType.QUICK_CRAFT || clickType == ClickType.PICKUP_ALL

    fun slotOrNull(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
    ): Slot? = if (slotId in 0 until menu.totalSlotsNumber) menu.getSlot(slotId) else null

    fun activeContainer(menu: StorageContainerMenuBase<*>): GemCaseContainer? =
        menu.upgradeContainers.values
            .filterIsInstance<GemCaseContainer>()
            .firstOrNull { it.upgradeWrapper.supportsProcessing && it.processingViewOpen }

    fun targetContext(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
        slot: GemMaterialSlot,
        player: ServerPlayer,
    ): Context? {
        val container = containerFor(menu, slot) ?: return null
        return ContextFactory.create(menu, player, container, SlotSelection(targetSlot = slot, targetSlotId = slotId))
    }

    fun containerFor(
        menu: StorageContainerMenuBase<*>,
        slot: GemMaterialSlot,
    ): GemCaseContainer? =
        menu.upgradeContainers.values
            .filterIsInstance<GemCaseContainer>()
            .firstOrNull { it.slots.any { candidate -> candidate === slot } }

    @Suppress("ReturnCount") // sourceの権限・材料・現物不一致はnativeへ戻す前の拒否境界。
    fun sourceContext(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
        container: GemCaseContainer,
        player: ServerPlayer,
    ): Context? {
        val sourceSlot = slotOrNull(menu, slotId) ?: return null
        val storageInventory = menu.storageWrapper.inventoryHandler
        val playerInventory = player.inventory
        val isStorageSlot = menu.isStorageInventorySlot(slotId) && sourceSlot.container !== playerInventory
        val isPlayerSlot = sourceSlot.container === playerInventory
        if (!SourceEligibility.isEligibleSource(
                sourceSlot,
                isStorageSlot,
                isPlayerSlot,
                storageInventory.slots,
                player,
            )
        ) {
            return null
        }
        return ContextFactory.create(
            menu,
            player,
            container,
            SlotSelection(sourceSlot = sourceSlot, sourceSlotId = slotId),
        )
    }

    fun materialAt(
        player: Player,
        stack: ItemStack,
    ): Boolean = !stack.isEmpty && GemPurityRecipes.isMaterial(player.level(), stack.copy())

    fun sourceIsMaterial(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
        player: Player,
    ): Boolean {
        val source = slotOrNull(menu, slotId) ?: return false
        val isSource =
            (menu.isStorageInventorySlot(slotId) && source.container !== player.inventory) ||
                source.container === player.inventory
        return isSource && source.hasItem() && materialAt(player, source.item) && StandardGem.keyOf(source.item) == null
    }
}

private object SourceEligibility {
    fun isEligibleSource(
        sourceSlot: Slot,
        isStorageSlot: Boolean,
        isPlayerSlot: Boolean,
        storageSlotCount: Int,
        player: ServerPlayer,
    ): Boolean =
        isFromAllowedInventory(isStorageSlot, isPlayerSlot) &&
            sourceCanBeMoved(sourceSlot, player) &&
            isValidStorageSlot(sourceSlot, isStorageSlot, storageSlotCount)

    private fun isFromAllowedInventory(
        isStorageSlot: Boolean,
        isPlayerSlot: Boolean,
    ): Boolean = isStorageSlot || isPlayerSlot

    private fun sourceCanBeMoved(
        source: Slot,
        player: ServerPlayer,
    ): Boolean =
        source.hasItem() &&
            source.mayPickup(player) &&
            ClickRouting.materialAt(player, source.item) &&
            StandardGem.keyOf(source.item) == null

    @Suppress("ReturnCount") // native handlerへのcastとindex範囲を別々に検証する。
    private fun isValidStorageSlot(
        source: Slot,
        isStorageSlot: Boolean,
        storageSlotCount: Int,
    ): Boolean {
        if (!isStorageSlot) return true
        val handlerSlot = source as? SlotItemHandler ?: return false
        return handlerSlot.slotIndex in 0 until storageSlotCount
    }
}

private object ContextFactory {
    @Suppress("ReturnCount") // 各境界条件で不正なGUI contextを早期に拒否する。
    fun create(
        menu: StorageContainerMenuBase<*>,
        player: ServerPlayer,
        container: GemCaseContainer,
        selection: SlotSelection,
    ): Context? {
        if (Thread.currentThread().threadGroup != SidedThreadGroups.SERVER) return null
        if (player.containerMenu !== menu || !menu.stillValid(player) || !container.processingViewOpen) return null
        val storage = menu.storageWrapper as? IBackpackWrapper ?: return null
        val storageId = storage.contentsUuid.orElse(null) ?: return null
        if (!container.isCurrent(menu) || !selectedSlotsMatch(menu, selection)) return null
        val wrapper = container.upgradeWrapper
        if (!wrapper.supportsProcessing || wrapper !== currentWrapper(menu, container)) return null
        return Context(
            menu,
            player,
            storage,
            storageId,
            container,
            wrapper,
            selection.targetSlot,
            selection.targetSlotId,
            selection.sourceSlot,
            selection.sourceSlotId,
        )
    }

    private fun selectedSlotsMatch(
        menu: StorageContainerMenuBase<*>,
        selection: SlotSelection,
    ): Boolean =
        selectedSlotMatches(menu, selection.targetSlot, selection.targetSlotId) &&
            selectedSlotMatches(menu, selection.sourceSlot, selection.sourceSlotId)

    private fun selectedSlotMatches(
        menu: StorageContainerMenuBase<*>,
        slot: Slot?,
        slotId: Int?,
    ): Boolean = slot == null || (slotId != null && menu.getSlot(slotId) === slot)

    private fun currentWrapper(
        menu: StorageContainerMenuBase<*>,
        container: GemCaseContainer,
    ): GemCaseUpgradeWrapper? =
        menu.storageWrapper.upgradeHandler.slotWrappers[container.upgradeContainerId] as? GemCaseUpgradeWrapper

    fun current(context: Context): Boolean =
        menuStillCurrent(context) &&
            storageStillCurrent(context) &&
            containerStillCurrent(context) &&
            selectedSlotsMatch(
                context.menu,
                SlotSelection(
                    context.targetSlot,
                    context.targetSlotId,
                    context.sourceSlot,
                    context.sourceSlotId,
                ),
            )

    private fun menuStillCurrent(context: Context): Boolean =
        context.player.containerMenu === context.menu && context.menu.stillValid(context.player)

    private fun storageStillCurrent(context: Context): Boolean =
        context.menu.storageWrapper === context.storage &&
            context.storage.contentsUuid.orElse(null) == context.storageId

    private fun containerStillCurrent(context: Context): Boolean =
        context.container.processingViewOpen &&
            context.container.isCurrent(context.menu) &&
            currentWrapper(context.menu, context.container) === context.wrapper
}

private object ClickOperations {
    @Suppress("ReturnCount") // 保存前検証と計画失敗は状態変更なしで終了する。
    fun runPickup(
        context: Context,
        button: Int,
    ) {
        val before = ClickState.readyMaterials(context) ?: return
        val cursor = context.menu.carried.copy()
        val plan = ClickPlanner.planPickup(context, before, cursor, button) ?: return
        transact(context, before, plan.updatedMaterials, cursor, plan.updatedCursor)
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount") // 外部callback失敗を戻し、確定前の無効条件で終了する。
    fun runShiftIntoMaterials(context: Context) {
        val source = context.sourceSlot ?: return
        val before = ClickState.readyMaterials(context) ?: return
        val sourceBefore = source.item.copy()
        val plan = ClickPlanner.planShiftInto(before, sourceBefore) ?: return
        withExclusive(context) { permit ->
            if (!ContextFactory.current(context) ||
                !ItemStack.matches(sourceBefore, source.item) ||
                ClickState.readyMaterials(context) != before
            ) {
                return@withExclusive
            }
            val sourceAfter = sourceBefore.copyWithCount(sourceBefore.count - plan.movedCount)
            try {
                ClickSourceInventory.setSource(context, source, sourceAfter)
                if (!ContextFactory.current(context)) {
                    ClickSourceInventory.restoreSource(context, source, sourceBefore, null)
                    return@withExclusive
                }
                val saved =
                    context.wrapper.replaceMaterials(
                        before,
                        plan.updatedMaterials,
                        context.player.serverLevel().registryAccess(),
                        permit,
                    ) {
                        ContextFactory.current(context) &&
                            ItemStack.matches(sourceAfter, source.item) &&
                            ClickState.readyMaterials(context) == plan.updatedMaterials
                    }
                if (!saved) {
                    ClickSourceInventory.restoreSource(context, source, sourceBefore, null)
                    return@withExclusive
                }
            } catch (failure: RuntimeException) {
                ClickSourceInventory.restoreSource(context, source, sourceBefore, failure)
                throw failure
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount") // 外部callback失敗を戻し、確定前の無効条件で終了する。
    fun runShiftOutOfMaterials(context: Context) {
        val before = ClickState.readyMaterials(context) ?: return
        val index = context.targetSlot?.slotIndex ?: return
        val source = before.stackAt(index)
        if (source.isEmpty) return
        val plan = ClickPlanner.planShiftOut(context, before, index, source) ?: return
        withExclusive(context) { permit ->
            if (!ContextFactory.current(context) || ClickState.readyMaterials(context) != before) return@withExclusive
            try {
                ClickInventory.applyDestinations(context, plan.destinations)
                if (!ContextFactory.current(context)) {
                    restoreDestinations(context, plan.destinations, null)
                    return@withExclusive
                }
                val saved =
                    context.wrapper.replaceMaterials(
                        before,
                        plan.updatedMaterials,
                        context.player.serverLevel().registryAccess(),
                        permit,
                    ) {
                        ContextFactory.current(context) &&
                            ClickDestinationCheck.destinationsMatch(context, plan.destinations) &&
                            ClickState.readyMaterials(context) == plan.updatedMaterials
                    }
                if (!saved) {
                    restoreDestinations(context, plan.destinations, null)
                    return@withExclusive
                }
            } catch (failure: RuntimeException) {
                restoreDestinations(context, plan.destinations, failure)
                throw failure
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // 保存失敗時にcursorを保存前のcopyへ戻す。
    private fun transact(
        context: Context,
        before: GemMaterialContents,
        updated: GemMaterialContents,
        cursor: ItemStack,
        updatedCursor: ItemStack,
    ) {
        withExclusive(context) { permit ->
            if (!ContextFactory.current(context) ||
                !ItemStack.matches(cursor, context.menu.carried) ||
                ClickState.readyMaterials(context) != before
            ) {
                return@withExclusive
            }
            try {
                if (!context.wrapper.replaceMaterials(
                        before,
                        updated,
                        context.player.serverLevel().registryAccess(),
                        permit,
                    ) {
                        ContextFactory.current(context) &&
                            ItemStack.matches(cursor, context.menu.carried) &&
                            ClickState.readyMaterials(context) == updated
                    }
                ) {
                    return@withExclusive
                }
                context.menu.carried = updatedCursor.copy()
            } catch (failure: RuntimeException) {
                context.menu.carried = cursor.copy()
                throw failure
            }
        }
    }

    private fun withExclusive(
        context: Context,
        action: (GemTransferPermit) -> Unit,
    ) {
        GemOperationContext.exclusive(context.storage) { permit -> action(permit) }
    }
}

private object ClickPlanner {
    @Suppress("ReturnCount") // pickupの各ケースは更新計画一つまたは拒否を返す。
    fun planPickup(
        context: Context,
        before: GemMaterialContents,
        cursor: ItemStack,
        button: Int,
    ): ClickPlan? {
        val slotIndex = context.targetSlot?.slotIndex ?: return null
        val stored = before.stackAt(slotIndex)
        if (cursor.isEmpty) {
            if (stored.isEmpty) return null
            val amount = if (button == RIGHT_CLICK) (stored.count + 1) / 2 else stored.count
            return ClickPlan(
                ClickMaterialMath.replace(before, slotIndex, stored.copyWithCount(stored.count - amount)),
                stored.copyWithCount(amount),
            )
        }
        if (!ClickRouting.materialAt(context.player, cursor)) return null
        if (!stored.isEmpty && !ItemStack.isSameItemSameComponents(stored, cursor)) {
            if (button == RIGHT_CLICK || cursor.count > ClickMaterialMath.slotCapacity(cursor)) return null
            return ClickPlan(ClickMaterialMath.replace(before, slotIndex, cursor), stored.copy())
        }
        val max = ClickMaterialMath.slotCapacity(cursor)
        val amount = minOf(if (button == RIGHT_CLICK) 1 else cursor.count, (max - stored.count).coerceAtLeast(0))
        if (amount <= 0) return null
        val updatedSlot = cursor.copyWithCount(stored.count + amount)
        return ClickPlan(
            ClickMaterialMath.replace(before, slotIndex, updatedSlot),
            cursor.copyWithCount(cursor.count - amount),
        )
    }

    fun planShiftInto(
        before: GemMaterialContents,
        source: ItemStack,
    ): ShiftInPlan? {
        val stacks = MutableList(GemMaterialContents.SLOT_COUNT) { before.stackAt(it) }
        val mergeIndices =
            stacks.indices.filter {
                !stacks[it].isEmpty &&
                    ClickMaterialMath.sameItem(
                        stacks[it],
                        source,
                    )
            }
        val emptyIndices = stacks.indices.filter { stacks[it].isEmpty }
        val remainingAfterMerge = ClickMaterialMath.fillSlots(stacks, source, mergeIndices, source.count)
        val remaining = ClickMaterialMath.fillSlots(stacks, source, emptyIndices, remainingAfterMerge)
        val moved = source.count - remaining
        return if (moved > 0) ShiftInPlan(GemMaterialContents.of(stacks), moved) else null
    }

    fun planShiftOut(
        context: Context,
        before: GemMaterialContents,
        materialIndex: Int,
        source: ItemStack,
    ): ShiftOutPlan? {
        val destinations = mutableListOf<Destination>()
        val afterStorage = collectStorageDestinations(context, source, destinations)
        val remaining = collectPlayerDestinations(context, afterStorage, destinations)
        val moved = source.count - remaining.count
        if (moved <= 0) return null
        return ShiftOutPlan(
            ClickMaterialMath.replace(before, materialIndex, source.copyWithCount(source.count - moved)),
            destinations,
        )
    }

    private fun collectStorageDestinations(
        context: Context,
        source: ItemStack,
        destinations: MutableList<Destination>,
    ): ItemStack {
        val inventory = context.storage.inventoryHandler
        val remaining = source.copy()
        val candidates = minOf(inventory.slots, MAX_NORMAL_DESTINATIONS)
        for (index in 0 until candidates) {
            val destination =
                if (inventory.isSlotAccessible(index)) {
                    storageDestination(context, index, remaining)
                } else {
                    null
                }
            if (destination != null) {
                destinations.add(destination)
                remaining.shrink(destination.after.count - destination.before.count)
                if (remaining.isEmpty) break
            }
        }
        return remaining
    }

    @Suppress("ReturnCount") // 容量・native受入・item一致の拒否では移動計画を作らない。
    private fun storageDestination(
        context: Context,
        index: Int,
        source: ItemStack,
    ): Destination? {
        val inventory = context.storage.inventoryHandler
        val old = inventory.getStackInSlot(index).copy()
        if (old.isEmpty && !inventory.isItemValid(index, source, context.player)) return null
        if (!old.isEmpty && !ClickMaterialMath.sameItem(old, source)) return null
        val limit = minOf(source.maxStackSize, inventory.getSlotLimit(index), inventory.getStackLimit(index, source))
        val moved = minOf(source.count, (limit - old.count).coerceAtLeast(0))
        if (moved <= 0) return null
        return Destination(index, old, source.copyWithCount(old.count + moved))
    }

    private fun collectPlayerDestinations(
        context: Context,
        source: ItemStack,
        destinations: MutableList<Destination>,
    ): ItemStack {
        if (source.isEmpty) return source
        var remaining = source
        val playerSlots =
            context.menu.slots.filter {
                it.container === context.player.inventory &&
                    it.mayPlace(remaining)
            }
        for (slot in playerSlots) {
            val destination = playerDestination(context, slot, remaining)
            if (destination != null) {
                destinations.add(destination)
                remaining =
                    remaining.copyWithCount(remaining.count - destination.after.count + destination.before.count)
                if (remaining.isEmpty) break
            }
        }
        return remaining
    }

    @Suppress("ReturnCount") // item一致と容量不足は移動先へ採用しない。
    private fun playerDestination(
        context: Context,
        slot: Slot,
        source: ItemStack,
    ): Destination? {
        val index = slot.slotIndex
        val old =
            context.player.inventory
                .getItem(index)
                .copy()
        if (!old.isEmpty && !ClickMaterialMath.sameItem(old, source)) return null
        val limit = minOf(context.player.inventory.maxStackSize, source.maxStackSize)
        val moved = minOf(source.count, (limit - old.count).coerceAtLeast(0))
        if (moved <= 0) return null
        return Destination(PLAYER_DESTINATION + index, old, source.copyWithCount(old.count + moved))
    }
}

private object ClickInventory {
    fun applyDestinations(
        context: Context,
        destinations: List<Destination>,
    ) {
        val inventory = context.storage.inventoryHandler
        destinations.forEach { destination ->
            if (destination.index >= PLAYER_DESTINATION) {
                context.player.inventory.setItem(destination.index - PLAYER_DESTINATION, destination.after.copy())
            } else {
                inventory.setStackInSlot(destination.index, destination.after.copy())
            }
        }
    }
}

/** 一つの復元callbackが失敗しても残り全slotの復元を試み、例外を失わない。 */
@Suppress("TooGenericExceptionCaught") // 外部inventory保存callbackの失敗を集約して元の例外へ残す。
private fun restoreDestinations(
    context: Context,
    destinations: List<Destination>,
    failure: RuntimeException?,
) {
    var primary = failure
    val inventory = context.storage.inventoryHandler
    destinations.asReversed().forEach { destination ->
        try {
            if (destination.index >= PLAYER_DESTINATION) {
                context.player.inventory.setItem(destination.index - PLAYER_DESTINATION, destination.before.copy())
            } else {
                inventory.setStackInSlot(destination.index, destination.before.copy())
            }
        } catch (recovery: RuntimeException) {
            val previous = primary
            if (previous == null) {
                primary = recovery
            } else if (previous !== recovery) {
                previous.addSuppressed(recovery)
            }
        }
    }
    if (failure == null) primary?.let { throw it }
}

private object ClickDestinationCheck {
    fun destinationsMatch(
        context: Context,
        destinations: List<Destination>,
    ): Boolean =
        destinations.all { destination ->
            val current =
                if (destination.index >= PLAYER_DESTINATION) {
                    context.player.inventory.getItem(destination.index - PLAYER_DESTINATION)
                } else {
                    context.storage.inventoryHandler.getStackInSlot(destination.index)
                }
            ItemStack.matches(destination.after, current)
        }
}

private object ClickState {
    fun readyMaterials(context: Context): GemMaterialContents? =
        (
            context.wrapper.materialsSnapshot(
                context.player.serverLevel().registryAccess(),
            ) as? GemMaterialState.Ready
        )?.contents
}

private object ClickMaterialMath {
    fun fillSlots(
        stacks: MutableList<ItemStack>,
        source: ItemStack,
        indices: List<Int>,
        initialRemaining: Int,
    ): Int {
        var remaining = initialRemaining
        for (index in indices) {
            val old = stacks[index]
            val moved = minOf((slotCapacity(source) - old.count).coerceAtLeast(0), remaining)
            if (moved > 0) {
                stacks[index] = source.copyWithCount(old.count + moved)
                remaining -= moved
                if (remaining == 0) break
            }
        }
        return remaining
    }

    fun sameItem(
        left: ItemStack,
        right: ItemStack,
    ): Boolean = ItemStack.isSameItemSameComponents(left, right)

    fun slotCapacity(stack: ItemStack): Int = minOf(GemMaterialContents.MAX_STACK_SIZE, stack.maxStackSize)

    fun replace(
        contents: GemMaterialContents,
        index: Int,
        stack: ItemStack,
    ): GemMaterialContents =
        GemMaterialContents.of(
            List(GemMaterialContents.SLOT_COUNT) {
                if (it ==
                    index
                ) {
                    stack
                } else {
                    contents.stackAt(it)
                }
            },
        )
}

private object ClickSourceInventory {
    fun setSource(
        context: Context,
        source: Slot,
        updated: ItemStack,
    ) {
        if (source.container === context.player.inventory) {
            source.set(updated.copy())
        } else {
            val handler = context.storage.inventoryHandler
            val index = (source as SlotItemHandler).slotIndex
            check(index in 0 until handler.slots) { "Material source slot index changed" }
            handler.setStackInSlot(index, updated.copy())
        }
    }

    @Suppress("TooGenericExceptionCaught") // 外部inventory callback失敗時にsourceの保存前copyを復元する。
    fun restoreSource(
        context: Context,
        source: Slot,
        original: ItemStack,
        failure: RuntimeException?,
    ) {
        try {
            setSource(context, source, original)
        } catch (recovery: RuntimeException) {
            attachOrThrow(failure, recovery)
        }
    }

    private fun attachOrThrow(
        failure: RuntimeException?,
        recovery: RuntimeException,
    ) {
        if (failure == null) throw recovery
        if (failure !== recovery) failure.addSuppressed(recovery)
    }
}

private const val RIGHT_CLICK: Int = 1
private const val PLAYER_DESTINATION: Int = 100_000
private const val MAX_NORMAL_DESTINATIONS: Int = 8_192
