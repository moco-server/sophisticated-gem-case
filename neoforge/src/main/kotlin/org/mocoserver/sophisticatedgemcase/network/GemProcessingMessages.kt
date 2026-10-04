package org.mocoserver.sophisticatedgemcase.network

import dev.shadowsoffire.apotheosis.socket.gem.Purity
import io.netty.handler.codec.DecoderException
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemKey
import java.nio.charset.StandardCharsets
import java.util.UUID

internal enum class GemProcessingAction {
    REQUEST_PREVIEW,
    PROCESS_ONE,
    PROCESS_MANY,
    SET_BACKPACK_MATERIALS,
    SET_PROCESSING_VIEW,
}

/** 要求はoutput指定か補充切替の一つだけを持つ。 */
internal data class GemProcessingRequest(
    val menuId: Int,
    val upgradeSlot: Int,
    val sessionId: UUID,
    val revision: Long,
    val action: GemProcessingAction,
    val output: GemKey?,
    val enabled: Boolean?,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GemProcessingRequest> = TYPE

    fun hasValidShape(): Boolean {
        if (menuId < 0 || upgradeSlot < 0 || revision < 0) return false
        return when (action) {
            GemProcessingAction.REQUEST_PREVIEW,
            GemProcessingAction.PROCESS_ONE,
            GemProcessingAction.PROCESS_MANY,
            -> output != null && enabled == null && validOutput(output)
            GemProcessingAction.SET_BACKPACK_MATERIALS,
            GemProcessingAction.SET_PROCESSING_VIEW,
            -> output == null && enabled != null
        }
    }

    companion object {
        const val MAX_ID_BYTES: Int = 256
        val TYPE: CustomPacketPayload.Type<GemProcessingRequest> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ModTemplate.ID, "gem_processing_request"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, GemProcessingRequest> =
            object : StreamCodec<RegistryFriendlyByteBuf, GemProcessingRequest> {
                override fun encode(
                    buffer: RegistryFriendlyByteBuf,
                    value: GemProcessingRequest,
                ) {
                    require(value.hasValidShape()) { "Invalid Gem processing request shape" }
                    buffer.writeVarInt(value.menuId)
                    buffer.writeVarInt(value.upgradeSlot)
                    buffer.writeUUID(value.sessionId)
                    buffer.writeLong(value.revision)
                    buffer.writeVarInt(value.action.ordinal)
                    buffer.writeBoolean(value.output != null)
                    value.output?.let { writeOutput(buffer, it) }
                    buffer.writeBoolean(value.enabled != null)
                    value.enabled?.let(buffer::writeBoolean)
                }

                override fun decode(buffer: RegistryFriendlyByteBuf): GemProcessingRequest {
                    val menuId = buffer.readVarInt()
                    val upgradeSlot = buffer.readVarInt()
                    val sessionId = buffer.readUUID()
                    val revision = buffer.readLong()
                    val action =
                        GemProcessingAction.entries.getOrNull(buffer.readVarInt())
                            ?: throw DecoderException("Unknown Gem processing action")
                    val output = if (buffer.readBoolean()) readOutput(buffer) else null
                    val enabled = if (buffer.readBoolean()) buffer.readBoolean() else null
                    val request =
                        GemProcessingRequest(
                            menuId,
                            upgradeSlot,
                            sessionId,
                            revision,
                            action,
                            output,
                            enabled,
                        )
                    if (!request.hasValidShape()) throw DecoderException("Invalid Gem processing request shape")
                    if (buffer.isReadable) throw DecoderException("Unexpected Gem processing request fields")
                    return request
                }
            }

        private fun writeOutput(
            buffer: RegistryFriendlyByteBuf,
            key: GemKey,
        ) {
            buffer.writeUtf(key.gemId, MAX_ID_BYTES)
            buffer.writeUtf(key.purity, MAX_ID_BYTES)
        }

        private fun readOutput(buffer: RegistryFriendlyByteBuf): GemKey {
            val key = GemKey(buffer.readUtf(MAX_ID_BYTES), buffer.readUtf(MAX_ID_BYTES))
            if (!validOutput(key)) throw DecoderException("Invalid Gem processing output")
            return key
        }

        private fun validOutput(key: GemKey): Boolean =
            validGemId(key.gemId) &&
                validPurity(key.purity) &&
                Purity.entries.any { it != Purity.CRACKED && it.serializedName == key.purity }

        private fun validGemId(value: String): Boolean =
            value.isNotEmpty() &&
                value.toByteArray(StandardCharsets.UTF_8).size <= MAX_ID_BYTES &&
                ResourceLocation.tryParse(value)?.toString() == value

        private fun validPurity(value: String): Boolean =
            value.isNotEmpty() && value.toByteArray(StandardCharsets.UTF_8).size <= MAX_ID_BYTES
    }
}
