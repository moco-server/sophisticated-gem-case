package org.mocoserver.sophisticatedgemcase.client

import net.minecraft.client.Minecraft
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.mixin.client.GemStorageScreenAccessor

/** 表示破棄時は素材許可を解放する。closeは複数回呼ばれても安全にする。 */
internal interface GemCaseModalContent {
    fun close()
}

/** CoreのEscape・外側クリックも、画面終了と同じ後処理へ接続する。 */
internal object GemCaseModalLifecycle {
    @JvmStatic
    fun closePrevious(previous: WidgetBase?) {
        (previous as? GemCaseModalContent)?.close()
    }

    @JvmStatic
    fun closeOverlay(screen: StorageScreenBase<*>) {
        val overlay = (screen as GemStorageScreenAccessor).gemCaseModalOverlay()
        if (overlay is GemCaseModalContent) screen.setModalOverlay(null)
    }

    fun closeOverlayLater(screen: StorageScreenBase<*>) {
        val accessor = screen as GemStorageScreenAccessor
        val overlay = accessor.gemCaseModalOverlay()
        // CoreのrenderはrenderWidgetの後もfieldを参照するため、描画中にnullへ変えない。
        Minecraft.getInstance().tell {
            if (accessor.gemCaseModalOverlay() === overlay) closeOverlay(screen)
        }
    }

    fun isCurrent(
        screen: StorageScreenBase<*>,
        container: GemCaseContainer,
    ): Boolean {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return false
        val sameMenu = minecraft.screen === screen && player.containerMenu === screen.menu
        val sameUpgrade =
            screen.menu.upgradeContainers[container.upgradeContainerId] === container &&
                container.isCurrent(screen.menu)
        return sameMenu && sameUpgrade && screen.menu.stillValid(player)
    }
}
