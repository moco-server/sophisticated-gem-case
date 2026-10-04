package org.mocoserver.sophisticatedgemcase.processing

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.mocoserver.sophisticatedgemcase.core.GemMaterialAllocation
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialContents

/** 配分結果から保存後材料とwire材料コストを組み立てる。 */
internal object GemProcessingMaterials {
    fun updated(
        materials: GemMaterialContents,
        allocation: GemMaterialAllocation,
    ): GemMaterialContents =
        GemMaterialContents.of(
            List(GemMaterialContents.SLOT_COUNT) { slot ->
                val stack = materials.stackAt(slot)
                val used = allocation.uses[slot] ?: 0
                when {
                    used == 0 -> stack
                    stack.count == used -> ItemStack.EMPTY
                    else -> stack.copyWithCount(stack.count - used)
                }
            },
        )

    fun costs(
        allocation: GemMaterialAllocation,
        stacksBySlot: Map<Int, ItemStack>,
    ): List<GemMaterialCost> {
        val totals = linkedMapOf<Pair<ResourceLocation, GemMaterialSide>, Long>()
        allocation.leftUses.forEach { (slot, count) ->
            addCost(totals, stacksBySlot, slot, count, GemMaterialSide.LEFT)
        }
        allocation.rightUses.forEach { (slot, count) ->
            addCost(totals, stacksBySlot, slot, count, GemMaterialSide.RIGHT)
        }
        return totals.map { (key, count) -> GemMaterialCost(key.first, count.toInt(), key.second) }
    }

    fun hasMaterials(contents: GemMaterialContents): Boolean =
        (0 until GemMaterialContents.SLOT_COUNT).any { !contents.stackAt(it).isEmpty }

    private fun addCost(
        totals: MutableMap<Pair<ResourceLocation, GemMaterialSide>, Long>,
        stacksBySlot: Map<Int, ItemStack>,
        slot: Int,
        count: Int,
        side: GemMaterialSide,
    ) {
        val itemId = BuiltInRegistries.ITEM.getKey(stacksBySlot.getValue(slot).item)
        val key = itemId to side
        totals[key] = (totals[key] ?: 0L) + count.toLong()
    }
}
