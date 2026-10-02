package org.mocoserver.sophisticatedgemcase.integration

import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemHandler
import org.mocoserver.sophisticatedgemcase.mixin.GemMergeInvoker
import org.mocoserver.sophisticatedgemcase.storage.GemInputOutputHandler
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem

/** Java Mixinとの版限定の接続。対象外のslotとclient保存には介入しない。 */
internal object GemCaseBridge {
    private val merging: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    @JvmStatic
    fun wrapInputOutput(
        backpack: BackpackWrapper,
        original: ITrackedContentsItemHandler,
    ): ITrackedContentsItemHandler = GemInputOutputHandler(backpack, original)

    @JvmStatic
    fun handleClick(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
        button: Int,
        clickType: ClickType,
        player: Player,
    ): Boolean {
        val wrapper = GemCaseUpgradeItem.find(menu.storageWrapper)
        val carriedGem = StandardGem.keyOf(menu.carried) != null
        val normalSlot = slotId in 0 until menu.numberOfStorageInventorySlots
        return if (wrapper == null) {
            false
        } else {
            when {
                clickType == ClickType.QUICK_CRAFT && carriedGem -> true
                normalSlot && clickType == ClickType.SWAP -> {
                    button in 0 until player.inventory.containerSize &&
                        StandardGem.keyOf(player.inventory.getItem(button)) != null
                }
                normalSlot && clickType == ClickType.PICKUP && carriedGem ->
                    GemManualClicks.insert(menu, slotId, button, player)
                else -> false
            }
        }
    }

    @JvmStatic
    fun routeMerge(
        menu: StorageContainerMenuBase<*>,
        input: GemMergeInput,
    ): ItemStack? {
        val toStorage = input.start >= 0 && input.end <= menu.numberOfStorageInventorySlots && input.start < input.end
        val fromPlayer =
            menu.slots.drop(menu.numberOfStorageInventorySlots).any {
                it.item === input.stack &&
                    it.mayPickup(menu.playerForGemRouting())
            }
        val eligible = !merging.get() && toStorage && fromPlayer && StandardGem.keyOf(input.stack) != null
        return if (eligible) GemManualClicks.merge(menu, input, merging) else null
    }
}

/** native mergeの引数をまとめ、originalの再呼出しで操作条件を維持する。 */
internal data class GemMergeInput(
    val stack: ItemStack,
    val start: Int,
    val end: Int,
    val reverse: Boolean,
    val maxStack: Boolean,
    val overflow: Boolean,
)

// 対象menuのplayerはnative fieldがprotectedのため、Java accessorで取得する。
private fun StorageContainerMenuBase<*>.playerForGemRouting(): Player = (this as GemMergeInvoker).gemCasePlayer()
