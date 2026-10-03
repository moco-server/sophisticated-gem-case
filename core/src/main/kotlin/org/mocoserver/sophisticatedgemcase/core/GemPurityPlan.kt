package org.mocoserver.sophisticatedgemcase.core

/** Gem強化後の個数と同時に消費する材料を保持する不変計画。 */
public data class GemPurityPlan(
    public val counts: GemCounts,
    public val materials: GemMaterialAllocation,
)

/** 同種類の隣接純度Gemを2個から1個へまとめる計画を作る。 */
public object GemPurityPlanner {
    private val purities: List<String> = listOf("cracked", "chipped", "flawed", "normal", "flawless", "perfect")

    /**
     * 既知純度の隣接する同種類Gemを2個消費して、指定純度を1個追加する計画を返す。
     * 入力不足、出力容量超過、異なる種類、未知または隣接しない純度ではnullを返し、元countsを変更しない。
     */
    public fun plan(
        counts: GemCounts,
        input: GemKey,
        output: GemKey,
        allocation: GemMaterialAllocation,
    ): GemPurityPlan? {
        val updatedCounts = updatedCountsOrNull(counts, input, output)
        return updatedCounts?.let { GemPurityPlan(it, allocation) }
    }

    private fun updatedCountsOrNull(
        counts: GemCounts,
        input: GemKey,
        output: GemKey,
    ): GemCounts? {
        if (!isAdjacentUpgrade(input, output) || counts.count(input) < INPUT_COUNT) return null
        val afterInput = counts.extract(input, INPUT_COUNT)
        val afterOutput = afterInput.state.insert(output, OUTPUT_COUNT)
        return afterOutput.state.takeIf { afterOutput.remainder == 0 }
    }

    private fun isAdjacentUpgrade(
        input: GemKey,
        output: GemKey,
    ): Boolean {
        if (input.gemId != output.gemId) return false
        val inputIndex = purities.indexOf(input.purity)
        return inputIndex >= 0 && purities.getOrNull(inputIndex + 1) == output.purity
    }

    private const val INPUT_COUNT: Int = 2
    private const val OUTPUT_COUNT: Int = 1
}
