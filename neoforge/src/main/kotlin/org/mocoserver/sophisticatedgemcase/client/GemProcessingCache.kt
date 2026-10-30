package org.mocoserver.sophisticatedgemcase.client

import net.minecraft.client.Minecraft
import net.neoforged.neoforge.network.PacketDistributor
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMaterialSlot
import org.mocoserver.sophisticatedgemcase.network.GemPage
import org.mocoserver.sophisticatedgemcase.network.GemProcessingAction
import org.mocoserver.sophisticatedgemcase.network.GemProcessingOverviewPayload
import org.mocoserver.sophisticatedgemcase.network.GemProcessingPreviewPayload
import org.mocoserver.sophisticatedgemcase.network.GemProcessingRequest
import org.mocoserver.sophisticatedgemcase.network.GemProcessingSync
import org.mocoserver.sophisticatedgemcase.network.GemProcessingTarget
import java.lang.ref.WeakReference
import java.util.UUID

/** 一つの種類の全強化先と素材状態を共有し、サーバーが確定した世代だけをUIへ渡す。 */
internal object GemProcessingCache {
    private val state = GemProcessingClientState()

    fun selectGem(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        gemId: String?,
    ) = state.selectGem(menu, slot, gemId)

    fun preview(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        output: GemKey,
    ): GemProcessingPreviewPayload? {
        val batch = state.overview(menu, slot) ?: return null
        return batch.previews.firstOrNull { it.output == output }?.let {
            GemProcessingPreviewPayload(
                batch.menuId,
                batch.upgradeSlot,
                batch.sessionId,
                batch.revision,
                batch.recipeEpoch,
                it,
            )
        }
    }

    fun receive(payload: GemProcessingOverviewPayload) {
        state.receive(payload)?.let { updateMaterialSlots(it, payload.upgradeSlot) }
    }

    fun updateMaterialSlots(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ) {
        val canEdit = canEditMaterials(menu, slot)
        val container = menu.upgradeContainers[slot] as? GemCaseContainer ?: return
        container.slots.filterIsInstance<GemMaterialSlot>().forEach { it.uiEditable = canEdit }
    }

    fun canEditMaterials(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): Boolean = state.overview(menu, slot)?.materialsLocked == false

    fun useBackpackMaterials(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): Boolean? = state.overview(menu, slot)?.takeUnless { it.settingsLocked }?.useBackpackMaterials

    fun canSetBackpackMaterials(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): Boolean = state.overview(menu, slot)?.settingsLocked == false

    fun canProcess(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        output: GemKey,
    ): Boolean =
        GemProcessingSync.canProcess(
            GemPageCache.forSlot(menu, slot),
            GemSettingsCache.forSlot(menu, slot),
            preview(menu, slot, output),
            output,
        )

    fun process(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        output: GemKey,
        many: Boolean,
    ) {
        if (!canProcess(menu, slot, output)) return
        val action = if (many) GemProcessingAction.PROCESS_MANY else GemProcessingAction.PROCESS_ONE
        state.send(menu, slot, action, output = output)
    }

    fun sendAction(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        action: GemProcessingAction,
        enabled: Boolean? = null,
    ) {
        if (action == GemProcessingAction.SET_BACKPACK_MATERIALS &&
            enabled != null &&
            canSetBackpackMaterials(menu, slot)
        ) {
            state.send(menu, slot, action, enabled = enabled)
        }
    }
}

/** menuとの同一性、選択連番、要求retryをまとめて所有する。 */
private class GemProcessingClientState {
    private var menuReference: WeakReference<StorageContainerMenuBase<*>>? = null
    private val selections = mutableMapOf<Int, String?>()
    private val selectionIds = mutableMapOf<Int, Long>()
    private val previews = mutableMapOf<Int, GemProcessingOverviewPayload>()
    private val requested = mutableMapOf<Int, PendingRequest>()

    private data class RequestKey(
        val session: UUID,
        val revision: Long,
        val gem: String?,
        val selection: Long,
    )

    private data class PendingRequest(
        val key: RequestKey,
        val sentAt: Long,
    )

    fun selectGem(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        gemId: String?,
    ) {
        bind(menu)
        if (!selectionIds.containsKey(slot) || selections[slot] != gemId) {
            selections[slot] = gemId
            selectionIds[slot] = (selectionIds[slot] ?: 0) + 1
            previews.remove(slot)
            requested.remove(slot)
        }
        overview(menu, slot)
    }

    // 閉じたmenu・不完全なpage・旧種類・旧recipe応答を保存前に区別して拒否する。
    @Suppress("ReturnCount")
    fun receive(payload: GemProcessingOverviewPayload): StorageContainerMenuBase<*>? {
        val menu = Minecraft.getInstance().player?.containerMenu as? StorageContainerMenuBase<*> ?: return null
        bind(menu)
        val slot = payload.upgradeSlot
        val key = requestKey(menu, slot) ?: return null
        if (!matches(payload, menu, slot, key)) return null
        val previous = previews[slot]
        if (previous?.sessionId == payload.sessionId && previous.recipeEpoch > payload.recipeEpoch) return null
        previews[slot] = payload
        requested.remove(slot)
        return menu
    }

