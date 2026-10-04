package org.mocoserver.sophisticatedgemcase.processing

import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.crafting.SizedIngredient
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import org.mocoserver.sophisticatedgemcase.core.GemMaterialAllocation
import org.mocoserver.sophisticatedgemcase.core.GemMaterialAllocator
import org.mocoserver.sophisticatedgemcase.core.GemMaterialCandidate
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialContents

/** native 6枠走査と補充候補の順序を作者実装に合わせて解決する。 */
internal object GemProcessingRecipeSelector {
    @Suppress("ReturnCount") // recipe予算枯渇と最初に成立したnative recipeを早期確定する。
    fun findDedicated(
        recipes: List<GemPurityRecipes.Recipe>,
        inputPurity: dev.shadowsoffire.apotheosis.socket.gem.Purity,
        materials: GemMaterialContents,
        budget: GemProcessingBudget,
    ): GemSelectionScan {
        for (recipe in recipes) {
            if (!budget.consumeRecipe()) return GemSelectionScan(exhausted = true)
            val result = dedicatedSelection(recipe, inputPurity, materials, budget)
            if (result.exhausted || result.selection != null) return result
        }
        return GemSelectionScan()
    }

    @Suppress("ReturnCount") // purity・budget・成立slot・core配分の拒否境界を区別する。
    private fun dedicatedSelection(
        recipe: GemPurityRecipes.Recipe,
        inputPurity: dev.shadowsoffire.apotheosis.socket.gem.Purity,
        materials: GemMaterialContents,
        budget: GemProcessingBudget,
    ): GemSelectionScan {
        if (recipe.value.purity() != inputPurity) return GemSelectionScan()
        val nativeScan = nativeSlots(recipe.value.left(), recipe.value.right(), materials, budget)
        if (nativeScan.exhausted) return GemSelectionScan(exhausted = true)
        val native = nativeScan.slots ?: return GemSelectionScan()
        val candidates =
            listOf(
                GemMaterialCandidate(native.left.slot, native.left.stack.count, true, false),
                GemMaterialCandidate(native.right.slot, native.right.stack.count, false, true),
            )
        val allocation =
            GemMaterialAllocator.allocate(
                candidates,
                native.left.ingredient.count(),
                native.right.ingredient.count(),
            ) ?: return GemSelectionScan()
        return GemSelectionScan(
            selection =
                GemProcessingSelection(
                    recipe = recipe,
                    pair =
                        GemProcessingCandidatePair(
                            native.left.ordinal,
                            native.left.ingredient.count(),
                            native.right.ordinal,
                            native.right.ingredient.count(),
                        ),
                    allocation = allocation,
                    stacksBySlot =
                        mapOf(
                            native.left.slot to native.left.stack.copy(),
                            native.right.slot to native.right.stack.copy(),
                        ),
                    normalChanges = emptyList(),
                ),
        )
    }

    @Suppress("ReturnCount") // Ingredient予算超過は部分一致を使わず処理全体を止める。
    private fun nativeSlots(
        left: List<SizedIngredient>,
        right: List<SizedIngredient>,
        materials: GemMaterialContents,
        budget: GemProcessingBudget,
    ): GemNativeScan {
        val search = NativeSearch(left, right, budget)
        for (slot in 0 until GemMaterialContents.SLOT_COUNT) {
            if (!search.scan(slot, materials)) return GemNativeScan(exhausted = true)
        }
        return GemNativeScan(search.result(materials))
    }

    @Suppress("ReturnCount") // 一致、予算枯渇、全候補不一致は異なる走査結果である。
    private fun firstSizedMatch(
        stack: ItemStack,
        ingredients: List<SizedIngredient>,
        budget: GemProcessingBudget,
    ): GemIngredientMatch {
        for ((ordinal, ingredient) in ingredients.withIndex()) {
            if (!budget.consumeIngredientTest()) return GemIngredientMatch(exhausted = true)
            if (ingredient.test(stack)) return GemIngredientMatch(ordinal)
        }
        return GemIngredientMatch()
    }

