package org.mocoserver.sophisticatedgemcase.core

import java.util.Collections

/** 左右のrecipe要求に対する一つの材料候補。countは未使用個数を表す。 */
public data class GemMaterialCandidate(
    public val slot: Int,
    public val count: Int,
    public val matchesLeft: Boolean,
    public val matchesRight: Boolean,
)

/** 左右それぞれの材料消費量を所有する不変の割当結果。 */
public class GemMaterialAllocation internal constructor(
    leftUses: Map<Int, Int>,
    rightUses: Map<Int, Int>,
) {
    /** 左側の要求に割り当てたslotごとの個数。 */
    public val leftUses: Map<Int, Int> = Collections.unmodifiableMap(leftUses.toMap())

    /** 右側の要求に割り当てたslotごとの個数。 */
    public val rightUses: Map<Int, Int> = Collections.unmodifiableMap(rightUses.toMap())

    /** 左右の消費をslotごとに合算した個数。 */
    public val uses: Map<Int, Int> =
        Collections.unmodifiableMap(
            buildMap {
                this@GemMaterialAllocation.leftUses.forEach { (slot, count) -> put(slot, count) }
                this@GemMaterialAllocation.rightUses.forEach { (slot, count) ->
                    put(slot, (get(slot) ?: 0) + count)
                }
            },
        )

    public override fun equals(other: Any?): Boolean =
        other is GemMaterialAllocation && leftUses == other.leftUses && rightUses == other.rightUses

    public override fun hashCode(): Int = 31 * leftUses.hashCode() + rightUses.hashCode()

    public override fun toString(): String = "GemMaterialAllocation(leftUses=$leftUses, rightUses=$rightUses)"
}

/** 左右の要求を満たすため、候補順を保った材料消費を計算する。 */
public object GemMaterialAllocator {
    /**
     * 左右の適合条件を満たす候補を優先順で割り当てる。
     *
     * 共有候補を左へ割り当てる際は、右側に必要な総数を残す。重複slotや負数は契約違反として
     * IllegalArgumentExceptionを送出し、どちらかの要求を満たせない場合はnullを返す。
     */
    public fun allocate(
        candidates: List<GemMaterialCandidate>,
        leftRequired: Int,
        rightRequired: Int,
    ): GemMaterialAllocation? {
        validate(candidates, leftRequired, rightRequired)
        val leftUses = allocateLeft(candidates, leftRequired, rightRequired) ?: return null
        return allocateRight(candidates, leftUses, rightRequired)?.let { GemMaterialAllocation(leftUses, it) }
    }

    private fun validate(
        candidates: List<GemMaterialCandidate>,
        leftRequired: Int,
        rightRequired: Int,
    ) {
        require(leftRequired >= 0 && rightRequired >= 0)
        val slots = mutableSetOf<Int>()
        candidates.forEach { candidate ->
            require(candidate.slot >= 0)
            require(candidate.count >= 0)
            require(slots.add(candidate.slot))
        }
    }

    private fun allocateLeft(
        candidates: List<GemMaterialCandidate>,
        leftRequired: Int,
        rightRequired: Int,
    ): Map<Int, Int>? {
        val rightCapacityAfter = rightCapacityAfter(candidates)
        val leftUses = mutableMapOf<Int, Int>()
        var leftRemaining = leftRequired.toLong()
        var rightRemaining = rightRequired.toLong()
        candidates.forEachIndexed { index, candidate ->
            val used = leftUse(candidate, leftRemaining, rightRemaining, rightCapacityAfter[index + 1])
            if (used > 0) leftUses[candidate.slot] = used
            leftRemaining -= used.toLong()
            if (candidate.matchesRight) {
                val availableForRight = candidate.count.toLong() - used.toLong()
                rightRemaining -= minOf(rightRemaining, availableForRight)
            }
        }
        return if (leftRemaining == 0L) leftUses else null
    }

    private fun rightCapacityAfter(candidates: List<GemMaterialCandidate>): LongArray {
        val capacity = LongArray(candidates.size + 1)
        for (index in candidates.indices.reversed()) {
            val candidate = candidates[index]
            capacity[index] = capacity[index + 1] +
                if (candidate.matchesRight) candidate.count.toLong() else 0L
        }
        return capacity
    }

    private fun leftUse(
        candidate: GemMaterialCandidate,
        leftRemaining: Long,
        rightRemaining: Long,
        rightCapacityAfter: Long,
    ): Int {
        if (!candidate.matchesLeft || leftRemaining == 0L) return 0
        val requiredFromCandidate =
            if (candidate.matchesRight) {
                maxOf(0L, rightRemaining - rightCapacityAfter)
            } else {
                0L
            }
        val availableForLeft = maxOf(0L, candidate.count.toLong() - requiredFromCandidate)
        return minOf(leftRemaining, availableForLeft).toInt()
    }

    private fun allocateRight(
        candidates: List<GemMaterialCandidate>,
        leftUses: Map<Int, Int>,
        rightRequired: Int,
    ): Map<Int, Int>? {
        val rightUses = mutableMapOf<Int, Int>()
        var rightRemaining = rightRequired.toLong()
        candidates.forEach { candidate ->
            if (candidate.matchesRight && rightRemaining > 0L) {
                val available = candidate.count.toLong() - (leftUses[candidate.slot] ?: 0).toLong()
                val used = minOf(rightRemaining, available).toInt()
                if (used > 0) rightUses[candidate.slot] = used
                rightRemaining -= used.toLong()
            }
        }
        return if (rightRemaining == 0L) rightUses else null
    }
}
