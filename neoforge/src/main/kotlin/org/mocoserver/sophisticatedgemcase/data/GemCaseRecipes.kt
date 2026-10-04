package org.mocoserver.sophisticatedgemcase.data

import net.minecraft.advancements.critereon.InventoryChangeTrigger
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.ShapedRecipeBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.crafting.Ingredient

/** 工程5の通常入手recipeを固定IDで生成する。 */
internal object GemCaseRecipes {
    private val glassBlocks = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "glass_blocks"))

    fun build(output: RecipeOutput) {
        ShapedRecipeBuilder
            .shaped(RecipeCategory.MISC, item("sophisticated_gem_case:gem_case_upgrade"))
            .pattern("GGG")
            .pattern("DBD")
            .pattern("DCD")
            .define('G', Ingredient.of(glassBlocks))
            .define('D', item("apotheosis:gem_dust"))
            .define('B', item("sophisticatedbackpacks:upgrade_base"))
            .define('C', item("minecraft:chest"))
            .unlockedBy(
                "has_gem_dust",
                InventoryChangeTrigger.TriggerInstance.hasItems(item("apotheosis:gem_dust")),
            ).save(output)

        ShapedRecipeBuilder
            .shaped(RecipeCategory.MISC, item("sophisticated_gem_case:gem_case_upgrade_conversion"))
            .pattern("GGG")
            .pattern("BEB")
            .pattern("BNU")
            .define('G', Ingredient.of(glassBlocks))
            .define('B', item("minecraft:basalt"))
            .define('E', item("apotheosis:gem_cutting_table"))
            .define('N', item("minecraft:ender_chest"))
            .define('U', item("sophisticatedbackpacks:upgrade_base"))
            .unlockedBy(
                "has_gem_cutting_table",
                InventoryChangeTrigger.TriggerInstance.hasItems(item("apotheosis:gem_cutting_table")),
            ).save(output)
    }

    private fun item(id: String): Item {
        val resourceId = ResourceLocation.parse(id)
        check(BuiltInRegistries.ITEM.containsKey(resourceId)) { "Recipe item is not registered: $id" }
        val item = BuiltInRegistries.ITEM.get(resourceId)
        check(BuiltInRegistries.ITEM.getKey(item) == resourceId) { "Recipe item ID did not resolve canonically: $id" }
        return item
    }
}
