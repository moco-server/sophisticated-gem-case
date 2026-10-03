package org.mocoserver.sophisticatedgemcase.menu

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** 保存用の入力slotを作らず、serverに束縛された操作sessionを持つタブcontainer。 */
internal class GemCaseContainer(
    player: Player,
    id: Int,
    wrapper: GemCaseUpgradeWrapper,
    type: UpgradeContainerType<GemCaseUpgradeWrapper, GemCaseContainer>,
) : UpgradeContainerBase<GemCaseUpgradeWrapper, GemCaseContainer>(player, id, wrapper, type) {
    private var session: GemMenuSession? = null

    fun isCurrent(menu: StorageContainerMenuBase<*>): Boolean =
        menu.storageWrapper.upgradeHandler.slotWrappers[upgradeContainerId] === upgradeWrapper &&
            menu.storageWrapper.upgradeHandler.getStackInSlot(upgradeContainerId) === upgradeWrapper.upgradeStack

    fun sessionFor(menu: StorageContainerMenuBase<*>): GemMenuSession {
        val storageId = menu.storageWrapper.contentsUuid.orElse(null)
        val stack = menu.storageWrapper.upgradeHandler.getStackInSlot(upgradeContainerId)
        val current =
            session?.takeIf { it.matchesStorage(storageId, stack) }
                ?: GemMenuSession(storageId, stack, upgradeWrapper.snapshot(), upgradeWrapper.settingsSnapshot()).also {
                    session =
                        it
                }
        current.refresh(upgradeWrapper)
        return current
    }

    // nativeの汎用NBT要求は使わず、型付きchannelだけで操作する。
    override fun handlePacket(data: CompoundTag) = Unit

    companion object {
        val TYPE: UpgradeContainerType<GemCaseUpgradeWrapper, GemCaseContainer> =
            UpgradeContainerType(::GemCaseContainer)
    }
}