    @Suppress("ReturnCount") // slot上限超過と走査budget枯渇は加工しない境界として返す。
    fun normalCandidates(
        storage: IStorageWrapper,
        materials: GemMaterialContents,
        budget: GemProcessingBudget,
    ): List<GemCandidateStack>? {
        val candidates = mutableListOf<GemCandidateStack>()
        for (slot in 0 until GemMaterialContents.SLOT_COUNT) {
            val stack = materials.stackAt(slot)
            if (!stack.isEmpty) candidates.add(GemCandidateStack(slot, stack))
        }
        val inventory = storage.inventoryHandler
        if (inventory.slots > MAX_NORMAL_SLOTS) return null
        for (slot in 0 until inventory.slots) {
            if (!budget.consumeNormalSlot()) return null
            val stack = inventory.getStackInSlot(slot)
            if (!stack.isEmpty) {
                candidates.add(GemCandidateStack(slot + GemMaterialContents.SLOT_COUNT, stack.copy()))
            }
        }
        return candidates
    }

    @Suppress("ReturnCount") // recipe候補の順序とbudget境界で最初の確定案を返す。
    fun findSupplemented(
        recipes: List<GemPurityRecipes.Recipe>,
        inputPurity: dev.shadowsoffire.apotheosis.socket.gem.Purity,
        candidates: List<GemCandidateStack>,
        budget: GemProcessingBudget,
    ): GemSelectionScan {
        for (recipe in recipes) {
            if (!budget.consumeRecipe()) return GemSelectionScan(exhausted = true)
            if (recipe.value.purity() == inputPurity) {
                val result = recipeSelection(recipe, candidates, budget)
                if (result.exhausted || result.selection != null) return result
            }
        }
        return GemSelectionScan()
    }

    @Suppress("ReturnCount") // pair budget枯渇をpartial selectionより優先する。
    private fun recipeSelection(
        recipe: GemPurityRecipes.Recipe,
        candidates: List<GemCandidateStack>,
        budget: GemProcessingBudget,
    ): GemSelectionScan {
        for ((leftOrdinal, left) in recipe.value.left().withIndex()) {
            for ((rightOrdinal, right) in recipe.value.right().withIndex()) {
                val pair = GemIngredientPair(leftOrdinal, left, rightOrdinal, right)
                val result = tryPair(recipe, candidates, budget, pair)
                if (result.exhausted || result.selection != null) return result
            }
        }
        return GemSelectionScan()
    }

    @Suppress("ReturnCount") // pair予算・候補照合・左右配分の結果を全て失敗理由へ変換する。
    private fun tryPair(
        recipe: GemPurityRecipes.Recipe,
        candidates: List<GemCandidateStack>,
        budget: GemProcessingBudget,
        pair: GemIngredientPair,
    ): GemSelectionScan {
        if (!budget.consumeCandidatePair()) return GemSelectionScan(exhausted = true)
        val matched =
            matchCandidates(candidates, pair.left, pair.right, budget)
                ?: return GemSelectionScan(exhausted = true)
        val allocation =
            GemMaterialAllocator.allocate(matched, pair.left.count(), pair.right.count())
                ?: return GemSelectionScan()
        return GemSelectionScan(
            selection =
                GemProcessingSelection(
                    recipe,
                    GemProcessingCandidatePair(
                        pair.leftOrdinal,
                        pair.left.count(),
                        pair.rightOrdinal,
                        pair.right.count(),
                    ),
                    allocation,
                    candidates.associate { it.slot to it.stack.copy() },
                    normalChanges(candidates, allocation),
                ),
        )
    }

    @Suppress("ReturnCount") // ingredient予算が尽きた部分結果は使用しない。
    private fun matchCandidates(
        candidates: List<GemCandidateStack>,
        left: SizedIngredient,
        right: SizedIngredient,
        budget: GemProcessingBudget,
    ): List<GemMaterialCandidate>? {
        val result = mutableListOf<GemMaterialCandidate>()
        for (candidate in candidates) {
            if (!budget.consumeIngredientTest()) return null
            val matchesLeft = left.ingredient().test(candidate.stack)
            if (!budget.consumeIngredientTest()) return null
            val matchesRight = right.ingredient().test(candidate.stack)
            result.add(GemMaterialCandidate(candidate.slot, candidate.stack.count, matchesLeft, matchesRight))
        }
        return result
    }

