package org.mocoserver.sophisticatedgemcase.client

import net.minecraft.client.Minecraft
import net.neoforged.neoforge.network.PacketDistributor
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMaterialSlot
import org.mocoserver.sophisticatedgemcase.network.GemPage
import org.mocoserver.sophisticatedgemcase.network.GemPageAccumulator
import org.mocoserver.sophisticatedgemcase.network.GemProcessingAction
import org.mocoserver.sophisticatedgemcase.network.GemProcessingPreviewPayload
import org.mocoserver.sophisticatedgemcase.network.GemProcessingRequest
import org.mocoserver.sophisticatedgemcase.network.GemProcessingSync
import org.mocoserver.sophisticatedgemcase.network.GemSettingsPageAccumulator
import java.lang.ref.WeakReference
import java.util.UUID

/** 加工表示を開いたmenuだけへ束縛し、server世代の違う応答を破棄する。 */
internal object GemProcessingCache {
    private var menuReference: WeakReference<StorageContainerMenuBase<*>>? = null
    private val selections = mutableMapOf<Int, GemKey>()
    private val previews = mutableMapOf<Int, GemProcessingPreviewPayload>()

    fun selectedOutput(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): GemKey? {
        bind(menu)
        return selections[slot]
    }

    fun selectOutput(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        output: GemKey,
    ) {
        bind(menu)
        if (selections[slot] != output) {
            selections[slot] = output
            previews.remove(slot)
            GemProcessingRequestCache.forget(menu, slot)
        }
        GemProcessingRequestCache.requestPreview(menu, slot, previews[slot])
    }

    fun preview(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): GemProcessingPreviewPayload? {
        bind(menu)
        GemProcessingRequestCache.requestPreview(menu, slot, previews[slot])
        val payload = previews[slot] ?: return null
        val counts = GemPageCache.forSlot(menu, slot)
        val settings = GemSettingsCache.forSlot(menu, slot)
        return payload.takeIf { matchesCurrentPreview(it, counts, settings, slot, menu.containerId) }
    }

    fun receive(payload: GemProcessingPreviewPayload) {
        val menu = currentMenu(payload) ?: return
        bind(menu)
        val counts = GemPageCache.forSlot(menu, payload.upgradeSlot)
        val settings = GemSettingsCache.forSlot(menu, payload.upgradeSlot)
        if (!matchesCurrentPreview(payload, counts, settings, payload.upgradeSlot, menu.containerId)) return
        previews[payload.upgradeSlot] = payload
        GemProcessingRequestCache.forget(menu, payload.upgradeSlot)
        updateMaterialSlots(menu, payload.upgradeSlot)
    }

    fun updateMaterialSlots(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ) {
        bind(menu)
        val counts = GemPageCache.forSlot(menu, slot)
        val settings = GemSettingsCache.forSlot(menu, slot)
        val payload = preview(menu, slot)
        val canEdit = GemProcessingSync.canEditMaterials(counts, settings, payload)
        val container = menu.upgradeContainers[slot] as? GemCaseContainer ?: return
        container.slots.filterIsInstance<GemMaterialSlot>().forEach { it.uiEditable = canEdit }
    }

    fun canSetBackpackMaterials(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): Boolean {
        val counts = GemPageCache.forSlot(menu, slot)
        val settings = GemSettingsCache.forSlot(menu, slot)
        return GemProcessingSync.canSetBackpackMaterials(counts, settings, preview(menu, slot))
    }

    fun canProcess(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): Boolean {
        val counts = GemPageCache.forSlot(menu, slot)
        val settings = GemSettingsCache.forSlot(menu, slot)
        return GemProcessingSync.canProcess(counts, settings, preview(menu, slot), selections[slot])
    }

    fun sendAction(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        action: GemProcessingAction,
        enabled: Boolean? = null,
    ) = GemProcessingRequestCache.sendAction(menu, slot, action, enabled)

    private fun matchesCurrentPreview(
        payload: GemProcessingPreviewPayload,
        counts: GemPageAccumulator,
        settings: GemSettingsPageAccumulator,
        slot: Int,
        menuId: Int,
    ): Boolean {
        val correctTarget = payload.menuId == menuId && payload.upgradeSlot == slot
        val matchingOutput = payload.preview.output == selections[slot]
        val sameGeneration = payload.sessionId == counts.sessionId && payload.revision == counts.revision
        return correctTarget &&
            matchingOutput &&
            sameGeneration &&
            GemProcessingSync.canRequestPreview(counts, settings, selections[slot])
    }

    private fun bind(menu: StorageContainerMenuBase<*>) {
        if (menuReference?.get() !== menu) {
            menuReference = WeakReference(menu)
            selections.clear()
            previews.clear()
            GemProcessingRequestCache.clear()
        }
    }
}

