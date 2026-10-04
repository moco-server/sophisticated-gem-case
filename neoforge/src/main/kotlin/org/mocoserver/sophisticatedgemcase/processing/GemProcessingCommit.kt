package org.mocoserver.sophisticatedgemcase.processing

import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialContents

/** 同一snapshotから計画したGemと材料だけを、一回の保存で置換する契約。 */
internal data class GemProcessingCommit(
    val expectedCounts: GemCounts,
    val expectedMaterials: GemMaterialContents,
    val expectedSettings: Boolean,
    val updatedCounts: GemCounts,
    val updatedMaterials: GemMaterialContents,
)
