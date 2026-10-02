package org.mocoserver.sophisticatedgemcase.core

import java.util.Collections

/** 所有する不変の個数表。操作は新しい値を返し、入力と過去の状態を変更しない。 */
public class GemCounts private constructor(
    private val values: Map<GemKey, Int>,
) {
    public fun count(key: GemKey): Int = values[key] ?: 0

    public fun entries(): Map<GemKey, Int> = values

    /** 負数は契約違反。容量を超えた個数を余りとして返す。 */
    public fun insert(
        key: GemKey,
        amount: Int,
    ): GemCountChange {
        require(amount >= 0)
        val moved = minOf(amount, CAPACITY - count(key))
        return change(key, count(key) + moved, moved, amount - moved)
    }

    /** 負数は契約違反。不足分を余りとして返し、ゼロ個のキーを残さない。 */
    public fun extract(
        key: GemKey,
        amount: Int,
    ): GemCountChange {
        require(amount >= 0)
        val moved = minOf(amount, count(key))
        return change(key, count(key) - moved, moved, amount - moved)
    }

    private fun change(
        key: GemKey,
        count: Int,
        moved: Int,
        remainder: Int,
    ): GemCountChange {
        val next = values.toMutableMap()
        if (count == 0) next.remove(key) else next[key] = count
        return GemCountChange(of(next), moved, remainder)
    }

    public override fun equals(other: Any?): Boolean = other is GemCounts && values == other.values

    public override fun hashCode(): Int = values.hashCode()

    public companion object {
        public const val CAPACITY: Int = 32_767

        /** 入力をコピーする。負数と容量超過は契約違反。ゼロのキーは保存しない。 */
        public fun of(entries: Map<GemKey, Int>): GemCounts {
            require(entries.values.all { it in 0..CAPACITY })
            return GemCounts(Collections.unmodifiableMap(entries.filterValues { it > 0 }.toMap()))
        }
    }
}

/** 個数操作の結果。remainderは要求のうち移動しなかった個数。 */
public data class GemCountChange(
    public val state: GemCounts,
    public val moved: Int,
    public val remainder: Int,
)
