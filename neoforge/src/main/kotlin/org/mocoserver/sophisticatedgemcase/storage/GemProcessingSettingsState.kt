package org.mocoserver.sophisticatedgemcase.storage

/** 通常収納からの補充設定の読み取り結果。summaryはnetwork向けであり保存更新には使えない。 */
internal sealed interface GemProcessingSettingsState {
    data class Ready(
        val useBackpackMaterials: Boolean,
    ) : GemProcessingSettingsState

    data class Locked(
        val reason: String,
    ) : GemProcessingSettingsState

    data class ClientSummary(
        val useBackpackMaterials: Boolean,
        val locked: Boolean,
    ) : GemProcessingSettingsState
}
