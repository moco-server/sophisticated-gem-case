package org.mocoserver.sophisticatedgemcase.storage

import org.mocoserver.sophisticatedgemcase.core.GemCounts

/** 原文の検証結果。ロックは空収納と異なり、変更と取り外しを許さない。 */
internal sealed interface GemStorageState {
    data class Ready(
        val counts: GemCounts,
    ) : GemStorageState

    data class Locked(
        val reason: String,
    ) : GemStorageState

    data class ClientSummary(
        val hasContents: Boolean,
        val locked: Boolean,
    ) : GemStorageState
}
