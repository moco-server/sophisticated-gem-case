package org.mocoserver.sophisticatedgemcase.client

import net.minecraft.network.chat.Component
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeSettingsTab
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer

/** 右側のアイコンを入口にし、狭い画面でも横にパネルを追加しない。 */
internal class GemCaseTab(
    private val container: GemCaseContainer,
    position: Position,
    screen: StorageScreenBase<*>,
) : UpgradeSettingsTab<GemCaseContainer>(container, position, screen, gemLabel("title"), gemLabel("open")) {
    override fun moveSlotsToTab() = Unit

    // 本文のない入口へ旧タブの展開状態を復元すると、Coreが高さを0にしてクリック不能になる。
    override fun onAfterInit() = Unit

    override fun onTabIconClicked(button: Int) {
        if (button == 0) screen.setModalOverlay(GemCaseModal(container, screen))
    }
}

internal fun gemLabel(
    key: String,
    vararg arguments: Any,
): Component = Component.translatable("sophisticated_gem_case.gui.$key", *arguments)
