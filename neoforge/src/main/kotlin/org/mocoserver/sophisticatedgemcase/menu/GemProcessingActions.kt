package org.mocoserver.sophisticatedgemcase.menu

import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.network.GemProcessingAction
import org.mocoserver.sophisticatedgemcase.network.GemProcessingRequest
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingEngine
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingPlanner
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingReason
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsState

/** 加工の要求を現在のserver menu・バッグ・世代へ束縛する。 */
internal object GemProcessingActions {
    private const val MAX_PROCESSING = 64

    private data class Context(
        val menu: StorageContainerMenuBase<*>,
        val container: GemCaseContainer,
        val session: GemMenuSession,
    )

    fun perform(
        player: ServerPlayer,
        request: GemProcessingRequest,
    ): Boolean =
        currentContext(player, request)?.let { context ->
            when (request.action) {
                GemProcessingAction.REQUEST_PREVIEW -> {
                    context.session.processingOutput = request.output
                    context.session.previewRevision = context.session.revision
                    true
                }
                GemProcessingAction.SET_PROCESSING_VIEW -> {
                    context.container.processingViewOpen = requireNotNull(request.enabled)
                    true
                }
                GemProcessingAction.SET_BACKPACK_MATERIALS -> updateSettings(context, requireNotNull(request.enabled))
                GemProcessingAction.PROCESS_ONE, GemProcessingAction.PROCESS_MANY -> process(context, player, request)
            }
        } ?: false

    // 権限と現物の不一致は操作対象を解決する前に拒否する。
    @Suppress("ReturnCount")
    private fun currentContext(
        player: ServerPlayer,
        request: GemProcessingRequest,
    ): Context? {
        if (!request.hasValidShape() || Thread.currentThread().threadGroup != SidedThreadGroups.SERVER) return null
        val menu = player.containerMenu as? StorageContainerMenuBase<*> ?: return null
        val container = menu.upgradeContainers[request.upgradeSlot] as? GemCaseContainer ?: return null
        if (!menuMatches(menu, container, player, request)) return null
        val session = container.sessionFor(menu)
        return if (session.storageId != null && session.id == request.sessionId && revisionMatches(session, request)) {
            Context(menu, container, session)
        } else {
            null
        }
    }

    private fun menuMatches(
        menu: StorageContainerMenuBase<*>,
        container: GemCaseContainer,
        player: ServerPlayer,
        request: GemProcessingRequest,
    ): Boolean =
        menu.containerId == request.menuId && menu.stillValid(player) && processingContainerMatches(container, menu)

    private fun processingContainerMatches(
        container: GemCaseContainer,
        menu: StorageContainerMenuBase<*>,
    ): Boolean = container.isCurrent(menu) && container.upgradeWrapper.supportsProcessing

    // 表示状態だけは保存を変えず、非同期更新後も閉じられるようrevision照合を省く。
    private fun revisionMatches(
        session: GemMenuSession,
        request: GemProcessingRequest,
    ): Boolean = request.action == GemProcessingAction.SET_PROCESSING_VIEW || session.revision == request.revision

    private fun updateSettings(
        context: Context,
        enabled: Boolean,
    ): Boolean =
        (context.container.upgradeWrapper.processingSettingsSnapshot() as? GemProcessingSettingsState.Ready)?.let {
            it.useBackpackMaterials != enabled &&
                context.container.upgradeWrapper.replaceProcessingSettings(it.useBackpackMaterials, enabled)
        } ?: false

    private fun process(
        context: Context,
        player: ServerPlayer,
        request: GemProcessingRequest,
    ): Boolean {
        val output = requireNotNull(request.output)
        val previewed =
            context.session.processingOutput == output &&
                context.session.previewRevision == context.session.revision
        val limit = if (request.action == GemProcessingAction.PROCESS_MANY) MAX_PROCESSING else 1
        return previewed &&
            GemProcessingPlanner.preview(context.menu.storageWrapper, player.serverLevel(), output).reason ==
            GemProcessingReason.READY &&
            GemProcessingEngine.process(context.menu.storageWrapper, player.serverLevel(), output, limit) > 0
    }
}
