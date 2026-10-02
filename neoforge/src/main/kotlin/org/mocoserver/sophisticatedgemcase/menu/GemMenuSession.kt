package org.mocoserver.sophisticatedgemcase.menu

import net.minecraft.world.item.ItemStack
import org.mocoserver.sophisticatedgemcase.network.GemPage
import org.mocoserver.sophisticatedgemcase.network.GemPages
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper
import java.util.UUID

/** menuの生存期間だけ持つ世代。upgrade差し替えでは別sessionを発行する。 */
internal class GemMenuSession(
    val storageId: UUID?,
    val upgradeStack: ItemStack,
    initial: GemStorageState,
) {
    val id: UUID = UUID.randomUUID()
    var revision: Long = 0
        private set
    private var state = initial
    private var pages = GemPages.partition(initial)
    var sentRevision: Long = -1
    private var requestTick = Long.MIN_VALUE
    private var requests = 0

    fun refresh(wrapper: GemCaseUpgradeWrapper) {
        val current = wrapper.snapshot()
        if (state != current) {
            state = current
            revision++
            pages = GemPages.partition(current)
        }
    }

    fun matchesStorage(
        storageId: UUID?,
        stack: ItemStack,
    ): Boolean = this.storageId == storageId && upgradeStack === stack

    fun validPage(index: Int): Boolean = index in pages.indices

    fun page(
        menuId: Int,
        slot: Int,
        index: Int,
    ): GemPage = GemPage(menuId, slot, id, revision, index, pages.size, pages[index], state !is GemStorageState.Ready)

    fun allowRequest(tick: Long): Boolean {
        if (requestTick != tick) {
            requestTick = tick
            requests = 0
        }
        requests++
        return requests <= MAX_REQUESTS_PER_TICK
    }

    companion object {
        private const val MAX_REQUESTS_PER_TICK = 16
    }
}
