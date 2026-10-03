package org.mocoserver.sophisticatedgemcase.network

import io.netty.handler.codec.DecoderException
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemFilterMode
import org.mocoserver.sophisticatedgemcase.core.GemTransferDirection
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsCodec
import java.nio.charset.StandardCharsets
import java.util.UUID

internal enum class GemSettingsAction {
    REQUEST_PAGE,
    SET_ENABLED,
    SET_MODE,
    SET_GEM_ID,
    SET_PURITY,
}

/** 設定全体やraw NBTを送らず、一つの編集操作だけを要求する。 */
internal data class GemSettingsRequest(
    val menuId: Int,
    val upgradeSlot: Int,
    val sessionId: UUID,
    val revision: Long,
    val action: GemSettingsAction,
    val direction: GemTransferDirection,
    val enabled: Boolean?,
    val mode: GemFilterMode?,
    val value: String?,
    val selected: Boolean?,
    val page: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GemSettingsRequest> = TYPE

    fun hasValidShape(): Boolean {
        if (!hasValidHeader()) return false
        return when (action) {
            GemSettingsAction.REQUEST_PAGE -> hasNoEditFields()
            GemSettingsAction.SET_ENABLED -> enabled != null && hasNoModeValueFields() && page == 0
            GemSettingsAction.SET_MODE -> mode != null && hasNoEnabledValueFields() && page == 0
            GemSettingsAction.SET_GEM_ID, GemSettingsAction.SET_PURITY -> hasValueFields()
        }
    }

    private fun hasValidHeader(): Boolean = menuId >= 0 && upgradeSlot >= 0 && revision >= 0 && page >= 0

    private fun hasNoEditFields(): Boolean = enabled == null && mode == null && value == null && selected == null

    private fun hasNoModeValueFields(): Boolean = mode == null && value == null && selected == null

    private fun hasNoEnabledValueFields(): Boolean = enabled == null && value == null && selected == null

    private fun hasValueFields(): Boolean =
        enabled == null && mode == null && value != null && selected != null && page == 0 && validText(value)

    companion object {
        val TYPE: CustomPacketPayload.Type<GemSettingsRequest> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ModTemplate.ID, "gem_settings_request"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, GemSettingsRequest> =
            object : StreamCodec<RegistryFriendlyByteBuf, GemSettingsRequest> {
                override fun encode(
                    buffer: RegistryFriendlyByteBuf,
                    value: GemSettingsRequest,
                ) {
                    require(value.hasValidShape()) { "Invalid Gem settings request shape" }
                    buffer.writeVarInt(value.menuId)
                    buffer.writeVarInt(value.upgradeSlot)
                    buffer.writeUUID(value.sessionId)
                    buffer.writeLong(value.revision)
                    buffer.writeVarInt(value.action.ordinal)
                    buffer.writeVarInt(value.direction.ordinal)
                    buffer.writeBoolean(value.enabled != null)
                    value.enabled?.let(buffer::writeBoolean)
                    buffer.writeBoolean(value.mode != null)
                    value.mode?.let { buffer.writeVarInt(it.ordinal) }
                    buffer.writeBoolean(value.value != null)
                    value.value?.let { buffer.writeUtf(it, GemSettingsCodec.MAX_TEXT_BYTES) }
                    buffer.writeBoolean(value.selected != null)
                    value.selected?.let(buffer::writeBoolean)
                    buffer.writeVarInt(value.page)
                }

                override fun decode(buffer: RegistryFriendlyByteBuf): GemSettingsRequest {
                    val menuId = buffer.readVarInt()
                    val slot = buffer.readVarInt()
                    val session = buffer.readUUID()
                    val revision = buffer.readLong()
                    val action =
                        GemSettingsAction.entries.getOrNull(buffer.readVarInt())
                            ?: throw DecoderException("Unknown Gem settings action")
                    val direction =
                        GemTransferDirection.entries.getOrNull(buffer.readVarInt())
                            ?: throw DecoderException("Unknown Gem transfer direction")
                    val enabled = if (buffer.readBoolean()) buffer.readBoolean() else null
                    val mode =
                        if (buffer.readBoolean()) {
                            GemFilterMode.entries.getOrNull(buffer.readVarInt())
                                ?: throw DecoderException("Unknown Gem filter mode")
                        } else {
                            null
                        }
                    val value = if (buffer.readBoolean()) buffer.readUtf(GemSettingsCodec.MAX_TEXT_BYTES) else null
                    val selected = if (buffer.readBoolean()) buffer.readBoolean() else null
                    val request =
                        GemSettingsRequest(
                            menuId,
                            slot,
                            session,
                            revision,
                            action,
                            direction,
                            enabled,
                            mode,
                            value,
                            selected,
                            buffer.readVarInt(),
                        )
                    if (!request.hasValidShape()) throw DecoderException("Invalid Gem settings request shape")
                    return request
                }
            }

        private fun validText(value: String): Boolean =
            value.isNotEmpty() && value.toByteArray(StandardCharsets.UTF_8).size <= GemSettingsCodec.MAX_TEXT_BYTES
    }
}
