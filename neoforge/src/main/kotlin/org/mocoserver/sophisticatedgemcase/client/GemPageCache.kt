package org.mocoserver.sophisticatedgemcase.client

import net.minecraft.client.Minecraft
import net.neoforged.neoforge.network.PacketDistributor
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.network.GemAction
import org.mocoserver.sophisticatedgemcase.network.GemPage
import org.mocoserver.sophisticatedgemcase.network.GemPageAccumulator
import org.mocoserver.sophisticatedgemcase.network.GemRequest
import java.lang.ref.WeakReference

/** 現在開いたmenuだけの表示cache。menuを強参照してワールドを保持し続けない。 */
internal object GemPageCache {
    private var menuReference: WeakReference<StorageContainerMenuBase<*>>? = null
    private val caches = mutableMapOf<Int, GemPageAccumulator>()

    fun forSlot(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
    ): GemPageAccumulator {
        if (menuReference?.get() !== menu) {
            caches.clear()
            menuReference = WeakReference(menu)
        }
        return caches.getOrPut(slot) { GemPageAccumulator() }
    }

    fun receive(page: GemPage) {
        val menu = Minecraft.getInstance().player?.containerMenu as? StorageContainerMenuBase<*>
        val eligible =
            menu != null &&
                menu.containerId == page.menuId &&
                menu.upgradeContainers[page.upgradeSlot] is GemCaseContainer
        if (eligible) {
            val current = requireNotNull(menu)
            val cache = forSlot(current, page.upgradeSlot)
            if (cache.accept(page)) {
                cache.nextPage()?.let { index -> send(current, page.upgradeSlot, GemAction.REQUEST_PAGE, null, index) }
            }
        }
    }

    fun send(
        menu: StorageContainerMenuBase<*>,
        slot: Int,
        action: GemAction,
        key: GemKey? = null,
        page: Int = 0,
    ) {
        val cache = forSlot(menu, slot)
        val session = cache.sessionId
        val ready = action == GemAction.REQUEST_PAGE || (cache.complete && !cache.locked)
        if (session != null && ready) {
            PacketDistributor.sendToServer(
                GemRequest(menu.containerId, slot, session, cache.revision, action, key, page),
            )
        }
    }
}
