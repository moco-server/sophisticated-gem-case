package org.mocoserver.sophisticatedgemcase.integration

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickType
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.ModItems

/** 下位upgrade専用変換をnative storage menuのclick境界へ接続する。 */
internal object GemCaseConversion {
    fun tryHandle(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
        button: Int,
        clickType: ClickType,
        player: Player,
    ): Boolean {
        if (!isConversionGesture(menu, slotId, button, clickType) ||
            menu.carried.item !== ModItems.gemCaseUpgradeConversion.get()
        ) {
            return false
        }
        if (!player.level().isClientSide) convertOnServer(menu, slotId, player)
        return true
    }

    private fun convertOnServer(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
        player: Player,
    ) {
        val serverPlayer = player as? ServerPlayer ?: return
        val context = GemCaseConversionSession.context(menu, slotId, serverPlayer) ?: return
        GemOperationContext.exclusive(context.storage) { GemCaseConversionActions.convert(context) }
    }

    private fun isConversionGesture(
        menu: StorageContainerMenuBase<*>,
        slotId: Int,
        button: Int,
        clickType: ClickType,
    ): Boolean =
        clickType == ClickType.PICKUP &&
            button == CONVERSION_BUTTON &&
            GemCaseConversionSession.isUpgradeSlot(menu, slotId)

    private const val CONVERSION_BUTTON: Int = 1
}
