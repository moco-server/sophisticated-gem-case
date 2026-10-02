package org.mocoserver.sophisticatedgemcase.client

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeGuiManager
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.network.GemNetworking

/**
 * side条件で読み込む。commonの初期化クラスからclient型を参照しない。
 * KFF 5.5.0のGAME登録はloader 4.0.44の削除済みBindingsを参照するためMODを明示する。
 */
@Suppress("DEPRECATION")
@EventBusSubscriber(modid = ModTemplate.ID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
internal object GemCaseClient {
    @SubscribeEvent
    fun setup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            GemNetworking.installClientConsumer(GemPageCache::receive)
            UpgradeGuiManager.registerTab(GemCaseContainer.TYPE, ::GemCaseTab)
        }
    }
}
