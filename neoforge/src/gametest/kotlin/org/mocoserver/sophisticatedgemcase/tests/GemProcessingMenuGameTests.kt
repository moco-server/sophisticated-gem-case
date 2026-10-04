package org.mocoserver.sophisticatedgemcase.tests

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMaterialSlot
import org.mocoserver.sophisticatedgemcase.menu.GemProcessingActions
import org.mocoserver.sophisticatedgemcase.mixin.GemSlotPositionAccessor
import org.mocoserver.sophisticatedgemcase.network.GemProcessingAction
import org.mocoserver.sophisticatedgemcase.network.GemProcessingRequest
import org.mocoserver.sophisticatedgemcase.processing.GemRecipeEpoch
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsState

/** 材料をnative slotで操作し、個数不変のreloadも操作世代を失効させる。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemProcessingMenuGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun editsSupplementDespiteLockedTransferSettingsAndRejectsReplay(helper: GameTestHelper) {
        val (old, player, _) = GemMenuFixtures.menu(helper)
        try {
            old.storageWrapper.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
            val menu = BackpackContainer(18, player, old.backpackContext)
            player.containerMenu = menu
            val container = menu.upgradeContainers[0] as GemCaseContainer
            val future = CustomData.of(CompoundTag().apply { putInt("schema_version", 99) })
            container.upgradeWrapper.upgradeStack.set(ModDataComponents.gemTransferSettings.get(), future)
            val session = container.sessionFor(menu)
            val request =
                GemProcessingRequest(
                    menu.containerId,
                    0,
                    session.id,
                    session.revision,
                    GemProcessingAction.SET_BACKPACK_MATERIALS,
                    null,
                    true,
                )
            helper.assertTrue(
                GemProcessingActions.perform(player, request),
                "Transfer lock blocked own supplement setting",
            )
            helper.assertTrue(
                container.upgradeWrapper.processingSettingsSnapshot() == GemProcessingSettingsState.Ready(true),
                "Supplement setting was not persisted",
            )
            helper.assertTrue(!GemProcessingActions.perform(player, request), "Stale supplement replay succeeded")
            helper.assertTrue(
                container.upgradeWrapper.upgradeStack.get(ModDataComponents.gemTransferSettings.get()) == future,
                "Own setting edit changed invalid transfer raw",
            )
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsProcessingRequestsForLowerClosedAndStaleMenus(helper: GameTestHelper) {
        val (menu, player, _) = GemMenuFixtures.menu(helper)
        try {
            val container = menu.upgradeContainers[0] as GemCaseContainer
            val session = container.sessionFor(menu)
            val request =
                GemProcessingRequest(
                    menu.containerId,
                    0,
                    session.id,
                    session.revision,
                    GemProcessingAction.SET_BACKPACK_MATERIALS,
                    null,
                    true,
                )
            helper.assertTrue(!GemProcessingActions.perform(player, request), "Lower accepted processing setting")
            player.containerMenu = player.inventoryMenu
            helper.assertTrue(!GemProcessingActions.perform(player, request), "Closed menu accepted processing setting")
            player.containerMenu = menu
            helper.assertTrue(
                !GemProcessingActions.perform(player, request.copy(revision = request.revision + 1)),
                "Wrong generation accepted processing setting",
            )
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun closesProcessingViewDespiteOldRevisionButRejectsWrongSession(helper: GameTestHelper) {
        val (old, player, _) = GemMenuFixtures.menu(helper)
        try {
            old.storageWrapper.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
            val menu = BackpackContainer(18, player, old.backpackContext)
            player.containerMenu = menu
            val container = menu.upgradeContainers[0] as GemCaseContainer
            val session = container.sessionFor(menu)
            val request =
                GemProcessingRequest(
                    menu.containerId,
                    0,
                    session.id,
                    session.revision,
                    GemProcessingAction.SET_PROCESSING_VIEW,
                    null,
                    true,
                )
            helper.assertTrue(GemProcessingActions.perform(player, request), "Current view did not open")
            GemRecipeEpoch.advance()
            container.sessionFor(menu)
            helper.assertTrue(
                GemProcessingActions.perform(player, request.copy(enabled = false)),
                "Old view did not close",
            )
            helper.assertTrue(!container.processingViewOpen, "Hidden materials remained editable")
            helper.assertTrue(
                !GemProcessingActions.perform(player, request.copy(sessionId = java.util.UUID.randomUUID())),
                "Wrong session opened material view",
            )
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun createsSixNativeSlotsAndInvalidatesRevisionOnRecipeEpoch(helper: GameTestHelper) {
        val (old, player, _) = GemMenuFixtures.menu(helper)
        try {
            old.storageWrapper.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
            val menu = BackpackContainer(18, player, old.backpackContext)
            player.containerMenu = menu
            val container = menu.upgradeContainers[0] as GemCaseContainer
            helper.assertTrue(container.slots.size == 6, "Upper did not expose six material slots")
            helper.assertTrue(container.slots.all { it is GemMaterialSlot }, "Material slots lack dedicated validation")
            val materialSlot = container.slots.first()
            val positioned = materialSlot as GemSlotPositionAccessor
            positioned.gemCaseSetX(31)
            positioned.gemCaseSetY(67)
            helper.assertTrue(materialSlot.x == 31 && materialSlot.y == 67, "Native slot position was not mutable")
            val session = container.sessionFor(menu)
            val before = session.revision
            GemRecipeEpoch.advance()
            helper.assertTrue(
                container.sessionFor(menu).revision == before + 1,
                "Recipe reload left old generation usable",
            )
            menu.carried = ItemStack(Items.STONE, 2)
            val slot = container.slots.first()
            menu.clicked(slot.index, 0, ClickType.PICKUP, player)
            helper.assertTrue(menu.carried.count == 2 && slot.item.isEmpty, "Non-material was consumed")
            menu.clicked(slot.index, 0, ClickType.SWAP, player)
            helper.assertTrue(menu.carried.count == 2 && slot.item.isEmpty, "Rejected swap changed materials")
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }
}
