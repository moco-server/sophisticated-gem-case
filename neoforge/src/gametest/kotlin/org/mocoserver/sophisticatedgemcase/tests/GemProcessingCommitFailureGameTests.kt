package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import dev.shadowsoffire.apotheosis.socket.gem.cutting.PurityUpgradeRecipe
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingEngine
import org.mocoserver.sophisticatedgemcase.processing.GemPurityRecipes
import org.mocoserver.sophisticatedgemcase.processing.GemRecipeEpoch
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** 保存後の前提不一致でも、未存在componentを新しい空componentへ書き換えない。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemProcessingCommitFailureGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun restoresExactRawComponentsWhenEpochChangesDuringSave(helper: GameTestHelper) {
        val backpack = GemTestFixtures.backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
        val initial = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val input = GemKey(gem.id.toString(), Purity.CRACKED.serializedName)
        val output = GemKey(gem.id.toString(), Purity.CHIPPED.serializedName)
        initial.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(input to 2)))
        initial.replaceProcessingSettings(false, true)
        val recipe = GemPurityRecipes.recipes(helper.level).first { it.value.purity() == Purity.CRACKED }.value
        fillNormal(backpack.inventoryHandler, recipe)
        val stack = backpack.upgradeHandler.getStackInSlot(0)
        val rawCounts = stack.get(ModDataComponents.gemStorage.get())
        val rawMaterials = stack.get(ModDataComponents.gemProcessingMaterials.get())
        val beforeNormal = List(backpack.inventoryHandler.slots) { backpack.inventoryHandler.getStackInSlot(it).copy() }
        var callbacks = 0
        val wrapper =
            GemCaseUpgradeWrapper(backpack, stack) {
                if (++callbacks == 1) GemRecipeEpoch.advance()
            }
        backpack.upgradeHandler.slotWrappers[0] = wrapper
        var rejected = false
        try {
            GemProcessingEngine.process(backpack, helper.level, output, 1)
        } catch (_: IllegalStateException) {
            rejected = true
        }
        helper.assertTrue(rejected, "Changed recipe epoch was accepted after save")
        helper.assertTrue(stack.get(ModDataComponents.gemStorage.get()) == rawCounts, "Gem raw was not restored")
        helper.assertTrue(
            stack.get(ModDataComponents.gemProcessingMaterials.get()) == rawMaterials,
            "Absent material component was replaced by canonical empty data",
        )
        helper.assertTrue(
            beforeNormal.indices.all {
                ItemStack.matches(beforeNormal[it], backpack.inventoryHandler.getStackInSlot(it))
            },
            "Normal materials were not restored",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun preservesRawAndOriginalExceptionWhenUpgradeSaveRepeatsFailure(helper: GameTestHelper) {
        val backpack = GemTestFixtures.backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
        val initial = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val input = GemKey(gem.id.toString(), Purity.CRACKED.serializedName)
        val output = GemKey(gem.id.toString(), Purity.CHIPPED.serializedName)
        initial.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(input to 2)))
        initial.replaceProcessingSettings(false, true)
        val recipe = GemPurityRecipes.recipes(helper.level).first { it.value.purity() == Purity.CRACKED }.value
        fillNormal(backpack.inventoryHandler, recipe)
        val stack = backpack.upgradeHandler.getStackInSlot(0)
        val rawCounts = stack.get(ModDataComponents.gemStorage.get())
        val rawMaterials = stack.get(ModDataComponents.gemProcessingMaterials.get())
        val beforeNormal = List(backpack.inventoryHandler.slots) { backpack.inventoryHandler.getStackInSlot(it).copy() }
        val repeated = IllegalStateException("reused upgrade save failure")
        val wrapper =
            GemCaseUpgradeWrapper(backpack, stack) {
                throw repeated
            }
        backpack.upgradeHandler.slotWrappers[0] = wrapper
        var failure: RuntimeException? = null
        try {
            GemProcessingEngine.process(backpack, helper.level, output, 1)
        } catch (caught: RuntimeException) {
            failure = caught
        }
        helper.assertTrue(failure === repeated, "Repeated upgrade save replaced the original exception")
        helper.assertTrue(stack.get(ModDataComponents.gemStorage.get()) == rawCounts, "Gem raw was not restored")
        helper.assertTrue(
            stack.get(ModDataComponents.gemProcessingMaterials.get()) == rawMaterials,
            "Absent material component was replaced by canonical empty data",
        )
        helper.assertTrue(
            beforeNormal.indices.all {
                ItemStack.matches(beforeNormal[it], backpack.inventoryHandler.getStackInSlot(it))
            },
            "Normal materials were not restored",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restoresAllNormalSlotsWhenCallbackReusesFailure(helper: GameTestHelper) {
        val backpack = GemTestFixtures.backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
        val wrapper = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val input = GemKey(gem.id.toString(), Purity.CRACKED.serializedName)
        val output = GemKey(gem.id.toString(), Purity.CHIPPED.serializedName)
        wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(input to 2)))
        wrapper.replaceProcessingSettings(false, true)
        val recipe = GemPurityRecipes.recipes(helper.level).first { it.value.purity() == Purity.CRACKED }.value
        val inventory = backpack.inventoryHandler
        fillNormal(inventory, recipe)
        val before = List(inventory.slots) { inventory.getStackInSlot(it).copy() }
        val counts = wrapper.snapshot()
        val repeated = IllegalStateException("reused inventory save failure")
        var calls = 0
        // 固定版の実保存callbackだけをtestで差し替え、本番へreflectionを持ち込まない。
        val field =
            net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler::class.java
                .getDeclaredField("saveHandler")
        field.isAccessible = true
        field.set(inventory, Runnable { if (++calls >= 2) throw repeated })
        val failure =
            try {
                GemProcessingEngine.process(backpack, helper.level, output, 1)
                null
            } catch (caught: RuntimeException) {
                caught
            }
        helper.assertTrue(failure === repeated, "Recovery replaced the original save exception")
        helper.assertTrue(
            before.indices.all {
                ItemStack.matches(before[it], inventory.getStackInSlot(it))
            },
            "Repeated recovery exception stopped restoration of normal slots",
        )
        helper.assertTrue(wrapper.snapshot() == counts, "Failed normal staging changed Gem counts")
        helper.succeed()
    }

    private fun fillNormal(
        inventory: net.neoforged.neoforge.items.IItemHandlerModifiable,
        recipe: PurityUpgradeRecipe,
    ) {
        inventory.setStackInSlot(
            0,
            recipe
                .left()
                .first()
                .getItems()
                .first(),
        )
        inventory.setStackInSlot(
            1,
            recipe
                .right()
                .first()
                .getItems()
                .first(),
        )
    }
}
