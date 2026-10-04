package org.mocoserver.sophisticatedgemcase.tests

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeType
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec

/** 通常craftの入手材料と出力が承認済みrecipe仕様に一致することを確認する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemAcquisitionRecipeGameTests {
    private val lowerRecipeId = id("gem_case_upgrade")
    private val conversionRecipeId = id("gem_case_upgrade_conversion")

    @JvmStatic
    @GameTest(template = "empty")
    fun craftsLowerFromNineIngredients(helper: GameTestHelper) {
        val input = CraftingInput.of(3, 3, lowerIngredients())
        val recipe = matchingRecipe(helper, input)
        helper.assertTrue(recipe?.id == lowerRecipeId, "Lower Gem Case recipe is missing or has a different ID")
        val result = requireNotNull(recipe).value().assemble(input, helper.level.registryAccess())

        helper.assertTrue(
            result.`is`(ModItems.gemCaseUpgrade.get()) && result.count == 1,
            "Lower recipe output differs",
        )
        assertFreshUpgradeComponents(helper, result)
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun craftsConversionFromNineIngredients(helper: GameTestHelper) {
        val input = CraftingInput.of(3, 3, conversionIngredients())
        val recipe = matchingRecipe(helper, input)
        helper.assertTrue(recipe?.id == conversionRecipeId, "Conversion recipe is missing or has a different ID")
        val result = requireNotNull(recipe).value().assemble(input, helper.level.registryAccess())

        helper.assertTrue(
            result.`is`(ModItems.gemCaseUpgradeConversion.get()) && result.count == 1,
            "Conversion recipe output differs",
        )
        assertFreshUpgradeComponents(helper, result)
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun acceptsTaggedGlassButRejectsMissingOrWrongMaterials(helper: GameTestHelper) {
        val lower = lowerIngredients().toMutableList()
        for (slot in 0..2) {
            lower[slot] = ItemStack(Items.RED_STAINED_GLASS)
        }
        assertMatches(helper, lower, lowerRecipeId, "Colored glass in the lower recipe was rejected")
        assertNineIngredients(helper, lower, lowerRecipeId, "Lower recipe")
        assertRequiredCount(helper, lower, lowerRecipeId, "apotheosis:gem_dust", 4)

        val conversion = conversionIngredients().toMutableList()
        for (slot in 0..2) {
            conversion[slot] = ItemStack(Items.RED_STAINED_GLASS)
        }
        assertMatches(helper, conversion, conversionRecipeId, "Colored glass in the conversion recipe was rejected")
        assertNineIngredients(helper, conversion, conversionRecipeId, "Conversion recipe")

        for ((recipeId, ingredients) in listOf(
            lowerRecipeId to lowerIngredients(),
            conversionRecipeId to conversionIngredients(),
        )) {
            for (slot in 0..8) {
                for (replacement in listOf(ItemStack.EMPTY, ItemStack(Items.DIRT, 64))) {
                    val changed = ingredients.toMutableList()
                    changed[slot] = replacement
                    helper.assertTrue(
                        matchingRecipe(helper, CraftingInput.of(3, 3, changed))?.id != recipeId,
                        "Recipe $recipeId accepted a missing or wrong item in slot $slot",
                    )
                }
            }
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun doesNotCraftAdvancedFromStoredLowerOrNativeGemCase(helper: GameTestHelper) {
        val lowerOccupied =
            ItemStack(ModItems.gemCaseUpgrade.get()).apply {
                set(
                    ModDataComponents.gemStorage.get(),
                    GemStorageCodec.write(GemCounts.of(mapOf(GemKey("future:kept", "normal") to 12))),
                )
            }
        val lowerAttempt = lowerIngredients().toMutableList().apply { this[4] = lowerOccupied }
        helper.assertTrue(
            matchingRecipe(helper, CraftingInput.of(3, 3, lowerAttempt))?.id != lowerRecipeId,
            "Stored lower Gem Case was consumed by the lower recipe",
        )

        val nativeGemCase = item("apotheosis:gem_case")
        val conversionAttempt = conversionIngredients().toMutableList().apply { this[4] = ItemStack(nativeGemCase) }
        helper.assertTrue(
            matchingRecipe(helper, CraftingInput.of(3, 3, conversionAttempt))?.id != conversionRecipeId,
            "Native Gem Case was consumed by the conversion recipe",
        )

        for ((recipeId, ingredients) in listOf(
            lowerRecipeId to lowerIngredients(),
            conversionRecipeId to conversionIngredients(),
        )) {
            val input = CraftingInput.of(3, 3, ingredients)
            val recipe = matchingRecipe(helper, input)
            helper.assertTrue(recipe?.id == recipeId, "Expected recipe $recipeId is missing")
            val result = requireNotNull(recipe).value().assemble(input, helper.level.registryAccess())
            helper.assertTrue(
                !result.`is`(ModItems.advancedGemCaseUpgrade.get()),
                "Recipe $recipeId directly outputs the advanced Gem Case",
            )
        }
        helper.succeed()
    }

    private fun assertFreshUpgradeComponents(
        helper: GameTestHelper,
        stack: ItemStack,
    ) {
        helper.assertTrue(!stack.has(ModDataComponents.gemStorage.get()), "Crafted item has gem storage")
        helper.assertTrue(
            !stack.has(ModDataComponents.gemProcessingMaterials.get()),
            "Crafted item has processing materials",
        )
        helper.assertTrue(
            !stack.has(ModDataComponents.gemProcessingSettings.get()),
            "Crafted item has processing settings",
        )
        helper.assertTrue(!stack.has(ModDataComponents.gemTransferSettings.get()), "Crafted item has transfer settings")
    }

    private fun assertMatches(
        helper: GameTestHelper,
        stacks: List<ItemStack>,
        expectedId: ResourceLocation,
        message: String,
    ) {
        helper.assertTrue(matchingRecipe(helper, CraftingInput.of(3, 3, stacks))?.id == expectedId, message)
    }

    private fun assertNineIngredients(
        helper: GameTestHelper,
        stacks: List<ItemStack>,
        recipeId: ResourceLocation,
        label: String,
    ) {
        val recipe = requireNotNull(matchingRecipe(helper, CraftingInput.of(3, 3, stacks)))
        helper.assertTrue(recipe.id == recipeId, "$label did not match")
        helper.assertTrue(recipe.value().ingredients.size == 9, "$label must define nine ingredient entries")
    }

    private fun assertRequiredCount(
        helper: GameTestHelper,
        stacks: List<ItemStack>,
        recipeId: ResourceLocation,
        itemId: String,
        expected: Int,
    ) {
        val recipe = requireNotNull(matchingRecipe(helper, CraftingInput.of(3, 3, stacks)))
        val ingredientItem = item(itemId)
        val actual = recipe.value().ingredients.count { ingredient -> ingredient.test(ItemStack(ingredientItem)) }
        helper.assertTrue(recipe.id == recipeId && actual == expected, "$recipeId must require $expected of $itemId")
    }

    private fun matchingRecipe(
        helper: GameTestHelper,
        input: CraftingInput,
    ) = helper.level.recipeManager
        .getRecipeFor(RecipeType.CRAFTING, input, helper.level)
        .orElse(null)

    private fun lowerIngredients(): List<ItemStack> =
        listOf(
            ItemStack(Items.GLASS),
            ItemStack(Items.GLASS),
            ItemStack(Items.GLASS),
            ItemStack(item("apotheosis:gem_dust")),
            ItemStack(item("sophisticatedbackpacks:upgrade_base")),
            ItemStack(item("apotheosis:gem_dust")),
            ItemStack(item("apotheosis:gem_dust")),
            ItemStack(Items.CHEST),
            ItemStack(item("apotheosis:gem_dust")),
        )

    private fun conversionIngredients(): List<ItemStack> =
        listOf(
            ItemStack(Items.GLASS),
            ItemStack(Items.GLASS),
            ItemStack(Items.GLASS),
            ItemStack(Items.BASALT),
            ItemStack(item("apotheosis:gem_cutting_table")),
            ItemStack(Items.BASALT),
            ItemStack(Items.BASALT),
            ItemStack(Items.ENDER_CHEST),
            ItemStack(item("sophisticatedbackpacks:upgrade_base")),
        )

    private fun item(id: String): Item {
        val resourceId = ResourceLocation.parse(id)
        check(BuiltInRegistries.ITEM.containsKey(resourceId)) { "Required test item is not registered: $id" }
        return BuiltInRegistries.ITEM.get(resourceId)
    }

    private fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(ModTemplate.ID, path)
}
