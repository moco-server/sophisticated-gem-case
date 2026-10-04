package org.mocoserver.sophisticatedgemcase.network

import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingReason

/** client操作に使うsnapshotがcounts・設定・previewで同じserver世代かを判定する。 */
internal object GemProcessingSync {
    fun canRequestPreview(
        counts: GemPageAccumulator,
        settings: GemSettingsPageAccumulator,
        output: GemKey?,
    ): Boolean = sameGeneration(counts, settings) && output != null

    fun canSetBackpackMaterials(
        counts: GemPageAccumulator,
        settings: GemSettingsPageAccumulator,
        preview: GemProcessingPreviewPayload?,
    ): Boolean =
        sameGeneration(counts, settings) &&
            preview.matches(counts, settings) &&
            preview?.preview?.settingsLocked == false

    fun canEditMaterials(
        counts: GemPageAccumulator,
        settings: GemSettingsPageAccumulator,
        preview: GemProcessingPreviewPayload?,
    ): Boolean =
        sameGeneration(counts, settings) &&
            preview.matches(counts, settings) &&
            preview?.preview?.materialsLocked == false

    fun canProcess(
        counts: GemPageAccumulator,
        settings: GemSettingsPageAccumulator,
        preview: GemProcessingPreviewPayload?,
        output: GemKey?,
    ): Boolean {
        val readyPreview = preview ?: return false
        return sameGeneration(counts, settings) &&
            !counts.locked &&
            output != null &&
            readyPreview.matches(counts, settings) &&
            readyPreview.preview.output == output &&
            !readyPreview.preview.materialsLocked &&
            !readyPreview.preview.settingsLocked &&
            readyPreview.preview.reason == GemProcessingReason.READY
    }

    private fun sameGeneration(
        counts: GemPageAccumulator,
        settings: GemSettingsPageAccumulator,
    ): Boolean =
        counts.complete &&
            settings.complete &&
            counts.sessionId != null &&
            counts.sessionId == settings.sessionId &&
            counts.revision >= 0 &&
            counts.revision == settings.revision

    private fun GemProcessingPreviewPayload?.matches(
        counts: GemPageAccumulator,
        settings: GemSettingsPageAccumulator,
    ): Boolean =
        this != null &&
            sessionId == counts.sessionId &&
            sessionId == settings.sessionId &&
            revision == counts.revision &&
            revision == settings.revision
}
