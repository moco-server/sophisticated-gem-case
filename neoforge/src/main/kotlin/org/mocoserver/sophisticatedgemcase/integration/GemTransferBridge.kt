package org.mocoserver.sophisticatedgemcase.integration

import net.minecraft.world.item.ItemStack
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.neoforged.neoforge.items.IItemHandler
import net.p3pp3rf1y.sophisticatedbackpacks.api.IItemHandlerInteractionUpgrade
import net.p3pp3rf1y.sophisticatedbackpacks.upgrades.deposit.DepositUpgradeWrapper
import net.p3pp3rf1y.sophisticatedbackpacks.upgrades.restock.RestockUpgradeWrapper
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeWrapper
import org.mocoserver.sophisticatedgemcase.storage.GemExternalTransfers
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem
import java.util.function.Supplier

/** Java Mixinと対象版native wrapper間の、限定された転送境界。 */
internal object GemTransferBridge {
    @JvmStatic
    fun deposit(
        storage: IStorageWrapper,
        nativeUpgrade: IUpgradeWrapper,
        target: IItemHandler,
        transferred: List<ItemStack>,
    ): List<ItemStack> {
        if (!validNative(storage, nativeUpgrade, deposit = true)) return transferred
        return transferred + GemExternalTransfers.deposit(storage, target)
    }

    @JvmStatic
    fun restock(
        storage: IStorageWrapper,
        nativeUpgrade: IUpgradeWrapper,
        source: IItemHandler,
        transferred: List<ItemStack>,
    ): List<ItemStack> {
        if (!validNative(storage, nativeUpgrade, deposit = false)) return transferred
        return transferred + GemExternalTransfers.restock(storage, source)
    }

    @JvmStatic
    fun <T> withoutRouting(
        storage: IStorageWrapper,
        action: Supplier<T>,
    ): T = GemOperationContext.withoutRouting(storage) { action.get() }

    @Suppress("ReturnCount") // server・有効状態・型・装着状態の境界条件を一箇所で判定する。
    private fun validNative(
        storage: IStorageWrapper,
        nativeUpgrade: IUpgradeWrapper,
        deposit: Boolean,
    ): Boolean {
        val onServer = Thread.currentThread().threadGroup == SidedThreadGroups.SERVER
        val matchesExpectedType =
            if (deposit) {
                nativeUpgrade is DepositUpgradeWrapper
            } else {
                nativeUpgrade is RestockUpgradeWrapper
            }
        val current =
            storage.upgradeHandler
                .getWrappersThatImplement(IItemHandlerInteractionUpgrade::class.java)
                .any { it === nativeUpgrade }
        return onServer &&
            nativeUpgrade.isEnabled &&
            matchesExpectedType &&
            current &&
            GemCaseUpgradeItem.find(storage) != null
    }
}
