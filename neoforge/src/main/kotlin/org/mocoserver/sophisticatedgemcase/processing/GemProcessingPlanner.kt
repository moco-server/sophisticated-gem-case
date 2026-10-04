package org.mocoserver.sophisticatedgemcase.processing

import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.server.level.ServerLevel
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.core.GemPurityPlanner
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialContents
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialState
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsState
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** 作者recipe順、材料slot順、Ingredient候補順を保った計画と表示概要を作る。 */
internal object GemProcessingPlanner {
    fun preview(
        storage: IStorageWrapper,
        level: ServerLevel,
        output: GemKey,
    ): GemProcessingPreview = decide(storage, level, output).preview

    fun plan(
        storage: IStorageWrapper,
        level: ServerLevel,
        output: GemKey,
    ): GemProcessingPlan? = decide(storage, level, output).plan

    private fun decide(
        storage: IStorageWrapper,
        level: ServerLevel,
        output: GemKey,
    ): Decision {
        val preparation = prepare(storage, level, output)
        val context = preparation.context ?: return requireNotNull(preparation.failure)
        return chooseAndPlan(context, storage, level)
    }

    @Suppress("ReturnCount") // upgrade・保存域・pure・個数の各契約違反を変更前に返す。
    private fun prepare(
        storage: IStorageWrapper,
        level: ServerLevel,
        output: GemKey,
    ): Preparation {
        val wrapper = GemCaseUpgradeItem.find(storage)
        if (wrapper == null || !wrapper.supportsProcessing) {
            return Preparation(failure = decision(output, reason = GemProcessingReason.NOT_ADVANCED))
        }
        val snapshot = readSnapshot(wrapper, level, output)
        val stored = snapshot.stored ?: return Preparation(failure = snapshot.failure)
        val transition = resolveTransition(output) ?: return Preparation(failure = transitionFailure(output, stored))
        val invalidCount = stored.countFailure(output, transition.input)
        if (invalidCount != null) {
            return Preparation(
                failure =
                    decision(
                        output,
                        ProcessingAvailability(
                            hasMaterials = GemProcessingMaterials.hasMaterials(stored.materials),
                            useBackpackMaterials = stored.useBackpackMaterials,
                        ),
                        invalidCount,
                    ),
            )
        }
        return Preparation(
            context =
                PlanningContext(
                    wrapper,
                    stored.counts,
                    stored.materials,
                    stored.useBackpackMaterials,
                    transition.input,
                    transition.inputPurity,
                ),
        )
    }

    @Suppress("ReturnCount") // Locked counts/material/settingsを各境界で区別して返す。
    private fun readSnapshot(
        wrapper: GemCaseUpgradeWrapper,
        level: ServerLevel,
        output: GemKey,
    ): SnapshotPreparation {
        val counts = (wrapper.snapshot() as? GemStorageState.Ready)?.counts
        if (counts == null) {
            return SnapshotPreparation(failure = decision(output, reason = GemProcessingReason.LOCKED_COUNTS))
        }
        val materialState = wrapper.materialsSnapshot(level.registryAccess())
        val materials = (materialState as? GemMaterialState.Ready)?.contents
        if (materials == null) {
            val hasContents = (materialState as? GemMaterialState.ClientSummary)?.hasContents == true
            return SnapshotPreparation(
                failure =
                    decision(
                        output,
                        ProcessingAvailability(hasMaterials = hasContents, materialsLocked = true),
                        GemProcessingReason.LOCKED_MATERIALS,
                    ),
            )
        }
        val settingState = wrapper.processingSettingsSnapshot()
        val useBackpackMaterials = (settingState as? GemProcessingSettingsState.Ready)?.useBackpackMaterials
        if (useBackpackMaterials == null) {
            return SnapshotPreparation(
                failure =
                    decision(
                        output,
                        ProcessingAvailability(
                            hasMaterials = GemProcessingMaterials.hasMaterials(materials),
                            settingsLocked = true,
                        ),
                        GemProcessingReason.LOCKED_SETTINGS,
                    ),
            )
        }
        return SnapshotPreparation(StoredSnapshot(counts, materials, useBackpackMaterials))
    }

    @Suppress("ReturnCount") // 未知純度・最下位純度・標準Gem再構成不能を個別に拒否する。
    private fun resolveTransition(output: GemKey): GemTransition? {
        if (Purity.entries.none { it.serializedName == output.purity }) return null
        val inputPurity = GemPurityRecipes.inputPurity(output.purity) ?: return null
        val input = GemKey(output.gemId, inputPurity.serializedName)
        return if (StandardGem.stackOf(input) != null && StandardGem.stackOf(output) != null) {
            GemTransition(input, inputPurity)
        } else {
            null
        }
    }

    private fun transitionFailure(
        output: GemKey,
        stored: StoredSnapshot,
    ): Decision {
        val reason =
            if (Purity.entries.none { it.serializedName == output.purity }) {
                GemProcessingReason.UNRESOLVED_GEM
            } else if (GemPurityRecipes.inputPurity(output.purity) == null) {
                GemProcessingReason.NOT_ADVANCED
            } else {
                GemProcessingReason.UNRESOLVED_GEM
            }
        return decision(
            output,
            ProcessingAvailability(
                hasMaterials = GemProcessingMaterials.hasMaterials(stored.materials),
                useBackpackMaterials = stored.useBackpackMaterials,
            ),
            reason,
        )
    }

