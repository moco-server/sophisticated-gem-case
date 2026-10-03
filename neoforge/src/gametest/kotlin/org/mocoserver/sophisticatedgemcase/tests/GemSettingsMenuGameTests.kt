package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemFilter
import org.mocoserver.sophisticatedgemcase.core.GemFilterMode
import org.mocoserver.sophisticatedgemcase.core.GemTransferDirection
import org.mocoserver.sophisticatedgemcase.core.GemTransferRule
import org.mocoserver.sophisticatedgemcase.core.GemTransferSettings
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMenuActions
import org.mocoserver.sophisticatedgemcase.menu.GemSettingsActions
import org.mocoserver.sophisticatedgemcase.network.GemAction
import org.mocoserver.sophisticatedgemcase.network.GemRequest
import org.mocoserver.sophisticatedgemcase.network.GemSettingsAction
import org.mocoserver.sophisticatedgemcase.network.GemSettingsRequest
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsState
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.StandardGem

/** 設定要求のmenu境界、世代検証、失敗時無変更を検査する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemSettingsMenuGameTests {
    private data class SettingsBaseline(
        val raw: CustomData?,
        val counts: GemCounts,
        val settings: GemSettingsState,
        val inventory: List<ItemStack>,
        val storageCursor: ItemStack,
        val playerCursor: ItemStack,
    )

    @JvmStatic
    @GameTest(template = "empty")
    fun settingsOnlyMutationAdvancesSharedRevisionAndRejectsReplay(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val key = requireNotNull(StandardGem.keyOf(GemMenuFixtures.gem(1)))
        helper.assertTrue(
            wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(key to 12))),
            "Cannot prepare counts",
        )
        try {
            val container = menu.upgradeContainers.getValue(0) as GemCaseContainer
            val session = container.sessionFor(menu)
            val tick = helper.level.gameTime
            repeat(16) {
                helper.assertTrue(session.allowRequest(tick), "Request quota rejected an in-limit request")
            }
            helper.assertTrue(!session.allowRequest(tick), "Request quota allowed a 17th request in one tick")
            helper.assertTrue(session.allowRequest(tick + 1), "Request quota did not recover on the next tick")
            val originalRevision = session.revision
            val beforeCounts = (wrapper.snapshot() as GemStorageState.Ready).counts
            val before = settingsRequest(menu.containerId, session, GemSettingsAction.SET_ENABLED, enabled = true)
            helper.assertTrue(GemSettingsActions.perform(player, before), "Setting change was rejected")
            val updatedSession = container.sessionFor(menu)
            val updatedSettings = wrapper.settingsSnapshot() as GemSettingsState.Ready
            helper.assertTrue(
                updatedSession.revision == originalRevision + 1,
                "Settings-only change did not advance revision",
            )
            helper.assertTrue(
                updatedSession.page(menu.containerId, 0, 0).revision ==
                    updatedSession.settingsPage(menu.containerId, 0, 0).revision,
                "Gem and settings pages do not share a revision",
            )
            helper.assertTrue(updatedSession.sentRevision == -1L, "Revision change did not schedule a fresh broadcast")
            helper.assertTrue(updatedSettings.settings.deposit.enabled, "Enabled flag was not stored")
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts == beforeCounts,
                "Settings edit changed Gem counts",
            )
            helper.assertTrue(!GemSettingsActions.perform(player, before), "Replayed setting request was accepted")
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts == beforeCounts,
                "Replay changed counts",
            )
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsStaleSessionAndMalformedActionShapeWithoutMutation(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        try {
            val container = menu.upgradeContainers.getValue(0) as GemCaseContainer
            val session = container.sessionFor(menu)
            val original = wrapper.settingsSnapshot()
            val stale = settingsRequest(menu.containerId, session, GemSettingsAction.SET_ENABLED, enabled = true)
            helper.assertTrue(
                !GemSettingsActions.perform(player, stale.copy(revision = stale.revision - 1)),
                "Stale revision accepted",
            )
            helper.assertTrue(
                !GemSettingsActions.perform(player, stale.copy(sessionId = java.util.UUID.randomUUID())),
                "Wrong session accepted",
            )
            helper.assertTrue(
                !GemSettingsActions.perform(player, stale.copy(enabled = true, mode = GemFilterMode.ALL)),
                "Extra field accepted for SET_ENABLED",
            )
            helper.assertTrue(wrapper.settingsSnapshot() == original, "Rejected requests changed settings")
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts.entries().isEmpty(),
                "Rejected requests changed counts",
            )
            helper.assertTrue(menu.carried.isEmpty, "Rejected request changed cursor")
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsClosedDistantForeignAndReplacedMenuWithoutMutation(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val key =
            requireNotNull(
                org.mocoserver.sophisticatedgemcase.storage.StandardGem
                    .keyOf(GemMenuFixtures.gem(4)),
            )
        val initialSettings =
            GemTransferSettings.DEFAULT.copy(
                restock = GemTransferRule(true, GemFilter.of(GemFilterMode.INCLUDE, setOf("future:kept"), emptySet())),
            )
        helper.assertTrue(
            wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(key to 4))),
            "Cannot prepare counts",
        )
        helper.assertTrue(
            wrapper.replaceSettings(GemTransferSettings.DEFAULT, initialSettings),
            "Cannot prepare settings",
        )
        menu.carried = ItemStack(Items.STONE, 3)
        val originalPosition = player.position()
        val baseline =
            SettingsBaseline(
                wrapper.upgradeStack.get(ModDataComponents.gemTransferSettings.get()),
                (wrapper.snapshot() as GemStorageState.Ready).counts,
                wrapper.settingsSnapshot(),
                player.inventory.items.map { it.copy() },
                menu.carried.copy(),
                player.inventoryMenu.carried.copy(),
            )
        try {
            val container = menu.upgradeContainers.getValue(0) as GemCaseContainer
            val session = container.sessionFor(menu)
            val request = settingsRequest(menu.containerId, session, GemSettingsAction.SET_ENABLED, enabled = true)
            player.containerMenu = player.inventoryMenu
            assertRejectedWithoutMutation(
                helper,
                GemSettingsActions.perform(player, request),
                player,
                menu,
                wrapper,
                baseline,
            )

            player.containerMenu = menu
            player.setPos(100.0, 100.0, 100.0)
            assertRejectedWithoutMutation(
                helper,
                GemSettingsActions.perform(player, request),
                player,
                menu,
                wrapper,
                baseline,
            )

            player.setPos(originalPosition.x, originalPosition.y, originalPosition.z)
            val foreignSession = request.copy(sessionId = java.util.UUID.randomUUID())
            assertRejectedWithoutMutation(
                helper,
                GemSettingsActions.perform(player, foreignSession),
                player,
                menu,
                wrapper,
                baseline,
            )

            menu.storageWrapper.upgradeHandler.setStackInSlot(0, ItemStack(Items.DIAMOND))
            assertRejectedWithoutMutation(
                helper,
                GemSettingsActions.perform(player, request),
                player,
                menu,
                wrapper,
                baseline,
            )
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsChangedBagUuidWithoutMutatingSavedSettings(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val counts =
            GemCounts.of(
                mapOf(
                    org.mocoserver.sophisticatedgemcase.core
                        .GemKey("missing:retained", "future") to 7,
                ),
            )
        helper.assertTrue(wrapper.replace(GemCounts.of(emptyMap()), counts), "Cannot prepare saved counts")
        val settings =
            GemTransferSettings.DEFAULT.copy(
                restock = GemTransferRule(true, GemFilter.of(GemFilterMode.ALL, emptySet(), emptySet())),
            )
        helper.assertTrue(
            wrapper.replaceSettings(GemTransferSettings.DEFAULT, settings),
            "Cannot prepare saved settings",
        )
        menu.carried = ItemStack(Items.STONE, 3)
        val baseline =
            SettingsBaseline(
                wrapper.upgradeStack.get(ModDataComponents.gemTransferSettings.get()),
                counts,
                wrapper.settingsSnapshot(),
                player.inventory.items.map { it.copy() },
                menu.carried.copy(),
                player.inventoryMenu.carried.copy(),
            )
        try {
            val container = menu.upgradeContainers.getValue(0) as GemCaseContainer
            val request =
                settingsRequest(
                    menu.containerId,
                    container.sessionFor(menu),
                    GemSettingsAction.SET_ENABLED,
                    enabled = true,
                )
            (menu.storageWrapper as net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper)
                .setContentsUuid(java.util.UUID.randomUUID())
            assertRejectedWithoutMutation(
                helper,
                GemSettingsActions.perform(player, request),
                player,
                menu,
                wrapper,
                baseline,
            )
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    private fun assertRejectedWithoutMutation(
        helper: GameTestHelper,
        accepted: Boolean,
        player: net.minecraft.server.level.ServerPlayer,
        menu: net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase<*>,
        wrapper: org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper,
        baseline: SettingsBaseline,
    ) {
        helper.assertTrue(!accepted, "Unauthorized settings request was accepted")
        helper.assertTrue(
            wrapper.upgradeStack.get(ModDataComponents.gemTransferSettings.get()) == baseline.raw,
            "Rejected request changed raw settings component",
        )
        helper.assertTrue(
            wrapper.snapshot() == GemStorageState.Ready(baseline.counts),
            "Rejected request changed counts",
        )
        helper.assertTrue(wrapper.settingsSnapshot() == baseline.settings, "Rejected request changed settings")
        helper.assertTrue(
            ItemStack.matches(menu.carried, baseline.storageCursor),
            "Rejected request changed storage cursor",
        )
        helper.assertTrue(
            ItemStack.matches(player.inventoryMenu.carried, baseline.playerCursor),
            "Rejected request changed player cursor",
        )
        helper.assertTrue(
            player.inventory.items.zip(baseline.inventory).all { (actual, expected) ->
                ItemStack.matches(actual, expected)
            },
            "Rejected request changed player inventory",
        )
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun updatesSingleFilterValueAndPreservesOtherDirection(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        try {
            val container = menu.upgradeContainers.getValue(0) as GemCaseContainer
            val session = container.sessionFor(menu)
            val initial =
                GemTransferSettings.DEFAULT.copy(
                    deposit = GemTransferRule(false, GemFilter.of(GemFilterMode.INCLUDE, emptySet(), emptySet())),
                    restock =
                        GemTransferRule(
                            true,
                            GemFilter.of(GemFilterMode.EXCLUDE, setOf("apotheosis:old_gem"), setOf("flawed")),
                        ),
                )
            helper.assertTrue(wrapper.replaceSettings(GemTransferSettings.DEFAULT, initial), "Cannot prepare settings")
            val fresh = container.sessionFor(menu)
            val request =
                settingsRequest(
                    menu.containerId,
                    fresh,
                    GemSettingsAction.SET_GEM_ID,
                    direction = GemTransferDirection.DEPOSIT,
                    value =
                        GemRegistry.INSTANCE.values
                            .first()
                            .id
                            .toString(),
                    selected = true,
                )
            helper.assertTrue(GemSettingsActions.perform(player, request), "Gem ID selection failed")
            val result = (wrapper.settingsSnapshot() as GemSettingsState.Ready).settings
            helper.assertTrue(result.deposit.filter.gemIds == setOf(request.value), "Gem ID was not added")
            helper.assertTrue(result.restock == initial.restock, "Other direction changed")
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun lockedSettingsRejectEditsButKeepManualGemInsertion(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val raw = CustomData.of(CompoundTag().apply { putInt("schema_version", 87) })
        wrapper.upgradeStack.set(ModDataComponents.gemTransferSettings.get(), raw)
        try {
            val container = menu.upgradeContainers.getValue(0) as GemCaseContainer
            val session = container.sessionFor(menu)
            val edit = settingsRequest(menu.containerId, session, GemSettingsAction.SET_ENABLED, enabled = true)
            helper.assertTrue(!GemSettingsActions.perform(player, edit), "Locked settings accepted an edit")
            helper.assertTrue(
                wrapper.upgradeStack.get(ModDataComponents.gemTransferSettings.get()) == raw,
                "Locked raw settings changed",
            )

            val gem = GemMenuFixtures.gem(3)
            val key = requireNotNull(StandardGem.keyOf(gem))
            menu.carried = gem.copy()
            val manual = GemRequest(menu.containerId, 0, session.id, session.revision, GemAction.INSERT_CURSOR, null, 0)
            helper.assertTrue(GemMenuActions.perform(player, manual), "Locked settings blocked manual insertion")
            helper.assertTrue(menu.carried.isEmpty, "Manual insertion left an unexpected remainder")
            helper.assertTrue(
                (wrapper.snapshot() as GemStorageState.Ready).counts.count(key) == gem.count,
                "Manual insertion did not save the expected Gem count",
            )
            helper.assertTrue(
                wrapper.upgradeStack.get(ModDataComponents.gemTransferSettings.get()) == raw,
                "Manual insertion rewrote locked settings",
            )
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun removesUnknownStoredValuesWithoutRequiringRegistryResolution(helper: GameTestHelper) {
        val (menu, player, wrapper) = GemMenuFixtures.menu(helper)
        val stored =
            GemTransferSettings.DEFAULT.copy(
                deposit =
                    GemTransferRule(
                        true,
                        GemFilter.of(GemFilterMode.EXCLUDE, setOf("future:gem"), setOf("future_purity")),
                    ),
            )
        helper.assertTrue(
            wrapper.replaceSettings(GemTransferSettings.DEFAULT, stored),
            "Cannot prepare unknown selections",
        )
        try {
            val container = menu.upgradeContainers.getValue(0) as GemCaseContainer
            var session = container.sessionFor(menu)
            val removeId =
                settingsRequest(
                    menu.containerId,
                    session,
                    GemSettingsAction.SET_GEM_ID,
                    value = "future:gem",
                    selected = false,
                )
            helper.assertTrue(GemSettingsActions.perform(player, removeId), "Could not remove unknown Gem ID")
            session = container.sessionFor(menu)
            val removePurity =
                settingsRequest(
                    menu.containerId,
                    session,
                    GemSettingsAction.SET_PURITY,
                    value = "future_purity",
                    selected = false,
                )
            helper.assertTrue(GemSettingsActions.perform(player, removePurity), "Could not remove unknown purity")
            val result = (wrapper.settingsSnapshot() as GemSettingsState.Ready).settings
            helper.assertTrue(
                result.deposit.filter.gemIds
                    .isEmpty() &&
                    result.deposit.filter.purities
                        .isEmpty(),
                "Unknown values remain selected",
            )
            helper.assertTrue(
                result.deposit.enabled && result.deposit.filter.mode == GemFilterMode.EXCLUDE,
                "Removing unknown values changed the rule",
            )
        } finally {
            player.server.playerList.remove(player)
        }
        helper.succeed()
    }

    private fun settingsRequest(
        menuId: Int,
        session: org.mocoserver.sophisticatedgemcase.menu.GemMenuSession,
        action: GemSettingsAction,
        direction: GemTransferDirection = GemTransferDirection.DEPOSIT,
        enabled: Boolean? = null,
        mode: GemFilterMode? = null,
        value: String? = null,
        selected: Boolean? = null,
    ): GemSettingsRequest =
        GemSettingsRequest(
            menuId,
            0,
            session.id,
            session.revision,
            action,
            direction,
            enabled,
            mode,
            value,
            selected,
            0,
        )
}
