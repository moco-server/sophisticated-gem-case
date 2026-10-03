package org.mocoserver.sophisticatedgemcase.storage

import org.mocoserver.sophisticatedgemcase.core.GemTransferSettings

/** 設定原文の検証状態。Lockedのraw componentは呼出側で保持し、置換しない。 */
internal sealed interface GemSettingsState {
    data class Ready(
        val settings: GemTransferSettings,
    ) : GemSettingsState

    data class Locked(
        val reason: String,
    ) : GemSettingsState

    data class ClientSummary(
        val locked: Boolean,
    ) : GemSettingsState
}
