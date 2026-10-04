package org.mocoserver.sophisticatedgemcase.menu

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.SlotItemHandler
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialHandler

/** native材料slotの新規受入をsimulateで検査し、壊れた保存へcursorを消費させない。 */
internal class GemMaterialSlot(
    private val materials: IItemHandler,
    slot: Int,
    private val viewOpen: () -> Boolean,
    private val editable: (Player) -> Boolean,
) : SlotItemHandler(materials, slot, HIDDEN_X, 0) {
    var processingVisible: Boolean = false
    var uiEditable: Boolean = false

    private val canEdit: Boolean
        get() = if (materials is GemMaterialHandler) viewOpen() else processingVisible && uiEditable

    override fun mayPlace(stack: ItemStack): Boolean =
        canEdit &&
            materials.isItemValid(slotIndex, stack) &&
            materials.insertItem(slotIndex, stack, true).count < stack.count

    override fun mayPickup(player: Player): Boolean = canEdit && editable(player) && super.mayPickup(player)

    override fun isActive(): Boolean = if (materials is GemMaterialHandler) viewOpen() else processingVisible

    companion object {
        private const val HIDDEN_X = -1000
    }
}
