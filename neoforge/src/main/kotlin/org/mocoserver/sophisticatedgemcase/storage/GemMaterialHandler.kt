package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandlerModifiable
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem

/** 専用材料だけを編集する。simulateは保存callbackを呼ばず、外部のstackを所有しない。 */
internal class GemMaterialHandler(
    private val storage: IStorageWrapper,
    private val level: ServerLevel,
    private val isMaterial: (ItemStack) -> Boolean,
) : IItemHandlerModifiable {
    override fun getSlots(): Int = GemMaterialContents.SLOT_COUNT

    override fun getSlotLimit(slot: Int): Int {
        require(slot in 0 until slots)
        return GemMaterialContents.MAX_STACK_SIZE
    }

    override fun getStackInSlot(slot: Int): ItemStack {
        require(slot in 0 until slots)
        return snapshot()?.stackAt(slot) ?: ItemStack.EMPTY
    }

    override fun isItemValid(
        slot: Int,
        stack: ItemStack,
    ): Boolean = slot in 0 until slots && !stack.isEmpty && isMaterial(stack.copy())

    override fun insertItem(
        slot: Int,
        stack: ItemStack,
        simulate: Boolean,
    ): ItemStack {
        require(slot in 0 until slots)
        val state = snapshot() ?: return stack.copy()
        val old = state.stackAt(slot)
        return if (isItemValid(slot, stack) && (old.isEmpty || ItemStack.isSameItemSameComponents(old, stack))) {
            insertMatching(slot, stack, old, state, simulate)
        } else {
            stack.copy()
        }
    }

    private fun insertMatching(
        slot: Int,
        stack: ItemStack,
        old: ItemStack,
        state: GemMaterialContents,
        simulate: Boolean,
    ): ItemStack {
        val moved = minOf(stack.count, minOf(getSlotLimit(slot), stack.maxStackSize) - old.count).coerceAtLeast(0)
        if (moved == 0) return stack.copy()
        val updated = replacing(state, slot, stack.copyWithCount(old.count + moved))
        val wrapper = GemCaseUpgradeItem.find(storage)
        val accepted =
            GemMaterialCodec.canWrite(updated, level.registryAccess()) &&
                (simulate || wrapper?.replaceMaterials(state, updated, level.registryAccess()) == true)
        return stack.copyWithCount(stack.count - if (accepted) moved else 0)
    }

    override fun extractItem(
        slot: Int,
        amount: Int,
        simulate: Boolean,
    ): ItemStack {
        require(slot in 0 until slots)
        val state = snapshot() ?: return ItemStack.EMPTY
        val old = state.stackAt(slot)
        val moved = minOf(old.count, amount.coerceAtLeast(0))
        val updated = replacing(state, slot, old.copyWithCount(old.count - moved))
        val wrapper = GemCaseUpgradeItem.find(storage)
        val accepted =
            moved > 0 &&
                (simulate || wrapper?.replaceMaterials(state, updated, level.registryAccess()) == true)
        return if (accepted) {
            old.copyWithCount(moved)
        } else {
            ItemStack.EMPTY
        }
    }

    override fun setStackInSlot(
        slot: Int,
        stack: ItemStack,
    ) {
        require(slot in 0 until slots)
        val state = snapshot() ?: return
        val old = state.stackAt(slot)
        // 既存材料の減数はrecipe reload後も認める。新規や増数は現在recipeを要求する。
        val recovery = ItemStack.isSameItemSameComponents(old, stack) && stack.count <= old.count
        if (stack.isEmpty || recovery || isItemValid(slot, stack)) {
            val updated = replacing(state, slot, stack)
            GemCaseUpgradeItem.find(storage)?.replaceMaterials(state, updated, level.registryAccess())
        }
    }

    private fun snapshot(): GemMaterialContents? =
        GemCaseUpgradeItem
            .find(storage)
            ?.takeIf { it.supportsProcessing }
            ?.materialsSnapshot(level.registryAccess())
            .let { (it as? GemMaterialState.Ready)?.contents }

    private fun replacing(
        state: GemMaterialContents,
        slot: Int,
        stack: ItemStack,
    ): GemMaterialContents = GemMaterialContents.of(List(slots) { if (it == slot) stack else state.stackAt(it) })
}
