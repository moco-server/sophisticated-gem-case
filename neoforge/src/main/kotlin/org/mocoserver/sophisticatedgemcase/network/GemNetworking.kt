package org.mocoserver.sophisticatedgemcase.network

import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMenuActions

/** commonはclient型を参照せず、client setupで渡された受信callbackだけを呼ぶ。 */
internal object GemNetworking {
    private var clientConsumer: (GemPage) -> Unit = {}

    fun installClientConsumer(consumer: (GemPage) -> Unit) {
        clientConsumer = consumer
    }

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer(GemRequest.TYPE, GemRequest.CODEC) { payload, context ->
            context.enqueueWork { (context.player() as? ServerPlayer)?.let { receive(it, payload) } }
        }
        registrar.playToClient(GemPage.TYPE, GemPage.CODEC) { payload, context ->
            context.enqueueWork { clientConsumer(payload) }
        }
    }

    fun broadcast(menu: StorageContainerMenuBase<*>) {
        val player = (menu as org.mocoserver.sophisticatedgemcase.mixin.GemMergeInvoker).gemCasePlayer()
        if (player is ServerPlayer && player.containerMenu === menu) {
            menu.upgradeContainers.values.filterIsInstance<GemCaseContainer>().forEach { container ->
                val session = requireNotNull(container).sessionFor(menu)
                if (session.sentRevision != session.revision) {
                    PacketDistributor.sendToPlayer(
                        player,
                        session.page(menu.containerId, container.upgradeContainerId, 0),
                    )
                    session.sentRevision = session.revision
                }
            }
        }
    }

    private fun receive(
        player: ServerPlayer,
        request: GemRequest,
    ) {
        val menu = player.containerMenu as? StorageContainerMenuBase<*>
        val container = menu?.upgradeContainers?.get(request.upgradeSlot) as? GemCaseContainer
        val bound = menu != null && container != null && menu.containerId == request.menuId
        if (bound && requireNotNull(menu).stillValid(player)) {
            val session = requireNotNull(container).sessionFor(menu)
            if (session.allowRequest(player.level().gameTime)) {
                val accepted = GemMenuActions.perform(player, request)
                if (!accepted && request.action != GemAction.REQUEST_PAGE) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component
                            .translatable("sophisticated_gem_case.error.operation"),
                        true,
                    )
                }
                val current = container.sessionFor(menu)
                val page = if (accepted && request.action == GemAction.REQUEST_PAGE) request.page else 0
                PacketDistributor.sendToPlayer(player, current.page(menu.containerId, request.upgradeSlot, page))
            }
        }
    }
}
