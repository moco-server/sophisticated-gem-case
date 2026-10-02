package org.mocoserver.sophisticatedgemcase.upgrade

import net.minecraft.world.item.ItemStack
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import java.util.function.Consumer

/** バッグのupgrade保存callbackだけで確定し、過去のsnapshotからの上書きを拒否する。 */
internal class GemCaseUpgradeWrapper(
    storage: IStorageWrapper,
    stack: ItemStack,
    saver: Consumer<ItemStack>,
) : UpgradeWrapperBase<GemCaseUpgradeWrapper, GemCaseUpgradeItem>(storage, stack, saver) {
    fun snapshot(): GemStorageState = GemStorageCodec.read(upgrade.get(ModDataComponents.gemStorage.get()))

    fun replace(
        expected: GemCounts,
        updated: GemCounts,
    ): Boolean {
        if (Thread.currentThread().threadGroup != SidedThreadGroups.SERVER) return false
        val current = snapshot() as? GemStorageState.Ready
        return if (current?.counts == expected && GemStorageCodec.canWrite(updated)) {
            upgrade.set(ModDataComponents.gemStorage.get(), GemStorageCodec.write(updated))
            save()
            true
        } else {
            false
        }
    }

    fun hasContentsOrLocked(): Boolean =
        when (val state = snapshot()) {
            is GemStorageState.Ready -> state.counts.entries().isNotEmpty()
            is GemStorageState.ClientSummary ->
                state.hasContents ||
                    state.locked ||
                    Thread.currentThread().threadGroup == SidedThreadGroups.SERVER
            is GemStorageState.Locked -> true
        }

    override fun canBeDisabled(): Boolean = false
}
