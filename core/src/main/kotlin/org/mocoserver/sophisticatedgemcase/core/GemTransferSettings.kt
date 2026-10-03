package org.mocoserver.sophisticatedgemcase.core

/** 自動転送の方向。 */
public enum class GemTransferDirection {
    DEPOSIT,
    RESTOCK,
}

/** 一方向の自動転送可否と対象条件を表す不変値。 */
public data class GemTransferRule(
    public val enabled: Boolean,
    public val filter: GemFilter,
)

/** DepositとRestockを独立して制御する不変設定。 */
public data class GemTransferSettings(
    public val deposit: GemTransferRule,
    public val restock: GemTransferRule,
) {
    public companion object {
        /** 新規装着時は意図しない自動移動を防ぐため、両方向を無効にする。 */
        public val DEFAULT: GemTransferSettings =
            GemTransferSettings(
                GemTransferRule(false, GemFilter.of(GemFilterMode.ALL, emptySet(), emptySet())),
                GemTransferRule(false, GemFilter.of(GemFilterMode.ALL, emptySet(), emptySet())),
            )
    }
}
