package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.integration.GemOperationContext
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.GemTransfers
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** 上位でも継承する一括移動の例外復元と保存callback再入を検査する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemBulkMoveFailureGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun restoresEveryNormalSlotWhenBulkMoveAndRecoverySaveThrow(helper: GameTestHelper) {
        val backpack = fixture()
        val inventory = backpack.inventoryHandler
        val before = List(inventory.slots) { inventory.getStackInSlot(it).copy() }
        val wrapper = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val counts = wrapper.snapshot()
        val repeated = IllegalStateException("bulk inventory failure")
        var calls = 0
        installCallback(inventory) { if (++calls >= 2) throw repeated }
        val failure = capture { GemTransfers.moveFromNormal(backpack) }
        helper.assertTrue(failure === repeated, "Bulk move did not preserve its inventory failure")
        helper.assertTrue(
            before.indices.all { ItemStack.matches(before[it], inventory.getStackInSlot(it)) },
            "Bulk move failed to restore all normal slots",
        )
        helper.assertTrue(wrapper.snapshot() == counts, "Failed bulk move changed dedicated counts")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restoresRawAndNormalSlotsWhenBulkGemSaveThrows(helper: GameTestHelper) {
        val backpack = fixture()
        val inventory = backpack.inventoryHandler
        val before = List(inventory.slots) { inventory.getStackInSlot(it).copy() }
        val stack = backpack.upgradeHandler.getStackInSlot(0)
        val raw = stack.get(ModDataComponents.gemStorage.get())
        val repeated = IllegalStateException("bulk gem failure")
        val wrapper = GemCaseUpgradeWrapper(backpack, stack) { throw repeated }
        backpack.upgradeHandler.slotWrappers[0] = wrapper
        val failure = capture { GemTransfers.moveFromNormal(backpack) }
        helper.assertTrue(failure === repeated, "Bulk move did not preserve its Gem save failure")
        helper.assertTrue(stack.get(ModDataComponents.gemStorage.get()) == raw, "Bulk failure changed Gem raw data")
        helper.assertTrue(
            before.indices.all { ItemStack.matches(before[it], inventory.getStackInSlot(it)) },
            "Bulk Gem save failure consumed normal Gem stacks",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsBulkMoveReentryFromInventorySave(helper: GameTestHelper) {
        val backpack = fixture()
        val wrapper = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val key = requireNotNull(StandardGem.keyOf(backpack.inventoryHandler.getStackInSlot(0)))
        var calls = 0
        var nested = -1
        installCallback(backpack.inventoryHandler) {
            if (++calls == 1) nested = GemTransfers.moveFromNormal(backpack)
        }
        val moved = GemTransfers.moveFromNormal(backpack)
        helper.assertTrue(nested == 0 && moved == 6, "Bulk move allowed reentry or failed its outer operation")
        helper.assertTrue(
            (wrapper.snapshot() as GemStorageState.Ready).counts.count(key) == 6,
            "Reentry changed dedicated Gem quantity",
        )
        helper.assertTrue(
            (0 until backpack.inventoryHandler.slots).all {
                backpack.inventoryHandler.getStackInSlot(it).isEmpty
            },
            "Bulk move left duplicate normal Gem stacks",
        )
        helper.assertTrue(!GemOperationContext.isExclusive(backpack), "Bulk move leaked exclusive context")
        helper.succeed()
    }

    private fun fixture(): BackpackWrapper {
        val backpack = GemTestFixtures.backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        repeat(2) { backpack.inventoryHandler.setStackInSlot(it, GemItem.createStack(gem, Purity.CRACKED, 3)) }
        return backpack
    }

    private fun capture(action: () -> Unit): RuntimeException? =
        try {
            action()
            null
        } catch (failure: RuntimeException) {
            failure
        }

    private fun installCallback(
        inventory: InventoryHandler,
        callback: () -> Unit,
    ) {
        // 固定版の実保存経路だけをtestで差し替える。
        val field = InventoryHandler::class.java.getDeclaredField("saveHandler")
        field.isAccessible = true
        field.set(inventory, Runnable(callback))
    }
}