private fun currentMenu(payload: GemProcessingPreviewPayload): StorageContainerMenuBase<*>? =
    (Minecraft.getInstance().player?.containerMenu as? StorageContainerMenuBase<*>)?.takeIf {
        it.containerId == payload.menuId &&
            it.upgradeContainers[payload.upgradeSlot] is GemCaseContainer
    }

/** selectionに対応するpreview要求を一回だけ送り、古いcacheへ操作を適用しない。 */
internal object GemProcessingRequestCache {
    private var menuReference: WeakReference<StorageContainerMenuBase<*>>? = null
    private val requested = mutableMapOf<Int, PendingPreviewRequest>()

    private data class PendingPreviewRequest(
        val key: PreviewRequestKey,
        val sentAtNanos: Long,
    )

    internal data class PreviewRequestKey(
        val sessionId: UUID,
        val revision: Long,
        val output: GemKey,
    )

    fun clear() {
        requested.clear()
        menuReference = null
    }

    fun forget(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ) {
        bind(menu)
        requested.remove(slot)
    }

    fun requestPreview(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        cachedPreview: GemProcessingPreviewPayload?,
    ) {
        val key = previewRequestKey(menu, slot) ?: return
        val cached = cachedPreviewMatches(cachedPreview, menu.containerId, slot, key)
        val now = System.nanoTime()
        val pending = requested[slot]
        val coolingDown = pending?.key == key && now - pending.sentAtNanos < PREVIEW_RETRY_DELAY_NANOS
        if (cached || coolingDown) return
        requested[slot] = PendingPreviewRequest(key, now)
        PacketDistributor.sendToServer(request(menu, slot, key, GemProcessingAction.REQUEST_PREVIEW))
    }

    private fun cachedPreviewMatches(
        payload: GemProcessingPreviewPayload?,
        menuId: Int,
        slot: Int,
        key: PreviewRequestKey,
    ): Boolean =
        payload?.menuId == menuId &&
            payload.upgradeSlot == slot &&
            payload.sessionId == key.sessionId &&
            payload.revision == key.revision &&
            payload.preview.output == key.output

    fun sendAction(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        action: GemProcessingAction,
        enabled: Boolean?,
    ) {
        val request = buildActionRequest(menu, slot, action, enabled) ?: return
        PacketDistributor.sendToServer(request)
    }

    private fun previewRequestKey(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): PreviewRequestKey? {
        bind(menu)
        val output = GemProcessingCache.selectedOutput(menu, slot) ?: return null
        val counts = GemPageCache.forSlot(menu, slot)
        val settings = GemSettingsCache.forSlot(menu, slot)
        val sessionId = counts.sessionId
        return if (sessionId != null && GemProcessingSync.canRequestPreview(counts, settings, output)) {
            PreviewRequestKey(sessionId, counts.revision, output)
        } else {
            null
        }
    }

    private fun buildActionRequest(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        action: GemProcessingAction,
        enabled: Boolean?,
    ): GemProcessingRequest? {
        val counts = GemPageCache.forSlot(menu, slot)
        val settings = GemSettingsCache.forSlot(menu, slot)
        val sessionId = counts.sessionId
        val allowed = actionAllowed(menu, slot, action, counts, settings)
        return if (sessionId != null && allowed) {
            GemProcessingRequest(
                menu.containerId,
                slot,
                sessionId,
                counts.revision,
                action,
                if (action == GemProcessingAction.SET_BACKPACK_MATERIALS) {
                    null
                } else {
                    GemProcessingCache.selectedOutput(menu, slot)
                },
                enabled,
            )
        } else {
            null
        }
    }

    private fun actionAllowed(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        action: GemProcessingAction,
        counts: GemPageAccumulator,
        settings: GemSettingsPageAccumulator,
    ): Boolean =
        when (action) {
            GemProcessingAction.SET_BACKPACK_MATERIALS ->
                GemProcessingSync.canSetBackpackMaterials(counts, settings, GemProcessingCache.preview(menu, slot))
            GemProcessingAction.PROCESS_ONE,
            GemProcessingAction.PROCESS_MANY,
            -> GemProcessingCache.canProcess(menu, slot)
            else ->
                GemProcessingSync.canRequestPreview(
                    counts,
                    settings,
                    GemProcessingCache.selectedOutput(menu, slot),
                )
        }

    private fun request(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        key: PreviewRequestKey,
        action: GemProcessingAction,
    ): GemProcessingRequest =
        GemProcessingRequest(menu.containerId, slot, key.sessionId, key.revision, action, key.output, null)

    private fun bind(menu: StorageContainerMenuBase<*>) {
        if (menuReference?.get() !== menu) {
            menuReference = WeakReference(menu)
            requested.clear()
        }
    }

    private const val PREVIEW_RETRY_DELAY_NANOS = 1_000_000_000L
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
