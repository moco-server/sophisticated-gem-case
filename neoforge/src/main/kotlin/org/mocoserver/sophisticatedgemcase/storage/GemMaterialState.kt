package org.mocoserver.sophisticatedgemcase.storage

/** 永続材料の読み取り結果。summaryはnetwork向けであり保存更新には使えない。 */
internal sealed interface GemMaterialState {
    data class Ready(
        val contents: GemMaterialContents,
    ) : GemMaterialState

    data class Locked(
        val reason: String,
    ) : GemMaterialState

    data class ClientSummary(
        val hasContents: Boolean,
        val locked: Boolean,
    ) : GemMaterialState
}
