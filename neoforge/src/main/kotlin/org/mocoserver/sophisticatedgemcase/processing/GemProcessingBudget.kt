package org.mocoserver.sophisticatedgemcase.processing

/** 一つの計画だけが所有する上限計数。上限を越える探索は部分結果を採用しない。 */
internal class GemProcessingBudget {
    private var recipes: Int = 0
    private var candidatePairs: Int = 0
    private var ingredientTests: Int = 0
    private var normalSlots: Int = 0

    fun consumeRecipe(): Boolean {
        if (recipes >= MAX_RECIPES) return false
        recipes++
        return true
    }

    fun consumeCandidatePair(): Boolean {
        if (candidatePairs >= MAX_CANDIDATE_PAIRS) return false
        candidatePairs++
        return true
    }

    fun consumeIngredientTest(): Boolean {
        if (ingredientTests >= MAX_INGREDIENT_TESTS) return false
        ingredientTests++
        return true
    }

    fun consumeNormalSlot(): Boolean {
        if (normalSlots >= MAX_NORMAL_SLOTS) return false
        normalSlots++
        return true
    }

    private companion object {
        const val MAX_RECIPES: Int = 512
        const val MAX_CANDIDATE_PAIRS: Int = 4_096
        const val MAX_INGREDIENT_TESTS: Int = 16_384
        const val MAX_NORMAL_SLOTS: Int = 8_192
    }
}
