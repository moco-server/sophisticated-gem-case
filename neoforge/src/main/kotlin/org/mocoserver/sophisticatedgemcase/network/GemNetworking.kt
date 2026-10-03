package org.mocoserver.sophisticatedgemcase.network

import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemMenuActions
import org.mocoserver.sophisticatedgemcase.menu.GemSettingsActions

/** commonはclient型を参照せず、client setupで渡された受信callbackだけを呼ぶ。 */
internal object GemNetworking {
    private var clientConsumer: (GemPage) -> Unit = {}
    private var settingsClientConsumer: (GemSettingsPage) -> Unit = {}

    fun installClientConsumer(consumer: (GemPage) -> Unit) {
        clientConsumer = consumer
    }

    fun installSettingsClientConsumer(consumer: (GemSettingsPage) -> Unit) {
        settingsClientConsumer = consumer
    }

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer(GemRequest.TYPE, GemRequest.CODEC) { payload, context ->
            context.enqueueWork { (context.player() as? ServerPlayer)?.let { receive(it, payload) } }
        }
        registrar.playToServer(GemSettingsRequest.TYPE, GemSettingsRequest.CODEC) { payload, context ->
            context.enqueueWork { (context.player() as? ServerPlayer)?.let { receiveSettings(it, payload) } }
        }
        registrar.playToClient(GemPage.TYPE, GemPage.CODEC) { payload, context ->
            context.enqueueWork { clientConsumer(payload) }
        }
        registrar.playToClient(GemSettingsPage.TYPE, GemSettingsPage.CODEC) { payload, context ->
            context.enqueueWork { settingsClientConsumer(payload) }
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
                    PacketDistributor.sendToPlayer(
                        player,
                        session.settingsPage(menu.containerId, container.upgradeContainerId, 0),
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
                if (accepted && request.action == GemAction.REQUEST_PAGE) {
                    PacketDistributor.sendToPlayer(
                        player,
                        current.page(menu.containerId, request.upgradeSlot, request.page),
                    )
                } else {
                    sendGeneration(player, menu, container, current)
                }
            }
        }
    }

    private fun receiveSettings(
        player: ServerPlayer,
        request: GemSettingsRequest,
    ) {
        val menu = player.containerMenu as? StorageContainerMenuBase<*>
        val container = menu?.upgradeContainers?.get(request.upgradeSlot) as? GemCaseContainer
        val bound = menu != null && container != null && menu.containerId == request.menuId
        if (bound && requireNotNull(menu).stillValid(player)) {
            val currentMenu = requireNotNull(menu)
            val currentContainer = requireNotNull(container)
            val session = currentContainer.sessionFor(currentMenu)
            if (session.allowRequest(player.level().gameTime)) {
                val accepted = GemSettingsActions.perform(player, request)
                if (!accepted && request.action != GemSettingsAction.REQUEST_PAGE) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component
                            .translatable("sophisticated_gem_case.error.operation"),
                        true,
                    )
                }
                val current = currentContainer.sessionFor(currentMenu)
                if (accepted && request.action == GemSettingsAction.REQUEST_PAGE) {
                    PacketDistributor.sendToPlayer(
                        player,
                        current.settingsPage(currentMenu.containerId, request.upgradeSlot, request.page),
                    )
                } else {
                    sendGeneration(player, currentMenu, currentContainer, current)
                }
            }
        }
    }

    private fun sendGeneration(
        player: ServerPlayer,
        menu: StorageContainerMenuBase<*>,
        container: GemCaseContainer,
        session: org.mocoserver.sophisticatedgemcase.menu.GemMenuSession,
    ) {
        PacketDistributor.sendToPlayer(player, session.page(menu.containerId, container.upgradeContainerId, 0))
        PacketDistributor.sendToPlayer(player, session.settingsPage(menu.containerId, container.upgradeContainerId, 0))
        session.sentRevision = session.revision
    }
}
