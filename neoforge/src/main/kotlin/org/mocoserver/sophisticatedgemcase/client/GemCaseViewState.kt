package org.mocoserver.sophisticatedgemcase.client

/** 収納・転送設定・加工のview選択を同じタブ内で共有する。 */
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
    PROCESSING,
}
