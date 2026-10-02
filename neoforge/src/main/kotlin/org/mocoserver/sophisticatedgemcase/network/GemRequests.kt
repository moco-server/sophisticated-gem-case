package org.mocoserver.sophisticatedgemcase.network

import io.netty.handler.codec.DecoderException
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec
import java.util.UUID

internal enum class GemAction {
    INSERT_CURSOR,
    WITHDRAW_ONE,
    WITHDRAW_STACK,
    WITHDRAW_TO_PLAYER,
    MOVE_NORMAL,
    REQUEST_PAGE,
}

/** clientは要求だけを送り、変更後のstackや保存payloadを渡さない。 */
internal data class GemRequest(
    val menuId: Int,
    val upgradeSlot: Int,
    val sessionId: UUID,
    val revision: Long,
    val action: GemAction,
    val key: GemKey?,
    val page: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GemRequest> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<GemRequest> =
            CustomPacketPayload.Type(
                ResourceLocation.fromNamespaceAndPath(ModTemplate.ID, "gem_request"),
            )
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, GemRequest> =
            object : StreamCodec<RegistryFriendlyByteBuf, GemRequest> {
                override fun encode(
                    buffer: RegistryFriendlyByteBuf,
                    value: GemRequest,
                ) {
                    buffer.writeVarInt(value.menuId)
                    buffer.writeVarInt(value.upgradeSlot)
                    buffer.writeUUID(value.sessionId)
                    buffer.writeLong(value.revision)
                    buffer.writeVarInt(value.action.ordinal)
                    buffer.writeBoolean(value.key != null)
                    value.key?.let { GemWire.writeKey(buffer, it) }
                    buffer.writeVarInt(value.page)
                }

                override fun decode(buffer: RegistryFriendlyByteBuf): GemRequest {
                    val menu = buffer.readVarInt()
                    val slot = buffer.readVarInt()
                    val session = buffer.readUUID()
                    val revision = buffer.readLong()
                    val action =
                        GemAction.entries.getOrNull(buffer.readVarInt()) ?: throw DecoderException("Unknown Gem action")
                    val key = if (buffer.readBoolean()) GemWire.readKey(buffer) else null
                    return GemRequest(menu, slot, session, revision, action, key, buffer.readVarInt())
                }
            }
    }
}

/** ネットワーク文字列のUTF-8 byte上限を、文字数制限とは別に検査する。 */
internal object GemWire {
    fun writeKey(
        buffer: RegistryFriendlyByteBuf,
        key: GemKey,
    ) {
        require(GemStorageCodec.validText(key.gemId) && GemStorageCodec.validText(key.purity))
        buffer.writeUtf(key.gemId, GemStorageCodec.MAX_TEXT_BYTES)
        buffer.writeUtf(key.purity, GemStorageCodec.MAX_TEXT_BYTES)
    }

    fun readKey(buffer: RegistryFriendlyByteBuf): GemKey {
        val key = GemKey(buffer.readUtf(GemStorageCodec.MAX_TEXT_BYTES), buffer.readUtf(GemStorageCodec.MAX_TEXT_BYTES))
        if (!GemStorageCodec.validText(key.gemId) ||
            !GemStorageCodec.validText(key.purity)
        ) {
            throw DecoderException("Gem key exceeds byte limit")
        }
        return key
    }
}
