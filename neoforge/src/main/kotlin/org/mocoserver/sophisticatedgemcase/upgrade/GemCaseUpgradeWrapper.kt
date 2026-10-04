package org.mocoserver.sophisticatedgemcase.upgrade

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.p3pp3rf1y.sophisticatedbackpacks.api.IInventoryWrapperUpgrade
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemHandler
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemTransferSettings
import org.mocoserver.sophisticatedgemcase.integration.GemOperationContext
import org.mocoserver.sophisticatedgemcase.integration.GemTransferPermit
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingCommit
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialCodec
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialContents
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialState
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingHandler
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsCodec
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsState
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsCodec
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsState
import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import java.util.function.Consumer

/**
 * バッグのupgrade保存callbackだけで確定し、過去のsnapshotからの上書きを拒否する。
 * 四つの保存域の操作を現物wrapperへ束縛するため、境界の関数数だけを許容する。
 */
@Suppress("TooManyFunctions")
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

    val supportsProcessing: Boolean
        get() = (upgrade.item as GemCaseUpgradeItem).supportsProcessing

    fun materialsSnapshot(registries: HolderLookup.Provider): GemMaterialState =
        GemMaterialCodec.read(upgrade.get(ModDataComponents.gemProcessingMaterials.get()), registries)

    fun processingSettingsSnapshot(): GemProcessingSettingsState =
        GemProcessingSettingsCodec.read(upgrade.get(ModDataComponents.gemProcessingSettings.get()))

    /** 材料回収は他の保存域の破損から独立させる。原文の正常性と現物を再検証する。 */
    fun replaceMaterials(
        expected: GemMaterialContents,
        updated: GemMaterialContents,
        registries: HolderLookup.Provider,
        permit: GemTransferPermit? = null,
    ): Boolean {
        if (!processingMayMutate(permit)) return false
        val state = materialsSnapshot(registries) as? GemMaterialState.Ready
        return if (state?.contents == expected && GemMaterialCodec.canWrite(updated, registries)) {
            saveMaterialData(GemMaterialCodec.write(updated, registries))
            true
        } else {
            false
        }
    }

    fun replaceProcessingSettings(
        expected: Boolean,
        updated: Boolean,
    ): Boolean {
        if (!processingMayMutate(null)) return false
        val state = processingSettingsSnapshot() as? GemProcessingSettingsState.Ready
        return if (state?.useBackpackMaterials == expected) {
            upgrade.set(ModDataComponents.gemProcessingSettings.get(), GemProcessingSettingsCodec.write(updated))
            save()
            true
        } else {
            false
        }
    }

    private fun processingMayMutate(permit: GemTransferPermit?): Boolean =
        supportsProcessing && Thread.currentThread().threadGroup == SidedThreadGroups.SERVER && mayMutate(permit)

    /** 独占操作中の現物と全期待値を照合し、Gemと材料を一つのcallbackで確定する。 */
    fun commitProcessing(
        change: GemProcessingCommit,
        registries: HolderLookup.Provider,
        permit: GemTransferPermit,
    ): Boolean {
        if (!processingMayMutate(permit)) return false
        return if (processingMatches(change, registries) &&
            GemStorageCodec.canWrite(change.updatedCounts) &&
            GemMaterialCodec.canWrite(change.updatedMaterials, registries)
        ) {
            saveProcessingData(
                GemStorageCodec.write(change.updatedCounts),
                GemMaterialCodec.write(change.updatedMaterials, registries),
            )
            true
        } else {
            false
        }
    }

    private fun processingMatches(
        change: GemProcessingCommit,
        registries: HolderLookup.Provider,
    ): Boolean {
        val counts = snapshot() as? GemStorageState.Ready
        val materials = materialsSnapshot(registries) as? GemMaterialState.Ready
        val settings = processingSettingsSnapshot() as? GemProcessingSettingsState.Ready
        return counts?.counts == change.expectedCounts &&
            materials?.contents == change.expectedMaterials &&
            settings?.useBackpackMaterials == change.expectedSettings
    }

    // 外部callbackは例外を投げ得るため、自分が変更した二componentの復元失敗も通知する。
    @Suppress("TooGenericExceptionCaught")
    private fun saveProcessingData(
        counts: CustomData,
        materials: CustomData,
    ) {
        val countsType = ModDataComponents.gemStorage.get()
        val materialsType = ModDataComponents.gemProcessingMaterials.get()
        val oldCounts = upgrade.get(countsType)
        val oldMaterials = upgrade.get(materialsType)
        try {
            upgrade.set(countsType, counts)
            upgrade.set(materialsType, materials)
            save()
        } catch (failure: RuntimeException) {
            upgrade.set(countsType, oldCounts)
            upgrade.set(materialsType, oldMaterials)
            try {
                save()
            } catch (recovery: RuntimeException) {
                failure.addSuppressed(recovery)
            }
            throw failure
        }
    }

    // save callbackの例外時は自分が変更したcomponentだけを復元し、回復失敗も呼出元へ伝える。
    @Suppress("TooGenericExceptionCaught")
    private fun saveMaterialData(data: CustomData) {
        val component = ModDataComponents.gemProcessingMaterials.get()
        val old = upgrade.get(component)
        try {
            upgrade.set(component, data)
            save()
        } catch (failure: RuntimeException) {
            upgrade.set(component, old)
            try {
                save()
            } catch (recovery: RuntimeException) {
                failure.addSuppressed(recovery)
            }
            throw failure
        }
    }

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
        if (Thread.currentThread().threadGroup == SidedThreadGroups.SERVER &&
            GemOperationContext.mayMutate(storageWrapper, null) &&
            GemCaseUpgradeItem.find(storageWrapper) === this
        ) {
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
        permit: GemTransferPermit? = null,
    ): Boolean {
        if (Thread.currentThread().threadGroup != SidedThreadGroups.SERVER || !mayMutate(permit)) return false
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
        if (GemOperationContext.isExclusive(storageWrapper) || materialsGuardRemoval()) {
            true
        } else {
            when (val state = snapshot()) {
                is GemStorageState.Ready -> state.counts.entries().isNotEmpty()
                is GemStorageState.ClientSummary ->
                    state.hasContents ||
                        state.locked ||
                        Thread.currentThread().threadGroup == SidedThreadGroups.SERVER
                is GemStorageState.Locked -> true
            }
        }

    /** 取り外しにはregistryを要求せず、厳密な空schemaだけを空と認める。 */
    private fun materialsGuardRemoval(): Boolean {
        val data = upgrade.get(ModDataComponents.gemProcessingMaterials.get())
        if (!supportsProcessing || data == null) return false

        @Suppress("DEPRECATION")
        val tag = data.unsafe
        val entries = tag.get("entries") as? ListTag
        return if (tag.allKeys == setOf("client_summary", "has_contents", "locked")) {
            materialSummaryGuardsRemoval(tag)
        } else {
            tag.allKeys != setOf("schema_version", "entries") ||
                !tag.contains("schema_version", Tag.TAG_INT.toInt()) ||
                tag.getInt("schema_version") != 1 ||
                entries == null ||
                !entries.isEmpty()
        }
    }

    private fun materialSummaryGuardsRemoval(tag: net.minecraft.nbt.CompoundTag): Boolean {
        val booleans = listOf("client_summary", "has_contents", "locked")
        val valid =
            booleans.all { tag.contains(it, Tag.TAG_BYTE.toInt()) && tag.getByte(it).toInt() in 0..1 } &&
                tag.getByte("client_summary").toInt() == 1
        return !valid ||
            Thread.currentThread().threadGroup == SidedThreadGroups.SERVER ||
            tag.getBoolean("has_contents") ||
            tag.getBoolean("locked")
    }

    fun mayMutate(permit: GemTransferPermit?): Boolean =
        GemOperationContext.mayMutate(storageWrapper, permit) && GemCaseUpgradeItem.find(storageWrapper) === this

    override fun canBeDisabled(): Boolean = false
}
