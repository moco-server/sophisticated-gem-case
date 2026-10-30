package org.mocoserver.sophisticatedgemcase.network

import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.server.level.ServerLevel
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMenuSession
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingBudget
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingMaterials
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingPlanner
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingPreview
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingReason
import org.mocoserver.sophisticatedgemcase.processing.GemRecipeEpoch
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialState
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsState

/** batch内で探索回数とwire byteを共有し、素材編集はGem解決の成否から分離する。 */
internal object GemProcessingOverview {
    fun create(
        menu: StorageContainerMenuBase<*>,
        container: GemCaseContainer,
        session: GemMenuSession,
        level: ServerLevel,
    ): GemProcessingOverviewPayload {
        val materials = container.upgradeWrapper.materialsSnapshot(level.registryAccess()) as? GemMaterialState.Ready
        val settings = container.upgradeWrapper.processingSettingsSnapshot() as? GemProcessingSettingsState.Ready
        val budget = GemProcessingBudget()
        var remaining = GemProcessingOverviewPayload.MAX_PAYLOAD_BYTES - GemProcessingOverviewPayload.MAX_HEADER_BYTES
        val previews =
            session.processingGemId
                ?.let { gem ->
                    Purity.entries.filter { it != Purity.CRACKED }.map { purity ->
                        val preview =
                            GemProcessingPlanner.preview(
                                menu.storageWrapper,
                                level,
                                GemKey(gem, purity.serializedName),
                                budget,
                            )
                        // 後続の固定理由previewも必ず収まるよう、各出力に最低限の余白を残す。
                        val encoded = GemProcessingPreviewLimits.encodedPreviewSize(preview)
                        val reserved = GemProcessingOverviewPayload.MAX_OUTPUTS * MIN_PREVIEW_BYTES
                        val bounded = if (encoded <= remaining - reserved) preview else oversized(preview)
                        remaining -= GemProcessingPreviewLimits.encodedPreviewSize(bounded).toInt()
                        bounded
                    }
                }.orEmpty()
        session.processingPreviewOutputs =
            previews.filter { it.reason == GemProcessingReason.READY }.map { it.output }.toSet()
        session.previewRevision = session.revision
        return GemProcessingOverviewPayload(
            menu.containerId,
            container.upgradeContainerId,
            session.id,
            session.revision,
            GemRecipeEpoch.current(),
            session.processingGemId,
            session.processingSelectionId,
            materials?.let { GemProcessingMaterials.hasMaterials(it.contents) } ?: false,
            materials == null,
            settings?.useBackpackMaterials ?: false,
            settings == null,
            previews,
        )
    }

    private fun oversized(preview: GemProcessingPreview): GemProcessingPreview =
        GemProcessingPreview(
            preview.output,
            preview.hasMaterials,
            preview.materialsLocked,
            preview.useBackpackMaterials,
            preview.settingsLocked,
            GemProcessingReason.PREVIEW_OVERSIZE,
            null,
            emptyList(),
        )

    private const val MIN_PREVIEW_BYTES = 320
}
