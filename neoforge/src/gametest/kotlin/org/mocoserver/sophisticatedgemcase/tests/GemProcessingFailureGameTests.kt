package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingMenu
import dev.shadowsoffire.apotheosis.socket.gem.cutting.PurityUpgradeRecipe
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingEngine
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingPlanner
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingReason
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialHandler

/** 加工前提の不足やcallback例外がGem、材料、通常slotを部分変更しないことを検査する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemProcessingFailureGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun inputShortageLeavesGemAndMaterialsUnchanged(helper: GameTestHelper) {
        val fixture = processingFixture(helper, inputCount = 1)
        val beforeCounts = fixture.wrapper.snapshot()
        val beforeMaterials = fixture.wrapper.materialsSnapshot(helper.level.registryAccess())

        helper.assertTrue(
            GemProcessingEngine.process(fixture.backpack, helper.level, fixture.output, 1) == 0,
            "One input Gem was enough for a two-input recipe",
        )
        helper.assertTrue(fixture.wrapper.snapshot() == beforeCounts, "Input shortage changed Gem counts")
        helper.assertTrue(
            fixture.wrapper.materialsSnapshot(helper.level.registryAccess()) == beforeMaterials,
            "Input shortage changed material storage",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun fullOutputLeavesGemAndMaterialsUnchanged(helper: GameTestHelper) {
        val fixture = processingFixture(helper, inputCount = 2, outputCount = GemCounts.CAPACITY)
        val beforeCounts = fixture.wrapper.snapshot()
        val beforeMaterials = fixture.wrapper.materialsSnapshot(helper.level.registryAccess())
        val preview = GemProcessingPlanner.preview(fixture.backpack, helper.level, fixture.output)

        helper.assertTrue(preview.reason == GemProcessingReason.OUTPUT_FULL, "Full output was not reported")
        helper.assertTrue(
            GemProcessingEngine.process(fixture.backpack, helper.level, fixture.output, 1) == 0,
            "Full output accepted a craft",
        )
        helper.assertTrue(fixture.wrapper.snapshot() == beforeCounts, "Full output changed Gem counts")
        helper.assertTrue(
            fixture.wrapper.materialsSnapshot(helper.level.registryAccess()) == beforeMaterials,
            "Full output consumed materials",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun materialShortageLeavesEveryStorageUnchanged(helper: GameTestHelper) {
        val fixture = processingFixture(helper, inputCount = 2, provideMaterials = false)
        val beforeCounts = fixture.wrapper.snapshot()
        val beforeMaterials = fixture.wrapper.materialsSnapshot(helper.level.registryAccess())
        val beforeNormal =
            List(fixture.backpack.inventoryHandler.slots) {
                fixture.backpack.inventoryHandler
                    .getStackInSlot(it)
                    .copy()
            }
        val preview = GemProcessingPlanner.preview(fixture.backpack, helper.level, fixture.output)

        helper.assertTrue(
            preview.reason == GemProcessingReason.MATERIAL_SHORTAGE,
            "Missing materials were not reported",
        )
        helper.assertTrue(
            GemProcessingEngine.process(fixture.backpack, helper.level, fixture.output, 1) == 0,
            "Insufficient materials accepted a craft",
        )
        helper.assertTrue(fixture.wrapper.snapshot() == beforeCounts, "Material shortage changed Gem counts")
        helper.assertTrue(
            fixture.wrapper.materialsSnapshot(helper.level.registryAccess()) == beforeMaterials,
            "Material shortage changed dedicated slots",
        )
        helper.assertTrue(
            beforeNormal.indices.all {
                ItemStack.matches(beforeNormal[it], fixture.backpack.inventoryHandler.getStackInSlot(it))
            },
            "Material shortage changed normal slots",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun failedSaveRestoresNormalMaterialsAndAllowsNextOperation(helper: GameTestHelper) {
        val fixture = processingFixture(helper, inputCount = 2, putMaterialsInNormalInventory = true)
        fixture.wrapper.replaceProcessingSettings(false, true)
        val original = fixture.backpack.upgradeHandler.getStackInSlot(0)
        var callbacks = 0
        val failingWrapper =
            org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper(
                fixture.backpack,
                original,
            ) {
                callbacks++
                if (callbacks == 1) throw IllegalStateException("test save failure")
            }
        fixture.backpack.upgradeHandler.slotWrappers[0] = failingWrapper
        val beforeCounts = failingWrapper.snapshot()
        val beforeMaterials = failingWrapper.materialsSnapshot(helper.level.registryAccess())
        val beforeNormal =
            List(fixture.backpack.inventoryHandler.slots) {
                fixture.backpack.inventoryHandler
                    .getStackInSlot(it)
                    .copy()
            }
        var failed = false
        try {
            GemProcessingEngine.process(fixture.backpack, helper.level, fixture.output, 1)
        } catch (_: IllegalStateException) {
            failed = true
        }

        helper.assertTrue(failed && callbacks >= 2, "Save exception was swallowed or rollback callback was skipped")
        helper.assertTrue(failingWrapper.snapshot() == beforeCounts, "Failed commit changed Gem counts")
        helper.assertTrue(
            failingWrapper.materialsSnapshot(helper.level.registryAccess()) == beforeMaterials,
            "Failed commit changed dedicated material storage",
        )
        helper.assertTrue(
            beforeNormal.indices.all {
                ItemStack.matches(beforeNormal[it], fixture.backpack.inventoryHandler.getStackInSlot(it))
            },
            "Failed commit did not restore normal slots",
        )

        fixture.backpack.upgradeHandler.slotWrappers[0] = fixture.wrapper
        helper.assertTrue(
            GemProcessingEngine.process(fixture.backpack, helper.level, fixture.output, 1) == 1,
            "Next operation failed after callback recovery",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun reentrantSaveCallbackCannotProcessTheSameBackpackTwice(helper: GameTestHelper) {
        val fixture = processingFixture(helper, inputCount = 2)
        val otherFixture = processingFixture(helper, inputCount = 2)
        val original = fixture.backpack.upgradeHandler.getStackInSlot(0)
        var nestedResult = -1
        var otherBackpackResult = -1
        var callbacks = 0
        val reentrantWrapper =
            org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper(
                fixture.backpack,
                original,
            ) {
                callbacks++
                if (callbacks == 1) {
                    nestedResult =
                        GemProcessingEngine.process(
                            fixture.backpack,
                            helper.level,
                            fixture.output,
                            1,
                        )
                    otherBackpackResult =
                        GemProcessingEngine.process(
                            otherFixture.backpack,
                            helper.level,
                            otherFixture.output,
                            1,
                        )
                }
            }
        fixture.backpack.upgradeHandler.slotWrappers[0] = reentrantWrapper

        val processed = GemProcessingEngine.process(fixture.backpack, helper.level, fixture.output, 1)

        helper.assertTrue(processed == 1, "Outer operation did not complete")
        helper.assertTrue(nestedResult == 0, "Save callback reentry processed the backpack twice")
        helper.assertTrue(otherBackpackResult == 1, "Exclusive operation blocked an unrelated backpack")
        val counts = reentrantWrapper.snapshot() as org.mocoserver.sophisticatedgemcase.storage.GemStorageState.Ready
        helper.assertTrue(
            counts.counts.count(fixture.output) == 1 &&
                counts.counts.count(
                    org.mocoserver.sophisticatedgemcase.core.GemKey(
                        fixture.output.gemId,
                        dev.shadowsoffire.apotheosis.socket.gem.Purity.CRACKED.serializedName,
                    ),
                ) == 0,
            "Reentrant callback caused duplicate Gem conversion",
        )
        helper.succeed()
    }

    private data class Fixture(
        val backpack: net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper,
        val wrapper: org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper,
        val output: GemKey,
    )

    private fun processingFixture(
        helper: GameTestHelper,
        inputCount: Int,
        outputCount: Int = 0,
        provideMaterials: Boolean = true,
        putMaterialsInNormalInventory: Boolean = false,
    ): Fixture {
        val backpack =
            GemTestFixtures.backpack().apply {
                upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
            }
        val wrapper =
            backpack.upgradeHandler.slotWrappers.getValue(0)
                as org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val input = GemKey(gem.id.toString(), Purity.CRACKED.serializedName)
        val output = GemKey(gem.id.toString(), Purity.CHIPPED.serializedName)
        val counts =
            buildMap {
                if (inputCount > 0) put(input, inputCount)
                if (outputCount > 0) put(output, outputCount)
            }
        check(wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(counts)))
        if (provideMaterials) {
            val recipe =
                GemCuttingMenu
                    .getRecipes(helper.level)
                    .first { holder ->
                        (holder.value as? PurityUpgradeRecipe)?.purity() == Purity.CRACKED
                    }.value as PurityUpgradeRecipe
            val left =
                recipe
                    .left()
                    .first()
                    .getItems()
                    .first()
            val right =
                recipe
                    .right()
                    .first()
                    .getItems()
                    .first()
            if (putMaterialsInNormalInventory) {
                backpack.inventoryHandler.setStackInSlot(0, left)
                backpack.inventoryHandler.setStackInSlot(1, right)
            } else {
                GemMaterialHandler(backpack, helper.level) { true }.apply {
                    setStackInSlot(0, left)
                    setStackInSlot(1, right)
                }
            }
        }
        return Fixture(backpack, wrapper, output)
    }
}
