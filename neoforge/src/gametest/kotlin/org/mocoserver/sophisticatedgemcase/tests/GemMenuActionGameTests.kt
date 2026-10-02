package org.mocoserver.sophisticatedgemcase.tests

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMenuActions
import org.mocoserver.sophisticatedgemcase.network.GemAction
import org.mocoserver.sophisticatedgemcase.network.GemPages
import org.mocoserver.sophisticatedgemcase.network.GemRequest
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.StandardGem

/** server menuと世代を検証し、再送・満杯・閉鎖で保存や出力を変えない。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemMenuActionGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun validatesRevisionAndDestinationBeforeWithdrawal(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val key = requireNotNull(StandardGem.keyOf(GemMenuFixtures.gem(1)))
        helper.assertTrue(
            wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(key to 70))),
            "Cannot prepare counts",
        )
        try {
            val container = menu.upgradeContainers.getValue(0) as GemCaseContainer
            val session = container.sessionFor(menu)
            val request = GemRequest(menu.containerId, 0, session.id, session.revision, GemAction.WITHDRAW_ONE, key, 0)
            helper.assertTrue(GemMenuActions.perform(player, request), "Withdraw one failed")
            helper.assertTrue(menu.carried.count == 1, "Wrong cursor count")
            helper.assertTrue(!GemMenuActions.perform(player, request), "Replay duplicated output")
            menu.carried = ItemStack(Items.STONE, 64)
            val blocked = request.copy(revision = session.revision, action = GemAction.WITHDRAW_STACK)
            helper.assertTrue(!GemMenuActions.perform(player, blocked), "Full incompatible cursor accepted output")
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts.count(key) == 69,
                "Rejected withdrawal consumed gem",
            )
            menu.carried = ItemStack.EMPTY
            val stack = blocked.copy(revision = session.revision)
            helper.assertTrue(GemMenuActions.perform(player, stack), "Withdraw stack failed")
            helper.assertTrue(menu.carried.count == minOf(64, menu.carried.maxStackSize), "Wrong full stack size")
            player.containerMenu = player.inventoryMenu
            helper.assertTrue(
                !GemMenuActions.perform(player, stack.copy(revision = session.revision)),
                "Closed menu accepted action",
            )
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun boundsPagesAndRejectsMissingKeys(helper: GameTestHelper) {
        val counts = GemCounts.of((0 until 65).associate { GemKey("missing:g$it", "future") to 1 })
        val pages = GemPages.partition(GemStorageState.Ready(counts))
        helper.assertTrue(pages.size == 2 && pages.first().size == 64, "65 entries must be paged")
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        try {
            val container = menu.upgradeContainers.getValue(0) as GemCaseContainer
            val session = container.sessionFor(menu)
            val invalid =
                GemRequest(
                    menu.containerId,
                    0,
                    session.id,
                    session.revision,
                    GemAction.WITHDRAW_ONE,
                    GemKey("missing:no", "cracked"),
                    0,
                )
            helper.assertTrue(!GemMenuActions.perform(player, invalid), "Unknown key generated output")
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts.entries().isEmpty(),
                "Invalid action changed storage",
            )
            helper.assertTrue(
                !GemMenuActions.perform(player, invalid.copy(action = GemAction.REQUEST_PAGE, key = null, page = -1)),
                "Negative page allowed",
            )
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun withdrawsToPartialPlayerSpaceAndLeavesFullInventoryUnchanged(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val key = requireNotNull(StandardGem.keyOf(GemMenuFixtures.gem(1)))
        helper.assertTrue(
            wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(key to 70))),
            "Cannot prepare counts",
        )
        try {
            repeat(36) { player.inventory.setItem(it, ItemStack(Items.STONE, 64)) }
            player.inventory.setItem(0, GemMenuFixtures.gem(63))
            val partial = request(menu, GemAction.WITHDRAW_TO_PLAYER, key)
            helper.assertTrue(GemMenuActions.perform(player, partial), "Partial player withdrawal failed")
            helper.assertTrue(player.inventory.getItem(0).count == 64, "Partial destination was not filled")
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts.count(key) == 69,
                "Withdrawal consumed more than the available destination space",
            )

            repeat(36) { player.inventory.setItem(it, ItemStack(Items.STONE, 64)) }
            val before = (wrapper.snapshot() as GemStorageState.Ready).counts.count(key)
            val full = request(menu, GemAction.WITHDRAW_TO_PLAYER, key)
            helper.assertTrue(!GemMenuActions.perform(player, full), "Full inventory accepted output")
            helper.assertTrue(
                player.inventory.items.all { it.`is`(Items.STONE) && it.count == 64 },
                "Full inventory changed",
            )
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts.count(key) == before,
                "Rejected withdrawal consumed gems",
            )
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsActionAfterUpgradeStackReplacement(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val key = requireNotNull(StandardGem.keyOf(GemMenuFixtures.gem(1)))
        helper.assertTrue(
            wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(key to 7))),
            "Cannot prepare counts",
        )
        try {
            val stale = request(menu, GemAction.WITHDRAW_ONE, key)
            menu.storageWrapper.upgradeHandler.setStackInSlot(0, ItemStack(Items.DIAMOND))
            helper.assertTrue(!GemMenuActions.perform(player, stale), "Request for replaced upgrade was accepted")
            helper.assertTrue(menu.carried.isEmpty, "Stale request changed cursor")
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts.count(key) == 7,
                "Stale request changed old upgrade storage",
            )
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun insertsCursorAndMovesNormalStorageThroughMenuActions(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val gem = GemMenuFixtures.gem(5)
        val key = requireNotNull(StandardGem.keyOf(gem))
        try {
            menu.carried = gem.copy()
            helper.assertTrue(
                GemMenuActions.perform(player, request(menu, GemAction.INSERT_CURSOR)),
                "Cursor insertion failed",
            )
            helper.assertTrue(menu.carried.isEmpty, "Cursor remainder was not updated")
            val inventory = menu.storageWrapper.inventoryHandler
            inventory.setStackInSlot(0, gem.copyWithCount(9))
            helper.assertTrue(
                GemMenuActions.perform(player, request(menu, GemAction.MOVE_NORMAL)),
                "Bulk movement failed",
            )
            helper.assertTrue(inventory.getStackInSlot(0).isEmpty, "Bulk movement left source items")
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts.count(key) == 14,
                "Menu actions lost or duplicated gems",
            )
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    private fun request(
        menu: net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer,
        action: GemAction,
        key: GemKey? = null,
    ): GemRequest {
        val container = menu.upgradeContainers.getValue(0) as GemCaseContainer
        val session = container.sessionFor(menu)
        return GemRequest(menu.containerId, 0, session.id, session.revision, action, key, 0)
    }
}
