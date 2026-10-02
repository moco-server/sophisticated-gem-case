package org.mocoserver.sophisticatedgemcase.tests

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMenuActions
import org.mocoserver.sophisticatedgemcase.network.GemAction
import org.mocoserver.sophisticatedgemcase.network.GemRequest
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import java.util.UUID

/** バッグ識別と距離の拒否、未解決entryの無変更を実menuで検証する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemSessionBoundaryGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsChangedBagOutOfRangeAndUnresolvedEntry(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val unknown = GemKey("missing:retained", "future")
        val counts = GemCounts.of(mapOf(unknown to 7))
        helper.assertTrue(wrapper.replace(GemCounts.of(emptyMap()), counts), "Cannot prepare unknown entry")
        try {
            val container = menu.upgradeContainers.getValue(0) as GemCaseContainer
            val session = container.sessionFor(menu)
            val original =
                GemRequest(menu.containerId, 0, session.id, session.revision, GemAction.WITHDRAW_ONE, unknown, 0)
            helper.assertTrue(!GemMenuActions.perform(player, original), "Unresolved entry produced output")
            (menu.storageWrapper as net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper)
                .setContentsUuid(UUID.randomUUID())
            helper.assertTrue(!GemMenuActions.perform(player, original), "Changed bag UUID accepted old request")
            val current = container.sessionFor(menu)
            player.setPos(player.x + 100.0, player.y, player.z)
            helper.assertTrue(
                !GemMenuActions.perform(player, original.copy(sessionId = current.id, revision = current.revision)),
                "Out-of-range player accepted action",
            )
            helper.assertTrue(menu.carried.isEmpty, "Rejected action changed cursor")
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts == counts,
                "Rejected action lost raw counts",
            )
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }
}