    private fun normalChanges(
        candidates: List<GemCandidateStack>,
        allocation: GemMaterialAllocation,
    ): List<NormalSlotChange> {
        val stacks = candidates.associate { it.slot to it.stack }
        return allocation.uses.mapNotNull { (candidateSlot, used) ->
            if (candidateSlot < GemMaterialContents.SLOT_COUNT) return@mapNotNull null
            val stack = stacks.getValue(candidateSlot)
            val remaining = stack.count - used
            val after = if (remaining == 0) ItemStack.EMPTY else stack.copyWithCount(remaining)
            NormalSlotChange(candidateSlot - GemMaterialContents.SLOT_COUNT, stack, after)
        }
    }

    private class NativeSearch(
        private val left: List<SizedIngredient>,
        private val right: List<SizedIngredient>,
        private val budget: GemProcessingBudget,
    ) {
        private var leftSlot: Int = -1
        private var rightSlot: Int = -1
        private var leftOrdinal: Int = -1
        private var rightOrdinal: Int = -1

        @Suppress("ReturnCount") // slotとIngredient予算の拒否を部分一致から分離する。
        fun scan(
            slot: Int,
            materials: GemMaterialContents,
        ): Boolean {
            val stack = materials.stackAt(slot)
            if (stack.isEmpty) return true
            if (!selectLeft(slot, stack)) return false
            if (leftSlot == slot || rightSlot >= 0) return true
            val rightMatch = GemProcessingRecipeSelector.firstSizedMatch(stack, right, budget)
            if (rightMatch.exhausted) return false
            if (rightMatch.ordinal >= 0) {
                rightSlot = slot
                rightOrdinal = rightMatch.ordinal
            }
            return true
        }

        fun result(materials: GemMaterialContents): GemNativeSlots? =
            if (leftSlot >= 0 && rightSlot >= 0) {
                GemNativeSlots(
                    GemNativeSlotMatch(leftSlot, leftOrdinal, left[leftOrdinal], materials.stackAt(leftSlot)),
                    GemNativeSlotMatch(rightSlot, rightOrdinal, right[rightOrdinal], materials.stackAt(rightSlot)),
                )
            } else {
                null
            }

        @Suppress("ReturnCount") // 既存leftと予算枯渇の境界を区別する。
        private fun selectLeft(
            slot: Int,
            stack: ItemStack,
        ): Boolean {
            if (leftSlot >= 0) return true
            val match = GemProcessingRecipeSelector.firstSizedMatch(stack, left, budget)
            if (match.exhausted) return false
            if (match.ordinal >= 0) {
                leftSlot = slot
                leftOrdinal = match.ordinal
            }
            return true
        }
    }

    private const val MAX_NORMAL_SLOTS: Int = 8_192
}

internal data class GemCandidateStack(
    val slot: Int,
    val stack: ItemStack,
)

internal data class GemProcessingSelection(
    val recipe: GemPurityRecipes.Recipe,
    val pair: GemProcessingCandidatePair,
    val allocation: GemMaterialAllocation,
    val stacksBySlot: Map<Int, ItemStack>,
    val normalChanges: List<NormalSlotChange>,
)

internal data class GemProcessingCandidatePair(
    val leftOrdinal: Int,
    val leftRequired: Int,
    val rightOrdinal: Int,
    val rightRequired: Int,
)

internal data class GemSelectionScan(
    val selection: GemProcessingSelection? = null,
    val exhausted: Boolean = false,
)

private data class GemNativeSlots(
    val left: GemNativeSlotMatch,
    val right: GemNativeSlotMatch,
)

private data class GemNativeSlotMatch(
    val slot: Int,
    val ordinal: Int,
    val ingredient: SizedIngredient,
    val stack: ItemStack,
)

private data class GemNativeScan(
    val slots: GemNativeSlots? = null,
    val exhausted: Boolean = false,
)

private data class GemIngredientMatch(
    val ordinal: Int = -1,
    val exhausted: Boolean = false,
)

private data class GemIngredientPair(
    val leftOrdinal: Int,
    val left: SizedIngredient,
    val rightOrdinal: Int,
    val right: SizedIngredient,
)
