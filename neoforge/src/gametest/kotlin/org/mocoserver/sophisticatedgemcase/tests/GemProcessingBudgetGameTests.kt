package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.processing.GemCandidateStack
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingBudget
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingRecipeSelector
import org.mocoserver.sophisticatedgemcase.processing.GemPurityRecipes

/** 補充の再走査もnative走査と同じ計画予算を共有する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemProcessingBudgetGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsSupplementedSelectionAfterRecipeBudgetIsExhausted(helper: GameTestHelper) {
        val recipe = GemPurityRecipes.recipes(helper.level).first { it.value.purity() == Purity.CRACKED }
        val left = recipe.value.left().first()
        val right = recipe.value.right().first()
        val candidates =
            listOf(
                GemCandidateStack(6, left.getItems().first().copyWithCount(left.count())),
                GemCandidateStack(7, right.getItems().first().copyWithCount(right.count())),
            )
        val budget = GemProcessingBudget()
        repeat(512) { helper.assertTrue(budget.consumeRecipe(), "Recipe budget exhausted before its limit") }
        val result = GemProcessingRecipeSelector.findSupplemented(listOf(recipe), Purity.CRACKED, candidates, budget)
        helper.assertTrue(
            result.exhausted && result.selection == null,
            "Supplemented pass ignored shared recipe budget",
        )
        helper.succeed()
    }
}
