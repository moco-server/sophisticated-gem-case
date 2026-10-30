package org.mocoserver.sophisticatedgemcase.client

/** 中央表示の収納・転送設定を切り替え、素材の表示許可を更新する。 */
internal class GemCaseViewState(
    private val onViewChanged: () -> Unit,
) {
    var view: GemCaseView = GemCaseView.STORAGE
        set(value) {
            if (field != value) {
                field = value
                onViewChanged()
            }
        }
}

internal enum class GemCaseView {
    STORAGE,
    SETTINGS,
}
