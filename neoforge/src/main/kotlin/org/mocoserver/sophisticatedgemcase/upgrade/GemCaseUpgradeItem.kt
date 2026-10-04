package org.mocoserver.sophisticatedgemcase.upgrade

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeSlotChangeResult
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType

/** 中身を通常交換で持ち出せない、一バッグ一個の専用upgrade。 */
internal class GemCaseUpgradeItem(
    val supportsProcessing: Boolean = false,
) : UpgradeItemBase<GemCaseUpgradeWrapper>(limits) {
    override fun getType(): UpgradeType<GemCaseUpgradeWrapper> = TYPE

    override fun getUpgradeGroup(): UpgradeGroup = GROUP

    override fun getUpgradeConflicts(): List<IUpgradeItem.UpgradeConflictDefinition> = emptyList()

    override fun canAddUpgradeTo(
        storage: IStorageWrapper,
        stack: ItemStack,
        first: Boolean,
        client: Boolean,
    ): UpgradeSlotChangeResult =
        if (storage !is IBackpackWrapper) {
            denied(
                "backpack_only",
            )
        } else {
            super.canAddUpgradeTo(storage, stack, first, client)
        }

    override fun checkExtraInsertConditions(
        stack: ItemStack,
        storage: IStorageWrapper,
        client: Boolean,
        slot: Int,
        existing: IUpgradeItem<*>?,
    ): UpgradeSlotChangeResult {
        if (storage !is IBackpackWrapper) return denied("backpack_only")
        val duplicates =
            storage.upgradeHandler.slotWrappers
                .filter { (index, wrapper) ->
                    index != slot && wrapper is GemCaseUpgradeWrapper
                }.keys
        return if (duplicates.isEmpty()) UpgradeSlotChangeResult.success() else denied("duplicate", duplicates)
    }

    override fun canRemoveUpgradeFrom(
        storage: IStorageWrapper,
        client: Boolean,
    ): UpgradeSlotChangeResult =
        if (find(storage)?.hasContentsOrLocked() == true) denied("not_empty") else UpgradeSlotChangeResult.success()

    override fun canSwapUpgradeFor(
        stack: ItemStack,
        slot: Int,
        storage: IStorageWrapper,
        client: Boolean,
    ): UpgradeSlotChangeResult =
        if (find(storage)?.hasContentsOrLocked() == true) {
            denied("not_empty")
        } else {
            super.canSwapUpgradeFor(stack, slot, storage, client)
        }

    companion object {
        val TYPE = UpgradeType(::GemCaseUpgradeWrapper)
        private val GROUP = UpgradeGroup("gem_case", "sophisticated_gem_case.upgrade_group.gem_case")
        private val limits =
            object : IUpgradeCountLimitConfig {
                override fun getMaxUpgradesPerStorage(
                    type: String,
                    item: ResourceLocation?,
                ): Int = 1

                override fun getMaxUpgradesInGroupPerStorage(
                    type: String,
                    group: UpgradeGroup,
                ): Int = 1
            }

        fun find(storage: IStorageWrapper): GemCaseUpgradeWrapper? =
            storage.upgradeHandler.slotWrappers.values
                .filterIsInstance<GemCaseUpgradeWrapper>()
                .firstOrNull()

        private fun denied(
            reason: String,
            slots: Set<Int> = emptySet(),
        ): UpgradeSlotChangeResult =
            UpgradeSlotChangeResult.fail(
                Component.translatable("sophisticated_gem_case.error.$reason"),
                slots,
                emptySet(),
                emptySet(),
            )
    }
}
