package org.mocoserver.sophisticatedgemcase.processing

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.integration.GemOperationContext
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialCodec
import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem

/** server thread上で計画を再検証し、Gem componentと通常材料slotを一回分だけ確定する。 */
internal object GemProcessingEngine {
    fun process(
        storage: IStorageWrapper,
        level: ServerLevel,
        output: GemKey,
        limit: Int,
    ): Int {
        require(limit == 1 || limit == MAX_PROCESSING_COUNT)
        if (Thread.currentThread().threadGroup != SidedThreadGroups.SERVER) return 0
        var processed = 0
        while (processed < limit) {
            val committed =
                GemOperationContext.exclusive(storage) { permit ->
                    val plan = GemProcessingPlanner.plan(storage, level, output) ?: return@exclusive false
                    commit(storage, level, plan, permit)
                } ?: false
            if (!committed) break
            processed++
        }
        return processed
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught") // stale計画・保存拒否・callback例外を変更前後で分離し、rollbackする。
    private fun commit(
        storage: IStorageWrapper,
        level: ServerLevel,
        plan: GemProcessingPlan,
        permit: org.mocoserver.sophisticatedgemcase.integration.GemTransferPermit,
    ): Boolean {
        val inventory = storage.inventoryHandler
        if (!canCommit(storage, level, plan, inventory)) return false

        val changedSlots = mutableListOf<NormalSlotChange>()
        try {
            for (change in plan.normalSlots) {
                changedSlots.add(change)
                inventory.setStackInSlot(change.slot, change.after)
            }
            val saved =
                plan.wrapper.commitProcessing(
                    GemProcessingCommit(
                        expectedCounts = plan.expectedCounts,
                        expectedMaterials = plan.expectedMaterials,
                        expectedSettings = plan.expectedSettings,
                        updatedCounts = plan.purityPlan.counts,
                        updatedMaterials = plan.updatedMaterials,
                    ),
                    level.registryAccess(),
                    permit,
                    verify = { matchesCommitted(storage, level, plan, inventory) },
                )
            if (!saved) {
                restoreNormal(inventory, changedSlots, null)
                return false
            }
            return true
        } catch (failure: RuntimeException) {
            restoreNormal(inventory, changedSlots, failure)
            throw failure
        }
    }

    private fun canCommit(
        storage: IStorageWrapper,
        level: ServerLevel,
        plan: GemProcessingPlan,
        inventory: net.neoforged.neoforge.items.IItemHandlerModifiable,
    ): Boolean =
        isCurrent(storage, level, plan, inventory) &&
            GemStorageCodec.canWrite(plan.purityPlan.counts) &&
            GemMaterialCodec.canWrite(plan.updatedMaterials, level.registryAccess())

    private fun isCurrent(
        storage: IStorageWrapper,
        level: ServerLevel,
        plan: GemProcessingPlan,
        inventory: net.neoforged.neoforge.items.IItemHandlerModifiable,
    ): Boolean {
        val sameEpoch = GemRecipeEpoch.current() == plan.recipeEpoch
        val sameWrapper = GemCaseUpgradeItem.find(storage) === plan.wrapper
        val counts = plan.wrapper.snapshot() as? org.mocoserver.sophisticatedgemcase.storage.GemStorageState.Ready
        val materials =
            plan.wrapper.materialsSnapshot(level.registryAccess()) as?
                org.mocoserver.sophisticatedgemcase.storage.GemMaterialState.Ready
        val settings =
            plan.wrapper.processingSettingsSnapshot() as?
                org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsState.Ready
        return sameEpoch &&
            sameWrapper &&
            counts?.counts == plan.expectedCounts &&
            materials?.contents == plan.expectedMaterials &&
            settings?.useBackpackMaterials == plan.expectedSettings &&
            plan.normalSlots.all { change ->
                change.slot in 0 until inventory.slots &&
                    ItemStack.matches(inventory.getStackInSlot(change.slot), change.before)
            }
    }

    private fun matchesCommitted(
        storage: IStorageWrapper,
        level: ServerLevel,
        plan: GemProcessingPlan,
        inventory: net.neoforged.neoforge.items.IItemHandlerModifiable,
    ): Boolean {
        if (GemRecipeEpoch.current() != plan.recipeEpoch ||
            GemCaseUpgradeItem.find(storage) !== plan.wrapper
        ) {
            return false
        }
        val counts = plan.wrapper.snapshot() as? org.mocoserver.sophisticatedgemcase.storage.GemStorageState.Ready
        val materials =
            plan.wrapper.materialsSnapshot(level.registryAccess()) as?
                org.mocoserver.sophisticatedgemcase.storage.GemMaterialState.Ready
        val settings =
            plan.wrapper.processingSettingsSnapshot() as?
                org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsState.Ready
        return counts?.counts == plan.purityPlan.counts &&
            materials?.contents == plan.updatedMaterials &&
            settings?.useBackpackMaterials == plan.expectedSettings &&
            plan.normalSlots.all { change ->
                ItemStack.matches(inventory.getStackInSlot(change.slot), change.after)
            }
    }

    @Suppress("TooGenericExceptionCaught") // slot復元callbackは任意のRuntimeExceptionを投げ得るので全slotを復元する。
    private fun restoreNormal(
        inventory: net.neoforged.neoforge.items.IItemHandlerModifiable,
        changes: List<NormalSlotChange>,
        failure: RuntimeException?,
    ) {
        var recoveryFailure: RuntimeException? = null
        for (change in changes.asReversed()) {
            try {
                inventory.setStackInSlot(change.slot, change.before)
            } catch (restoreFailure: RuntimeException) {
                if (failure != null) {
                    failure.addSuppressed(restoreFailure)
                } else if (recoveryFailure == null) {
                    recoveryFailure = restoreFailure
                } else {
                    recoveryFailure.addSuppressed(restoreFailure)
                }
            }
        }
        if (failure == null && recoveryFailure != null) throw recoveryFailure
    }

    private const val MAX_PROCESSING_COUNT: Int = 64
}