    fun overview(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): GemProcessingOverviewPayload? {
        bind(menu)
        val key = requestKey(menu, slot) ?: return null
        val cached = previews[slot]?.takeIf { matches(it, menu, slot, key) }
        if (cached == null) requestOverview(menu, slot, key)
        return cached
    }

    private fun requestKey(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): RequestKey? {
        val container = menu.upgradeContainers[slot] as? GemCaseContainer
        val counts = GemPageCache.forSlot(menu, slot)
        val settings = GemSettingsCache.forSlot(menu, slot)
        val ready =
            container?.upgradeWrapper?.supportsProcessing == true && GemProcessingSync.sameGeneration(counts, settings)
        return if (ready) {
            RequestKey(requireNotNull(counts.sessionId), counts.revision, selections[slot], selectionIds[slot] ?: 0)
        } else {
            null
        }
    }

    private fun matches(
        payload: GemProcessingOverviewPayload,
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        key: RequestKey,
    ): Boolean =
        payload.matchesSelection(
            GemProcessingTarget(menu.containerId, slot),
            key.session,
            key.revision,
            key.gem,
            key.selection,
        )

    private fun requestOverview(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        key: RequestKey,
    ) {
        val now = System.nanoTime()
        val previous = requested[slot]
        if (previous?.key == key && now - previous.sentAt < PREVIEW_RETRY_DELAY_NANOS) return
        requested[slot] = PendingRequest(key, now)
        PacketDistributor.sendToServer(
            GemProcessingRequest(
                menu.containerId,
                slot,
                key.session,
                key.revision,
                GemProcessingAction.REQUEST_GEM_PREVIEWS,
                null,
                null,
                key.gem,
                key.selection,
            ),
        )
    }

    fun send(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        action: GemProcessingAction,
        output: GemKey? = null,
        enabled: Boolean? = null,
    ) {
        val key = requestKey(menu, slot) ?: return
        PacketDistributor.sendToServer(
            GemProcessingRequest(
                menu.containerId,
                slot,
                key.session,
                key.revision,
                action,
                output,
                enabled,
                selectionId = key.selection,
            ),
        )
    }

    private fun bind(menu: StorageContainerMenuBase<*>) {
        if (menuReference?.get() !== menu) {
            menuReference = WeakReference(menu)
            selections.clear()
            selectionIds.clear()
            previews.clear()
            requested.clear()
        }
    }

    private companion object {
        const val PREVIEW_RETRY_DELAY_NANOS = 1_000_000_000L
    }
}

/** view open stateは同期前に保留し、UUID取得後の最初のcount pageで送る。 */
internal object GemProcessingViewCache {
    private var menuReference: WeakReference<StorageContainerMenuBase<*>>? = null
    private val desiredView = mutableMapOf<Int, Boolean>()
    private val sentView = mutableMapOf<Int, SentView>()

    private data class SentView(
        val sessionId: UUID,
        val opened: Boolean,
    )

    fun setProcessingView(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        opened: Boolean,
    ) {
        bind(menu)
        desiredView[slot] = opened
        sendProcessingView(menu, slot)
    }

    fun onPageReceived(page: GemPage) {
        val menu = Minecraft.getInstance().player?.containerMenu as? StorageContainerMenuBase<*> ?: return
        if (menu.containerId != page.menuId || menu.upgradeContainers[page.upgradeSlot] !is GemCaseContainer) return
        bind(menu)
        sendProcessingView(menu, page.upgradeSlot)
    }

    private fun sendProcessingView(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ) {
        val request = buildProcessingViewRequest(menu, slot) ?: return
        PacketDistributor.sendToServer(request)
        sentView[slot] = SentView(request.sessionId, request.enabled == true)
    }

    private fun buildProcessingViewRequest(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): GemProcessingRequest? {
        val counts = GemPageCache.forSlot(menu, slot)
        val sessionId = counts.sessionId
        val opened = desiredView[slot] ?: false
        val previous = sentView[slot]
        val needsUpdate = sessionId != null && counts.revision >= 0 && requiresViewUpdate(previous, sessionId, opened)
        return sessionId?.takeIf { needsUpdate }?.let {
            GemProcessingRequest(
                menu.containerId,
                slot,
                it,
                counts.revision,
                GemProcessingAction.SET_PROCESSING_VIEW,
                null,
                opened,
            )
        }
    }

    private fun requiresViewUpdate(
        previous: SentView?,
        sessionId: UUID,
        opened: Boolean,
    ): Boolean {
        val sameSentState = previous?.sessionId == sessionId && previous.opened == opened
        val closingOpenSession = !opened && previous?.sessionId == sessionId && previous.opened
        return !sameSentState && (opened || closingOpenSession)
    }

    private fun bind(menu: StorageContainerMenuBase<*>) {
        if (menuReference?.get() !== menu) {
            menuReference = WeakReference(menu)
            desiredView.clear()
            sentView.clear()
        }
    }
}
