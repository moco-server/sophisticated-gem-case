package org.mocoserver.sophisticatedgemcase.upgrade

import net.minecraft.world.item.ItemStack
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.p3pp3rf1y.sophisticatedbackpacks.api.IInventoryWrapperUpgrade
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemHandler
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemTransferSettings
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingHandler
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsCodec
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsState
import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import java.util.function.Consumer

/** バッグのupgrade保存callbackだけで確定し、過去のsnapshotからの上書きを拒否する。 */
internal class GemCaseUpgradeWrapper(
    storage: IStorageWrapper,
    stack: ItemStack,
    saver: Consumer<ItemStack>,
) : UpgradeWrapperBase<GemCaseUpgradeWrapper, GemCaseUpgradeItem>(storage, stack, saver),
    IInventoryWrapperUpgrade {
    private var cachedData: net.minecraft.world.item.component.CustomData? = null
    private var cachedState: GemStorageState? = null
    private var cachedSettingsData: net.minecraft.world.item.component.CustomData? = null
    private var cachedSettingsState: GemSettingsState? = null

    fun settingsSnapshot(): GemSettingsState {
        val data = upgrade.get(ModDataComponents.gemTransferSettings.get())
        val previous = cachedSettingsState
        return if (data === cachedSettingsData && previous != null) {
            previous
        } else {
            GemSettingsCodec.read(data).also {
                cachedSettingsData = data
                cachedSettingsState = it
            }
        }
    }

    fun replaceSettings(
        expected: GemTransferSettings,
        updated: GemTransferSettings,
    ): Boolean =
        if (Thread.currentThread().threadGroup == SidedThreadGroups.SERVER) {
            val current = settingsSnapshot() as? GemSettingsState.Ready
            if (current?.settings == expected && GemSettingsCodec.canWrite(updated)) {
                upgrade.set(ModDataComponents.gemTransferSettings.get(), GemSettingsCodec.write(updated))
                save()
                true
            } else {
                false
            }
        } else {
            false
        }

    fun snapshot(): GemStorageState {
        val data = upgrade.get(ModDataComponents.gemStorage.get())
        val previous = cachedState
        return if (data === cachedData && previous != null) {
            previous
        } else {
            GemStorageCodec.read(data).also {
                cachedData = data
                cachedState = it
            }
        }
    }

    override fun wrapInventory(inventory: ITrackedContentsItemHandler): ITrackedContentsItemHandler =
        GemProcessingHandler(storageWrapper, inventory)

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
