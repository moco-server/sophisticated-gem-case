package org.mocoserver.sophisticatedgemcase.integration

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import java.util.UUID

internal data class GemCaseConversionContext(
    val menu: StorageContainerMenuBase<*>,
    val slot: StorageContainerMenuBase<*>.StorageUpgradeSlot,
    val globalSlotId: Int,
    val slotIndex: Int,
    val storage: IBackpackWrapper,
    val storageId: UUID,
    val player: ServerPlayer,
)

internal data class PreparedGemCaseConversion(
    val oldStack: ItemStack,
    val targetStack: ItemStack,
    val cursorStack: ItemStack,
)
