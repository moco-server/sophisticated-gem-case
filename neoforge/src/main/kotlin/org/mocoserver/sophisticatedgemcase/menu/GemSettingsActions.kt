package org.mocoserver.sophisticatedgemcase.menu

import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.core.GemFilter
import org.mocoserver.sophisticatedgemcase.core.GemTransferDirection
import org.mocoserver.sophisticatedgemcase.core.GemTransferRule
import org.mocoserver.sophisticatedgemcase.core.GemTransferSettings
import org.mocoserver.sophisticatedgemcase.network.GemSettingsAction
import org.mocoserver.sophisticatedgemcase.network.GemSettingsRequest
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsCodec
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsState
import java.nio.charset.StandardCharsets

/**
 * 現在のserver menuと一つの型付き操作からだけ設定を更新する。
 * 同じ設定要求境界の権限検証とaction別更新を小さい関数へ分けて保つ。
 */
@Suppress("TooManyFunctions")
internal object GemSettingsActions {
    private enum class SettingValueKind {
        GEM_ID,
        PURITY,
    }

    private data class ValueEdit(
        val value: String?,
        val selected: Boolean?,
    )

    private data class Context(
        val menu: StorageContainerMenuBase<*>,
        val container: GemCaseContainer,
        val session: GemMenuSession,
    )

    fun perform(
        player: ServerPlayer,
        request: GemSettingsRequest,
    ): Boolean =
        currentContext(player, request)?.let { context ->
            if (request.action == GemSettingsAction.REQUEST_PAGE) {
                context.session.validSettingsPage(request.page)
            } else {
                performEdit(context, request)
            }
        } ?: false

    private fun performEdit(
        context: Context,
        request: GemSettingsRequest,
    ): Boolean =
        (context.session.settingsSnapshot() as? GemSettingsState.Ready)?.let { ready ->
            update(ready.settings, request)?.let { updated ->
                updated != ready.settings && context.container.upgradeWrapper.replaceSettings(ready.settings, updated)
            }
        } ?: false

    // 検証失敗は対象menuやupgradeを後続処理へ渡さず、その場で拒否する。
    @Suppress("ReturnCount") // menu・装着状態などの失敗条件は後続処理へ進めず即時拒否する。
    private fun currentContext(
        player: ServerPlayer,
        request: GemSettingsRequest,
    ): Context? {
        if (!request.hasValidShape() || Thread.currentThread().threadGroup != SidedThreadGroups.SERVER) return null
        val menu = player.containerMenu as? StorageContainerMenuBase<*> ?: return null
        val container = menu.upgradeContainers[request.upgradeSlot] as? GemCaseContainer ?: return null
        if (menu.containerId != request.menuId || !menu.stillValid(player)) return null
        val session = container.sessionFor(menu)
        val matchesSession =
            session.id == request.sessionId &&
                session.revision == request.revision &&
                session.storageId != null &&
                container.isCurrent(menu)
        return if (matchesSession) Context(menu, container, session) else null
    }

    private fun update(
        settings: GemTransferSettings,
        request: GemSettingsRequest,
    ): GemTransferSettings? =
        settings.rule(request.direction).let { oldRule ->
            updateRule(oldRule, request)?.let { settings.withRule(request.direction, it) }
        }

    private fun GemTransferSettings.rule(direction: GemTransferDirection): GemTransferRule =
        when (direction) {
            GemTransferDirection.DEPOSIT -> deposit
            GemTransferDirection.RESTOCK -> restock
        }

    private fun GemTransferSettings.withRule(
        direction: GemTransferDirection,
        rule: GemTransferRule,
    ): GemTransferSettings =
        when (direction) {
            GemTransferDirection.DEPOSIT -> copy(deposit = rule)
            GemTransferDirection.RESTOCK -> copy(restock = rule)
        }

    private fun updateRule(
        current: GemTransferRule,
        request: GemSettingsRequest,
    ): GemTransferRule? =
        when (request.action) {
            GemSettingsAction.REQUEST_PAGE -> null
            GemSettingsAction.SET_ENABLED -> request.enabled?.let { current.copy(enabled = it) }
            GemSettingsAction.SET_MODE ->
                request.mode?.let { mode ->
                    current.copy(filter = GemFilter.of(mode, current.filter.gemIds, current.filter.purities))
                }
            GemSettingsAction.SET_GEM_ID ->
                updateValues(current, ValueEdit(request.value, request.selected), SettingValueKind.GEM_ID)
            GemSettingsAction.SET_PURITY ->
                updateValues(current, ValueEdit(request.value, request.selected), SettingValueKind.PURITY)
        }

    private fun updateValues(
        current: GemTransferRule,
        edit: ValueEdit,
        kind: SettingValueKind,
    ): GemTransferRule? {
        val value = edit.value
        val selected = edit.selected
        val allowed = value != null && selected != null && (!selected || isAllowed(kind, value))
        return if (allowed) applySelection(current, requireNotNull(value), requireNotNull(selected), kind) else null
    }

    private fun applySelection(
        current: GemTransferRule,
        value: String,
        selected: Boolean,
        kind: SettingValueKind,
    ): GemTransferRule? {
        val existing = selectedValues(current, kind)
        val changedValues = existing.toMutableSet()
        val changed = if (selected) changedValues.add(value) else changedValues.remove(value)
        if (!changed) return null
        val filter =
            if (kind == SettingValueKind.GEM_ID) {
                GemFilter.of(current.filter.mode, changedValues, current.filter.purities)
            } else {
                GemFilter.of(current.filter.mode, current.filter.gemIds, changedValues)
            }
        return current.copy(filter = filter)
    }

    private fun selectedValues(
        current: GemTransferRule,
        kind: SettingValueKind,
    ): Set<String> =
        when (kind) {
            SettingValueKind.GEM_ID -> current.filter.gemIds
            SettingValueKind.PURITY -> current.filter.purities
        }

    private fun isAllowed(
        kind: SettingValueKind,
        value: String,
    ): Boolean =
        when (kind) {
            SettingValueKind.GEM_ID -> isRegisteredGem(value)
            SettingValueKind.PURITY -> isKnownPurity(value)
        }

    private fun isRegisteredGem(value: String): Boolean =
        validText(value) &&
            ResourceLocation.tryParse(value)?.let { id ->
                id.toString() == value && GemRegistry.INSTANCE.holder(id).isBound
            } == true

    private fun isKnownPurity(value: String): Boolean = Purity.entries.any { it.serializedName == value }

    private fun validText(value: String): Boolean =
        value.isNotEmpty() && value.toByteArray(StandardCharsets.UTF_8).size <= GemSettingsCodec.MAX_TEXT_BYTES
}
