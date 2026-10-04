package org.mocoserver.sophisticatedgemcase.processing

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemPurityPlan
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialContents
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper
import java.util.Collections

/** 加工を確定するために再照合する全snapshotと変更案。 */
internal class GemProcessingPlan(
    val wrapper: GemCaseUpgradeWrapper,
    val expected: GemProcessingExpectedState,
    val purityPlan: GemPurityPlan,
    val updatedMaterials: GemMaterialContents,
    selection: GemProcessingSelection,
    val recipeEpoch: Long,
) {
    val expectedCounts: GemCounts = expected.counts
    val expectedMaterials: GemMaterialContents = expected.materials
    val expectedSettings: Boolean = expected.useBackpackMaterials
    val recipeId: ResourceLocation = selection.recipe.id
    val leftCandidateOrdinal: Int = selection.pair.leftOrdinal
    val leftRequired: Int = selection.pair.leftRequired
    val rightCandidateOrdinal: Int = selection.pair.rightOrdinal
    val rightRequired: Int = selection.pair.rightRequired
    val normalSlots: List<NormalSlotChange> = Collections.unmodifiableList(selection.normalChanges.map { it.copy() })
    val costs: List<GemMaterialCost> =
        Collections.unmodifiableList(
            GemProcessingMaterials.costs(selection.allocation, selection.stacksBySlot),
        )
}

/** 計画時に期待するGem・専用材料・補充設定の一組。 */
internal data class GemProcessingExpectedState(
    val counts: GemCounts,
    val materials: GemMaterialContents,
    val useBackpackMaterials: Boolean,
)

/** 通常収納から消費する実slotの変更前後を所有する。 */
internal class NormalSlotChange(
    val slot: Int,
    before: ItemStack,
    after: ItemStack,
) {
    private val beforeStack: ItemStack = before.copy()
    private val afterStack: ItemStack = after.copy()

    val before: ItemStack
        get() = beforeStack.copy()

    val after: ItemStack
        get() = afterStack.copy()

    fun copy(): NormalSlotChange = NormalSlotChange(slot, beforeStack, afterStack)
}
