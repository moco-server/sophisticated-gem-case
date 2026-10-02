package org.mocoserver.sophisticatedgemcase.core

/** ゲーム型に依存しない保存キー。種類や純度の解決はloader側で行う。 */
public data class GemKey(
    public val gemId: String,
    public val purity: String,
)
