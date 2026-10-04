package org.mocoserver.sophisticatedgemcase.processing

import dev.shadowsoffire.apotheosis.socket.gem.Purity
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingMenu
import dev.shadowsoffire.apotheosis.socket.gem.cutting.PurityUpgradeRecipe
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/** 固定Apotheosis recipe managerから毎回現在の純度recipeを解決する。 */
internal object GemPurityRecipes {
    /** 左右どちらかの現行純度recipeに適合する材料だけを新規投入可能とする。 */
    internal fun isMaterial(
        level: Level,
        stack: ItemStack,
    ): Boolean =
        !stack.isEmpty &&
            recipes(level).any { recipe ->
                recipe.value.left().any { it.ingredient().test(stack) } ||
                    recipe.value.right().any { it.ingredient().test(stack) }
            }

    internal fun recipes(level: Level): List<Recipe> =
        GemCuttingMenu.getRecipes(level).mapNotNull { holder ->
            val recipe = holder.value as? PurityUpgradeRecipe ?: return@mapNotNull null
            Recipe(holder.id, recipe)
        }

    internal fun inputPurity(outputPurity: String): Purity? {
        val output = Purity.entries.firstOrNull { it.serializedName == outputPurity } ?: return null
        return Purity.entries.getOrNull(output.ordinal - 1)
    }

    internal data class Recipe(
        val id: net.minecraft.resources.ResourceLocation,
        val value: PurityUpgradeRecipe,
    )
}
