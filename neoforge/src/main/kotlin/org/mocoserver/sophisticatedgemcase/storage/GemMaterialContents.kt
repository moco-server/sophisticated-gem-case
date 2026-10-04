package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.world.item.ItemStack

/** 加工専用6枠を所有し、呼出元とのItemStack共有を避ける。 */
internal class GemMaterialContents private constructor(
    stacks: List<ItemStack>,
) {
    private val ownedStacks: List<ItemStack> = stacks.map { it.copy() }

    init {
        require(stacks.size == SLOT_COUNT) { "material slot count" }
    }

    fun stackAt(slot: Int): ItemStack {
        require(slot in 0 until SLOT_COUNT) { "material slot" }
        return ownedStacks[slot].copy()
    }

    override fun equals(other: Any?): Boolean =
        other is GemMaterialContents &&
            (0 until SLOT_COUNT).all { ItemStack.matches(ownedStacks[it], other.ownedStacks[it]) }

    override fun hashCode(): Int =
        ownedStacks.fold(1) { hash, stack ->
            31 * hash + 31 * (stack.count + 31 * ItemStack.hashItemAndComponents(stack))
        }

    companion object {
        const val SLOT_COUNT: Int = 6
        const val MAX_STACK_SIZE: Int = 64

        fun of(stacks: List<ItemStack>): GemMaterialContents = GemMaterialContents(stacks)

        fun empty(): GemMaterialContents = GemMaterialContents(List(SLOT_COUNT) { ItemStack.EMPTY })
    }
}
