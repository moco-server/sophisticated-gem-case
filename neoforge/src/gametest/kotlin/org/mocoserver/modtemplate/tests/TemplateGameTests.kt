package org.mocoserver.modtemplate.tests

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeType
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.modtemplate.ModTemplate
import org.mocoserver.modtemplate.core.checkedQuantity

/** 本番item・core契約・実際のrecipe lookupを別source setで検査する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object TemplateGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun registryAndQuantity(helper: GameTestHelper) {
        val id = ResourceLocation.fromNamespaceAndPath(ModTemplate.ID, "sample_token")
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "Sample item is missing")
        helper.assertTrue(checkedQuantity(0) == 0 && checkedQuantity(1) == 1, "Quantity contract failed")
        val failure = runCatching { checkedQuantity(-1) }.exceptionOrNull()
        helper.assertTrue(failure is IllegalArgumentException, "Negative quantity must be rejected")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun craftingRecipe(helper: GameTestHelper) {
        val input = CraftingInput.of(2, 1, listOf(ItemStack(Items.IRON_INGOT), ItemStack(Items.PAPER)))
        val recipe =
            helper.level.recipeManager
                .getRecipeFor(RecipeType.CRAFTING, input, helper.level)
                .orElseThrow { IllegalStateException("Sample recipe is missing") }
        val result = recipe.value().assemble(input, helper.level.registryAccess())
        val expected = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ModTemplate.ID, "sample_token"))
        helper.assertTrue(result.`is`(expected) && result.count == 1, "Recipe result must be one sample token")
        helper.succeed()
    }
}
