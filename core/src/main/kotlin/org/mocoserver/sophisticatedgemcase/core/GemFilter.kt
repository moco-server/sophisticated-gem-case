package org.mocoserver.sophisticatedgemcase.core

import java.util.Collections

/** 集合の照合方法。INCLUDE／EXCLUDEは種類と純度の両軸が一致した場合に適用する。 */
public enum class GemFilterMode {
    ALL,
    INCLUDE,
    EXCLUDE,
}

/** アイテムゲーム型を含まない、所有権を持つ不変のGemフィルタ。 */
public class GemFilter private constructor(
    public val mode: GemFilterMode,
    public val gemIds: Set<String>,
    public val purities: Set<String>,
) {
    /** ALLは常に一致し、空の軸は制限しない。INCLUDEの両軸空は不一致となる。 */
    public fun matches(key: GemKey): Boolean {
        if (mode == GemFilterMode.ALL) return true
        val hasConstraint = gemIds.isNotEmpty() || purities.isNotEmpty()
        val matches =
            hasConstraint &&
                (gemIds.isEmpty() || key.gemId in gemIds) &&
                (purities.isEmpty() || key.purity in purities)
        return if (mode == GemFilterMode.INCLUDE) matches else !matches
    }

    public override fun equals(other: Any?): Boolean =
        other is GemFilter && mode == other.mode && gemIds == other.gemIds && purities == other.purities

    public override fun hashCode(): Int = 31 * (31 * mode.hashCode() + gemIds.hashCode()) + purities.hashCode()

    public companion object {
        /** 集合をコピーし、呼出側の後続変更や取得側の変更から値を保護する。 */
        public fun of(
            mode: GemFilterMode,
            gemIds: Set<String>,
            purities: Set<String>,
        ): GemFilter =
            GemFilter(
                mode,
                Collections.unmodifiableSet(java.util.LinkedHashSet(gemIds)),
                Collections.unmodifiableSet(java.util.LinkedHashSet(purities)),
            )
    }
}