    @Suppress("ReturnCount") // native成立・各budget枯渇・材料不足を別の固定reasonで返す。
    private fun chooseAndPlan(
        context: PlanningContext,
        storage: IStorageWrapper,
        level: ServerLevel,
    ): Decision {
        val output = GemKey(context.input.gemId, context.inputPurity.next().serializedName)
        val budget = GemProcessingBudget()
        val recipes = GemPurityRecipes.recipes(level)
        val recipeScan =
            GemProcessingRecipeSelector.findDedicated(recipes, context.inputPurity, context.materials, budget)
        if (recipeScan.exhausted) {
            return decision(output, context.availability, GemProcessingReason.BUDGET_EXCEEDED)
        }
        var selection = recipeScan.selection
        if (selection == null && context.useBackpackMaterials) {
            val candidates = GemProcessingRecipeSelector.normalCandidates(storage, context.materials, budget)
            if (candidates == null) {
                return decision(output, context.availability, GemProcessingReason.BUDGET_EXCEEDED)
            }
            val fallback =
                GemProcessingRecipeSelector.findSupplemented(
                    recipes,
                    context.inputPurity,
                    candidates,
                    budget,
                )
            if (fallback.exhausted) {
                return decision(output, context.availability, GemProcessingReason.BUDGET_EXCEEDED)
            }
            selection = fallback.selection
        }
        val selected =
            selection
                ?: return decision(output, context.availability, GemProcessingReason.MATERIAL_SHORTAGE)

        return makePlan(context, output, selected)
    }

    @Suppress("ReturnCount") // input不成立とpreview wire超過はいずれも加工不可として返す。
    private fun makePlan(
        context: PlanningContext,
        output: GemKey,
        selected: GemProcessingSelection,
    ): Decision {
        val purityPlan =
            GemPurityPlanner.plan(context.counts, context.input, output, selected.allocation)
                ?: return decision(output, context.availability, GemProcessingReason.INPUT_SHORTAGE)
        val updatedMaterials = GemProcessingMaterials.updated(context.materials, selected.allocation)
        val costs = GemProcessingMaterials.costs(selected.allocation, selected.stacksBySlot)
        val preview =
            GemProcessingPreview(
                output = output,
                hasMaterials = GemProcessingMaterials.hasMaterials(context.materials),
                materialsLocked = false,
                useBackpackMaterials = context.useBackpackMaterials,
                settingsLocked = false,
                reason = GemProcessingReason.READY,
                recipeId = selected.recipe.id,
                costs = costs,
            )
        if (!org.mocoserver.sophisticatedgemcase.network.GemProcessingPreviewLimits
                .isEncodable(preview)
        ) {
            return decision(output, context.availability, GemProcessingReason.PREVIEW_OVERSIZE)
        }
        val plan =
            GemProcessingPlan(
                wrapper = context.wrapper,
                expected = GemProcessingExpectedState(context.counts, context.materials, context.useBackpackMaterials),
                purityPlan = purityPlan,
                updatedMaterials = updatedMaterials,
                selection = selected,
                recipeEpoch = GemRecipeEpoch.current(),
            )
        return Decision(
            preview,
            plan,
        )
    }

    private fun decision(
        output: GemKey,
        availability: ProcessingAvailability = ProcessingAvailability(),
        reason: GemProcessingReason,
    ) = Decision(
        GemProcessingPreview(
            output,
            availability.hasMaterials,
            availability.materialsLocked,
            availability.useBackpackMaterials,
            availability.settingsLocked,
            reason,
            null,
            emptyList(),
        ),
        null,
    )

    private data class Decision(
        val preview: GemProcessingPreview,
        val plan: GemProcessingPlan?,
    )

    private data class ProcessingAvailability(
        val hasMaterials: Boolean = false,
        val materialsLocked: Boolean = false,
        val useBackpackMaterials: Boolean = false,
        val settingsLocked: Boolean = false,
    )

    private data class PlanningContext(
        val wrapper: GemCaseUpgradeWrapper,
        val counts: GemCounts,
        val materials: GemMaterialContents,
        val useBackpackMaterials: Boolean,
        val input: GemKey,
        val inputPurity: Purity,
    ) {
        val availability: ProcessingAvailability
            get() =
                ProcessingAvailability(
                    hasMaterials = GemProcessingMaterials.hasMaterials(materials),
                    useBackpackMaterials = useBackpackMaterials,
                )
    }

    private data class StoredSnapshot(
        val counts: GemCounts,
        val materials: GemMaterialContents,
        val useBackpackMaterials: Boolean,
    ) {
        fun countFailure(
            output: GemKey,
            input: GemKey,
        ): GemProcessingReason? =
            when {
                counts.count(input) < GemProcessingPlanner.INPUT_COUNT -> GemProcessingReason.INPUT_SHORTAGE
                counts.count(output) >= GemCounts.CAPACITY -> GemProcessingReason.OUTPUT_FULL
                else -> null
            }
    }

    private data class SnapshotPreparation(
        val stored: StoredSnapshot? = null,
        val failure: Decision? = null,
    )

    private data class GemTransition(
        val input: GemKey,
        val inputPurity: Purity,
    )

    private data class Preparation(
        val context: PlanningContext? = null,
        val failure: Decision? = null,
    )

    private const val INPUT_COUNT: Int = 2
}
