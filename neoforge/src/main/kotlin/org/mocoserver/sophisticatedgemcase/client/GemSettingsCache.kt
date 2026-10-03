package org.mocoserver.sophisticatedgemcase.client

import net.minecraft.client.Minecraft
import net.neoforged.neoforge.network.PacketDistributor
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.core.GemFilterMode
import org.mocoserver.sophisticatedgemcase.core.GemTransferDirection
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.network.GemSettingsAction
import org.mocoserver.sophisticatedgemcase.network.GemSettingsPage
import org.mocoserver.sophisticatedgemcase.network.GemSettingsPageAccumulator
import org.mocoserver.sophisticatedgemcase.network.GemSettingsRequest
import org.mocoserver.sophisticatedgemcase.network.GemSettingsSync
import java.lang.ref.WeakReference

/** 現在開いたmenuだけを保持し、両一覧が同じ世代で揃うまで設定編集を止める。 */
internal object GemSettingsCache {
    private var menuReference: WeakReference<StorageContainerMenuBase<*>>? = null
    private val caches = mutableMapOf<Int, GemSettingsPageAccumulator>()

    fun forSlot(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): GemSettingsPageAccumulator {
        if (menuReference?.get() !== menu) {
            caches.clear()
            menuReference = WeakReference(menu)
        }
        return caches.getOrPut(slot) { GemSettingsPageAccumulator() }
    }

    fun canEdit(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): Boolean {
        val counts = GemPageCache.forSlot(menu, slot)
        val settings = forSlot(menu, slot)
        return GemSettingsSync.canEdit(counts, settings)
    }

    fun receive(page: GemSettingsPage) {
        val menu = Minecraft.getInstance().player?.containerMenu as? StorageContainerMenuBase<*>
        val eligible =
            menu != null &&
                menu.containerId == page.menuId &&
                menu.upgradeContainers[page.upgradeSlot] is GemCaseContainer
        if (eligible) {
            val current = requireNotNull(menu)
            val counts = GemPageCache.forSlot(current, page.upgradeSlot)
            if (counts.sessionId != null &&
                (counts.sessionId != page.sessionId || counts.revision != page.revision)
            ) {
                return
            }
            val cache = forSlot(current, page.upgradeSlot)
            if (cache.accept(page)) {
                cache.nextPage()?.let { index ->
                    send(current, page.upgradeSlot, GemSettingsAction.REQUEST_PAGE, page = index)
                }
            }
        }
    }

    // actionごとのnullable fieldはwire requestの型付き契約をそのまま表す。
    // アクション種別に応じた不正な組み合わせは送信側でもhasValidShapeで拒否するため、個別command型へ分割しない。
    @Suppress("LongParameterList")
    fun send(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        action: GemSettingsAction,
        direction: GemTransferDirection = GemTransferDirection.DEPOSIT,
        enabled: Boolean? = null,
        mode: GemFilterMode? = null,
        value: String? = null,
        selected: Boolean? = null,
        page: Int = 0,
    ) {
        val settings = forSlot(menu, slot)
        val isPageRequest = action == GemSettingsAction.REQUEST_PAGE
        if ((!isPageRequest && !canEdit(menu, slot)) || settings.sessionId == null) return
        PacketDistributor.sendToServer(
            GemSettingsRequest(
                menu.containerId,
                slot,
                requireNotNull(settings.sessionId),
                settings.revision,
                action,
                direction,
                enabled,
                mode,
                value,
                selected,
                page,
            ),
        )
    }
}
