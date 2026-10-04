package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingMenu
import dev.shadowsoffire.apotheosis.socket.gem.cutting.PurityUpgradeRecipe
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeHolder
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.processing.GemMaterialSide
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingEngine
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingPlanner
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingReason
import org.mocoserver.sophisticatedgemcase.processing.GemRecipeEpoch
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialHandler
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialState
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState

/** 固定版Apotheosisのrecipeを使い、上位Gem Caseの加工選択と専用／通常材料を検査する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemProcessingGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun processesUsingFirstNativeRecipeAndConsumesItsLeftAndRightCosts(helper: GameTestHelper) {
        val backpack = advancedBackpack()
        val wrapper = wrapper(backpack)
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val input = GemKey(gem.id.toString(), Purity.CRACKED.serializedName)
        val output = GemKey(gem.id.toString(), Purity.CHIPPED.serializedName)
        val recipeHolder =
            GemCuttingMenu.getRecipes(helper.level).first { holder ->
                (holder.value as? PurityUpgradeRecipe)?.purity() == Purity.CRACKED
            }
        val recipe = recipeHolder.value as PurityUpgradeRecipe
        val left =
            recipe
                .left()
                .first()
                .getItems()
                .first()
        val right =
            recipe
                .right()
                .last()
                .getItems()
                .first()
        val materials =
            materialHandler(backpack, helper).apply {
                setStackInSlot(0, left)
                setStackInSlot(1, right)
            }
        helper.assertTrue(
            wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(input to 2))),
            "Could not prepare input Gems",
        )

        val preview = GemProcessingPlanner.preview(backpack, helper.level, output)
        val plan = GemProcessingPlanner.plan(backpack, helper.level, output)
        helper.assertTrue(preview.reason == GemProcessingReason.READY, "Native material slots did not satisfy recipe")
        helper.assertTrue(preview.recipeId == recipeHolder.id, "Recipe order differs from Apotheosis selection")
        helper.assertTrue(
            plan != null && plan.leftCandidateOrdinal == 0 && plan.rightCandidateOrdinal == recipe.right().lastIndex,
            "Plan did not retain the selected left/right candidate ordinals",
        )
        helper.assertTrue(
            preview.costs ==
                listOf(
                    nativeCost(left, recipe.left().first().count(), GemMaterialSide.LEFT),
                    nativeCost(right, recipe.right().last().count(), GemMaterialSide.RIGHT),
                ),
            "Preview did not expose the selected recipe's actual left/right costs",
        )
        helper.assertTrue(GemProcessingEngine.process(backpack, helper.level, output, 1) == 1, "Processing failed")
        val counts = (wrapper.snapshot() as GemStorageState.Ready).counts
        helper.assertTrue(counts.count(input) == 0 && counts.count(output) == 1, "Gem 2-to-1 counts are wrong")
        val remaining = (wrapper.materialsSnapshot(helper.level.registryAccess()) as GemMaterialState.Ready).contents
        helper.assertTrue(
            remaining.stackAt(0).isEmpty && remaining.stackAt(1).isEmpty,
            "Recipe materials were not consumed",
        )
        helper.assertTrue(materials.getStackInSlot(0).isEmpty, "Left material handler retained consumed input")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun dedicatedMaterialsRemainPreferredWhenRefillIsEnabled(helper: GameTestHelper) {
        val backpack = advancedBackpack()
        val wrapper = wrapper(backpack)
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val input = GemKey(gem.id.toString(), Purity.CRACKED.serializedName)
        val output = GemKey(gem.id.toString(), Purity.CHIPPED.serializedName)
        val recipe = nativeRecipe(helper, Purity.CRACKED)
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
        materialHandler(backpack, helper).apply {
            setStackInSlot(0, left.copy())
            setStackInSlot(1, right.copy())
        }
        backpack.inventoryHandler.setStackInSlot(0, left.copy())
        backpack.inventoryHandler.setStackInSlot(1, right.copy())
        helper.assertTrue(
            wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(input to 2))),
            "Could not prepare input",
        )
        helper.assertTrue(wrapper.replaceProcessingSettings(false, true), "Could not enable refill")

        helper.assertTrue(GemProcessingEngine.process(backpack, helper.level, output, 1) == 1, "Native recipe failed")
        val materials = (wrapper.materialsSnapshot(helper.level.registryAccess()) as GemMaterialState.Ready).contents
        helper.assertTrue(
            materials.stackAt(0).isEmpty && materials.stackAt(1).isEmpty,
            "Dedicated materials were not consumed first",
        )
        helper.assertTrue(
            backpack.inventoryHandler.getStackInSlot(0).count == left.count &&
                backpack.inventoryHandler.getStackInSlot(1).count == right.count,
            "Refill materials were consumed while a native pair was available",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun sharedCandidateIsReservedForRightWhenLeftHasAnotherSource(helper: GameTestHelper) {
        val backpack = advancedBackpack()
        val wrapper = wrapper(backpack)
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val input = GemKey(gem.id.toString(), Purity.CRACKED.serializedName)
        val output = GemKey(gem.id.toString(), Purity.CHIPPED.serializedName)
        val manager = helper.level.recipeManager
        val originalRecipes = manager.recipes.toList()
        val recipeId = ResourceLocation.fromNamespaceAndPath("sophisticated_gem_case", "test/shared_candidate")
        val sharedRecipe =
            PurityUpgradeRecipe(
                Purity.CRACKED,
                listOf(
                    net.neoforged.neoforge.common.crafting.SizedIngredient(
                        Ingredient.of(Items.DIAMOND, Items.EMERALD),
                        1,
                    ),
                ),
                listOf(
                    net.neoforged.neoforge.common.crafting
                        .SizedIngredient(Ingredient.of(Items.DIAMOND), 2),
                ),
            )
        val sharedHolder = RecipeHolder(recipeId, sharedRecipe)
        try {
            manager.replaceRecipes(originalRecipes + sharedHolder)
            GemRecipeEpoch.advance()
            backpack.inventoryHandler.setStackInSlot(0, ItemStack(Items.DIAMOND, 2))
            backpack.inventoryHandler.setStackInSlot(1, ItemStack(Items.EMERALD, 1))
            helper.assertTrue(
                wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(input to 2))),
                "Could not prepare input",
            )
            helper.assertTrue(wrapper.replaceProcessingSettings(false, true), "Could not enable refill")

            val preview = GemProcessingPlanner.preview(backpack, helper.level, output)
            helper.assertTrue(
                preview.reason == GemProcessingReason.READY,
                "Shared candidate prevented a valid allocation",
            )
            helper.assertTrue(preview.recipeId == recipeId, "Synthetic recipe was not selected")
            helper.assertTrue(
                preview.costs ==
                    listOf(
                        nativeCost(ItemStack(Items.EMERALD), 1, GemMaterialSide.LEFT),
                        nativeCost(ItemStack(Items.DIAMOND), 2, GemMaterialSide.RIGHT),
                    ),
                "The shared stack was spent on left before reserving the right requirement",
            )
            val beforeReloadEpoch = GemRecipeEpoch.current()
            manager.replaceRecipes(originalRecipes)
            GemRecipeEpoch.advance()
            val afterReload = GemProcessingPlanner.preview(backpack, helper.level, output)
            helper.assertTrue(
                GemRecipeEpoch.current() > beforeReloadEpoch &&
                    afterReload.reason == GemProcessingReason.MATERIAL_SHORTAGE,
                "Recipe reload retained a cached recipe or old epoch",
            )
            manager.replaceRecipes(originalRecipes + sharedHolder)
            GemRecipeEpoch.advance()
            helper.assertTrue(
                GemProcessingEngine.process(backpack, helper.level, output, 1) == 1,
                "Shared allocation failed",
            )
            helper.assertTrue(backpack.inventoryHandler.getStackInSlot(0).isEmpty, "Right stack was not consumed")
            helper.assertTrue(backpack.inventoryHandler.getStackInSlot(1).isEmpty, "Left-only stack was not consumed")
        } finally {
            manager.replaceRecipes(originalRecipes)
            GemRecipeEpoch.advance()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun ingredientBudgetExhaustionLeavesAllStorageUnchanged(helper: GameTestHelper) {
        val backpack = advancedBackpack()
        val wrapper = wrapper(backpack)
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val input = GemKey(gem.id.toString(), Purity.CRACKED.serializedName)
        val output = GemKey(gem.id.toString(), Purity.CHIPPED.serializedName)
        val manager = helper.level.recipeManager
        val originalRecipes = manager.recipes.toList()
        val recipeId = ResourceLocation.fromNamespaceAndPath("sophisticated_gem_case", "test/candidate_budget")
        val ingredient =
            net.neoforged.neoforge.common.crafting.SizedIngredient(
                Ingredient.of(Items.DIAMOND),
                1,
            )
        val budgetRecipe =
            PurityUpgradeRecipe(
                Purity.CRACKED,
                List(65) { ingredient },
                List(65) { ingredient },
            )
        try {
            manager.replaceRecipes(originalRecipes + RecipeHolder(recipeId, budgetRecipe))
            GemRecipeEpoch.advance()
            helper.assertTrue(
                wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(input to 2))),
                "Could not prepare input",
            )
            helper.assertTrue(wrapper.replaceProcessingSettings(false, true), "Could not enable refill")
            val beforeCounts = wrapper.snapshot()
            val beforeMaterials = wrapper.materialsSnapshot(helper.level.registryAccess())
            val beforeNormal =
                List(backpack.inventoryHandler.slots) { backpack.inventoryHandler.getStackInSlot(it).copy() }

            val preview = GemProcessingPlanner.preview(backpack, helper.level, output)
            helper.assertTrue(
                preview.reason == GemProcessingReason.BUDGET_EXCEEDED,
                "Candidate budget exhaustion was not reported",
            )
            helper.assertTrue(
                GemProcessingEngine.process(backpack, helper.level, output, 1) == 0,
                "Budget exhaustion crafted",
            )
            helper.assertTrue(wrapper.snapshot() == beforeCounts, "Budget exhaustion changed Gem counts")
            helper.assertTrue(
                wrapper.materialsSnapshot(helper.level.registryAccess()) == beforeMaterials,
                "Budget exhaustion changed dedicated materials",
            )
            helper.assertTrue(
                beforeNormal.indices.all {
                    ItemStack.matches(beforeNormal[it], backpack.inventoryHandler.getStackInSlot(it))
                },
                "Budget exhaustion changed normal inventory",
            )
        } finally {
            manager.replaceRecipes(originalRecipes)
            GemRecipeEpoch.advance()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun recipeBudgetExhaustionDoesNotUsePartialRecipeList(helper: GameTestHelper) {
        val backpack = advancedBackpack()
        val wrapper = wrapper(backpack)
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val input = GemKey(gem.id.toString(), Purity.CRACKED.serializedName)
        val output = GemKey(gem.id.toString(), Purity.CHIPPED.serializedName)
        val manager = helper.level.recipeManager
        val originalRecipes = manager.recipes.toList()
        val syntheticRecipes =
            List(513) { index ->
                RecipeHolder(
                    ResourceLocation.fromNamespaceAndPath("sophisticated_gem_case", "test/recipe_budget_$index"),
                    PurityUpgradeRecipe(Purity.CRACKED, emptyList(), emptyList()),
                )
            }
        try {
            manager.replaceRecipes(originalRecipes + syntheticRecipes)
            GemRecipeEpoch.advance()
            helper.assertTrue(
                wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(input to 2))),
                "Could not prepare input",
            )
            val before = wrapper.snapshot()

            val preview = GemProcessingPlanner.preview(backpack, helper.level, output)
            helper.assertTrue(
                preview.reason == GemProcessingReason.BUDGET_EXCEEDED,
                "Recipe budget exhaustion was not reported",
            )
            helper.assertTrue(
                GemProcessingEngine.process(backpack, helper.level, output, 1) == 0,
                "Partial recipe list crafted",
            )
            helper.assertTrue(wrapper.snapshot() == before, "Recipe budget exhaustion changed Gem counts")
        } finally {
            manager.replaceRecipes(originalRecipes)
            GemRecipeEpoch.advance()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun usesOnlySameBackpackNormalSlotsWhenRefillIsEnabled(helper: GameTestHelper) {
        val fixture = GemMenuFixtures.menu(helper)
        val backpack = fixture.menu.storageWrapper
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
        val wrapper =
            backpack.upgradeHandler.slotWrappers.getValue(0)
                as org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper
        val otherBackpack = GemTestFixtures.backpack()
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val input = GemKey(gem.id.toString(), Purity.CRACKED.serializedName)
        val output = GemKey(gem.id.toString(), Purity.CHIPPED.serializedName)
        val recipe = nativeRecipe(helper, Purity.CRACKED)
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
        backpack.inventoryHandler.setStackInSlot(0, left.copy())
        backpack.inventoryHandler.setStackInSlot(1, right.copy())
        otherBackpack.inventoryHandler.setStackInSlot(0, left.copy())
        helper.assertTrue(
            wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(input to 2))),
            "Could not prepare input Gems",
        )

        try {
            helper.assertTrue(
                GemProcessingEngine.process(backpack, helper.level, output, 1) == 0,
                "Normal slots were consumed while refill was disabled",
            )
            helper.assertTrue(
                backpack.inventoryHandler.getStackInSlot(0).count == left.count,
                "Disabled refill changed slot 0",
            )
            helper.assertTrue(
                backpack.inventoryHandler.getStackInSlot(1).count == right.count,
                "Disabled refill changed slot 1",
            )
            helper.assertTrue(wrapper.replaceProcessingSettings(false, true), "Could not enable same-backpack refill")
            helper.assertTrue(
                GemProcessingEngine.process(backpack, helper.level, output, 1) == 1,
                "Enabled refill did not use same-backpack normal slots",
            )
            helper.assertTrue(
                backpack.inventoryHandler.getStackInSlot(0).isEmpty,
                "Left normal material was not consumed",
            )
            helper.assertTrue(
                backpack.inventoryHandler.getStackInSlot(1).isEmpty,
                "Right normal material was not consumed",
            )
            helper.assertTrue(
                otherBackpack.inventoryHandler.getStackInSlot(0).count == left.count,
                "A different backpack supplied material",
            )
            helper.assertTrue(
                fixture.player.inventory.items
                    .all { it.isEmpty },
                "Player inventory supplied material",
            )
        } finally {
            fixture.player.server.playerList
                .remove(fixture.player)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun shiftProcessingStopsAfterSixtyFourSuccessfulCrafts(helper: GameTestHelper) {
        val backpack = advancedBackpack()
        val wrapper = wrapper(backpack)
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val input = GemKey(gem.id.toString(), Purity.CRACKED.serializedName)
        val output = GemKey(gem.id.toString(), Purity.CHIPPED.serializedName)
        val recipe = nativeRecipe(helper, Purity.CRACKED)
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
        helper.assertTrue(
            wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(input to 129))),
            "Could not prepare 129 input Gems",
        )
        helper.assertTrue(wrapper.replaceProcessingSettings(false, true), "Could not enable same-backpack refill")
        val inventory = backpack.inventoryHandler
        val slotAfterLeft = fillStacks(inventory, 0, left, recipe.left().first().count() * 64)
        val slotAfterRight = fillStacks(inventory, slotAfterLeft, right, recipe.right().first().count() * 64)

        val processed = GemProcessingEngine.process(backpack, helper.level, output, 64)

        helper.assertTrue(processed == 64, "Shift operation did not stop at its 64-success limit")
        val counts = (wrapper.snapshot() as GemStorageState.Ready).counts
        helper.assertTrue(
            counts.count(input) == 1 && counts.count(output) == 64,
            "Successful iterations were not retained exactly",
        )
        helper.assertTrue(
            (0 until slotAfterRight).all { inventory.getStackInSlot(it).isEmpty },
            "Processing left unused materials after 64 successful allocations",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun shiftProcessingKeepsSuccessfulPrefixWhenNextCraftLacksMaterials(helper: GameTestHelper) {
        val backpack = advancedBackpack()
        val wrapper = wrapper(backpack)
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val input = GemKey(gem.id.toString(), Purity.CRACKED.serializedName)
        val output = GemKey(gem.id.toString(), Purity.CHIPPED.serializedName)
        val recipe = nativeRecipe(helper, Purity.CRACKED)
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
        helper.assertTrue(
            wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(mapOf(input to 6))),
            "Could not prepare input",
        )
        helper.assertTrue(wrapper.replaceProcessingSettings(false, true), "Could not enable refill")
        val inventory = backpack.inventoryHandler
        val afterLeft = fillStacks(inventory, 0, left, recipe.left().first().count())
        val afterRight = fillStacks(inventory, afterLeft, right, recipe.right().first().count())

        val processed = GemProcessingEngine.process(backpack, helper.level, output, 64)

        helper.assertTrue(processed == 1, "Processing did not stop at the first missing-material iteration")
        val counts = (wrapper.snapshot() as GemStorageState.Ready).counts
        helper.assertTrue(counts.count(input) == 4 && counts.count(output) == 1, "Successful prefix was not retained")
        helper.assertTrue(
            (0 until afterRight).all { inventory.getStackInSlot(it).isEmpty },
            "Materials from the failed next iteration changed inventory",
        )
        helper.succeed()
    }

    private fun advancedBackpack() =
        GemTestFixtures.backpack().apply {
            upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
        }

    private fun wrapper(backpack: net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper) =
        backpack.upgradeHandler.slotWrappers.getValue(0)
            as org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

    private fun materialHandler(
        backpack: net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper,
        helper: GameTestHelper,
    ): GemMaterialHandler = GemMaterialHandler(backpack, helper.level) { true }

    private fun nativeRecipe(
        helper: GameTestHelper,
        purity: Purity,
    ): PurityUpgradeRecipe =
        GemCuttingMenu
            .getRecipes(helper.level)
            .first { holder ->
                (holder.value as? PurityUpgradeRecipe)?.purity() == purity
            }.value as PurityUpgradeRecipe

    private fun nativeCost(
        stack: ItemStack,
        count: Int,
        side: GemMaterialSide,
    ) = org.mocoserver.sophisticatedgemcase.processing.GemMaterialCost(
        BuiltInRegistries.ITEM.getKey(stack.item),
        count,
        side,
    )

    private fun fillStacks(
        inventory: net.neoforged.neoforge.items.IItemHandlerModifiable,
        startSlot: Int,
        prototype: ItemStack,
        amount: Int,
    ): Int {
        var remaining = amount
        var slot = startSlot
        while (remaining > 0) {
            val moved = minOf(remaining, 64)
            inventory.setStackInSlot(slot, prototype.copyWithCount(moved))
            remaining -= moved
            slot++
        }
        return slot
    }
}
