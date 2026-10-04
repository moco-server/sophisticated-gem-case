package org.mocoserver.sophisticatedgemcase.processing

import net.minecraft.resources.ResourceLocation
import org.mocoserver.sophisticatedgemcase.core.GemKey
import java.util.Collections

/** 画面と通信へ公開してよい、選択済みrecipeの材料概要。 */
@Suppress("LongParameterList") // wire同期とGUI契約のfield群を一対一で保持するimmutable DTO。
internal class GemProcessingPreview(
    output: GemKey,
    hasMaterials: Boolean,
    materialsLocked: Boolean,
    useBackpackMaterials: Boolean,
    settingsLocked: Boolean,
    reason: GemProcessingReason,
    recipeId: ResourceLocation?,
    costs: List<GemMaterialCost>,
) {
    val output: GemKey = output
    val hasMaterials: Boolean = hasMaterials
    val materialsLocked: Boolean = materialsLocked
    val useBackpackMaterials: Boolean = useBackpackMaterials
    val settingsLocked: Boolean = settingsLocked
    val reason: GemProcessingReason = reason
    val recipeId: ResourceLocation? = recipeId
    val costs: List<GemMaterialCost> = Collections.unmodifiableList(costs.toList())
}

/** previewに表示する材料消費の左右。 */
internal enum class GemMaterialSide {
    LEFT,
    RIGHT,
}

/** 選択recipeから実際に割り当てた、item ID別の材料消費。 */
internal data class GemMaterialCost(
    val itemId: ResourceLocation,
    val count: Int,
    val side: GemMaterialSide,
)

/** 任意の例外文ではなく、クライアントが固定表示できる加工状態。 */
internal enum class GemProcessingReason {
    READY,
    NOT_ADVANCED,
    UNRESOLVED_GEM,
    LOCKED_COUNTS,
    LOCKED_MATERIALS,
    LOCKED_SETTINGS,
    INPUT_SHORTAGE,
    OUTPUT_FULL,
    MATERIAL_SHORTAGE,
    BUDGET_EXCEEDED,
    PREVIEW_OVERSIZE,
}
