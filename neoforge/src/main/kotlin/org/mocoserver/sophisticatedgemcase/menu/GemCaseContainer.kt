package org.mocoserver.sophisticatedgemcase.menu

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.ItemStackHandler
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType
import org.mocoserver.sophisticatedgemcase.processing.GemPurityRecipes
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialContents
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** 上位だけ六材料slotを持ち、Gem数量操作はserverへ束縛したsessionで扱う。 */
internal class GemCaseContainer(
    player: Player,
    id: Int,
    wrapper: GemCaseUpgradeWrapper,
    type: UpgradeContainerType<GemCaseUpgradeWrapper, GemCaseContainer>,
) : UpgradeContainerBase<GemCaseUpgradeWrapper, GemCaseContainer>(player, id, wrapper, type) {
    private var session: GemMenuSession? = null

    // menu内だけの表示状態。保存しないため、閉じる要求は古いrevisionでも受け入れる。
    var processingViewOpen: Boolean = false

    init {
        if (wrapper.supportsProcessing) {
            val level = player.level()
            val materials =
                if (level is ServerLevel) {
                    wrapper.materialHandler(level)
                } else {
                    object : ItemStackHandler(GemMaterialContents.SLOT_COUNT) {
                        override fun isItemValid(
                            slot: Int,
                            stack: ItemStack,
                        ): Boolean = GemPurityRecipes.isMaterial(level, stack)
                    }
                }
            repeat(GemMaterialContents.SLOT_COUNT) { index ->
                slots.add(
                    GemMaterialSlot(materials, index, { processingViewOpen }) { owner ->
                        val menu = owner.containerMenu as? StorageContainerMenuBase<*>
                        menu != null && isCurrent(menu) && menu.stillValid(owner)
                    },
                )
            }
        }
    }

    fun isCurrent(menu: StorageContainerMenuBase<*>): Boolean =
        menu.storageWrapper.upgradeHandler.slotWrappers[upgradeContainerId] === upgradeWrapper &&
            menu.storageWrapper.upgradeHandler.getStackInSlot(upgradeContainerId) === upgradeWrapper.upgradeStack

    fun sessionFor(menu: StorageContainerMenuBase<*>): GemMenuSession {
        val storageId = menu.storageWrapper.contentsUuid.orElse(null)
        val stack = menu.storageWrapper.upgradeHandler.getStackInSlot(upgradeContainerId)
        val current =
            session?.takeIf { it.matchesStorage(storageId, stack) }
                ?: GemMenuSession(storageId, stack, upgradeWrapper.snapshot(), upgradeWrapper.settingsSnapshot()).also {
                    if (session != null) processingViewOpen = false
                    session =
                        it
                }
        current.refresh(upgradeWrapper, player.level().registryAccess())
        return current
    }

    // nativeの汎用NBT要求は使わず、型付きchannelだけで操作する。
    override fun handlePacket(data: CompoundTag) = Unit

    companion object {
        val TYPE: UpgradeContainerType<GemCaseUpgradeWrapper, GemCaseContainer> =
            UpgradeContainerType(::GemCaseContainer)
    }
}
