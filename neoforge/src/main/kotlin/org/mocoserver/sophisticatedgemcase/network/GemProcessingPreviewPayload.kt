package org.mocoserver.sophisticatedgemcase.network

import dev.shadowsoffire.apotheosis.socket.gem.Purity
import io.netty.handler.codec.DecoderException
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.processing.GemMaterialCost
import org.mocoserver.sophisticatedgemcase.processing.GemMaterialSide
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingPreview
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingReason
import java.nio.charset.StandardCharsets
import java.util.UUID

/** 材料rawを含めず、選択結果と必要材料だけをmenu世代に束縛して送る。 */
internal data class GemProcessingPreviewPayload(
    val menuId: Int,
    val upgradeSlot: Int,
    val sessionId: UUID,
    val revision: Long,
    val recipeEpoch: Long,
    val preview: GemProcessingPreview,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GemProcessingPreviewPayload> = TYPE

    companion object {
        const val MAX_PAYLOAD_BYTES: Int = GemProcessingPreviewLimits.MAX_PAYLOAD_BYTES
        const val MAX_COSTS: Int = GemProcessingPreviewLimits.MAX_COSTS
        const val MAX_ID_BYTES: Int = GemProcessingPreviewLimits.MAX_ID_BYTES
        val TYPE: CustomPacketPayload.Type<GemProcessingPreviewPayload> =
            CustomPacketPayload.Type(
                ResourceLocation.fromNamespaceAndPath(ModTemplate.ID, "gem_processing_preview"),
            )
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, GemProcessingPreviewPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, GemProcessingPreviewPayload> {
                override fun encode(
                    buffer: RegistryFriendlyByteBuf,
                    value: GemProcessingPreviewPayload,
                ) {
                    require(validHeader(value)) { "Invalid Gem processing preview header" }
                    require(value.preview.costs.size <= MAX_COSTS) { "Gem processing preview cost limit" }
                    val start = buffer.writerIndex()
                    buffer.writeVarInt(value.menuId)
                    buffer.writeVarInt(value.upgradeSlot)
                    buffer.writeUUID(value.sessionId)
                    buffer.writeLong(value.revision)
                    buffer.writeLong(value.recipeEpoch)
                    writePreview(buffer, value.preview)
                    require(buffer.writerIndex() - start <= MAX_PAYLOAD_BYTES) { "Gem processing preview byte limit" }
                }

                override fun decode(buffer: RegistryFriendlyByteBuf): GemProcessingPreviewPayload {
                    if (buffer.readableBytes() > MAX_PAYLOAD_BYTES) {
                        throw DecoderException("Gem processing preview byte limit")
                    }
                    val start = buffer.readerIndex()
                    val menuId = buffer.readVarInt()
                    val upgradeSlot = buffer.readVarInt()
                    val sessionId = buffer.readUUID()
                    val revision = buffer.readLong()
                    val recipeEpoch = buffer.readLong()
                    val preview = readPreview(buffer)
                    val result =
                        GemProcessingPreviewPayload(
                            menuId,
                            upgradeSlot,
                            sessionId,
                            revision,
                            recipeEpoch,
                            preview,
                        )
                    if (!validHeader(result) || buffer.readerIndex() - start > MAX_PAYLOAD_BYTES) {
                        throw DecoderException("Invalid Gem processing preview")
                    }
                    if (buffer.isReadable) throw DecoderException("Unexpected Gem processing preview fields")
                    return result
                }
            }

        internal fun writePreview(
            buffer: RegistryFriendlyByteBuf,
            preview: GemProcessingPreview,
        ) {
            writeGemKey(buffer, preview.output)
            buffer.writeBoolean(preview.hasMaterials)
            buffer.writeBoolean(preview.materialsLocked)
            buffer.writeBoolean(preview.useBackpackMaterials)
            buffer.writeBoolean(preview.settingsLocked)
            buffer.writeVarInt(preview.reason.ordinal)
            buffer.writeBoolean(preview.recipeId != null)
            preview.recipeId?.let { ResourceLocation.STREAM_CODEC.encode(buffer, it) }
            buffer.writeVarInt(preview.costs.size)
            preview.costs.forEach { cost ->
                ResourceLocation.STREAM_CODEC.encode(buffer, cost.itemId)
                buffer.writeVarInt(cost.count)
                buffer.writeVarInt(cost.side.ordinal)
            }
        }

        internal fun readPreview(buffer: RegistryFriendlyByteBuf): GemProcessingPreview {
            val output = readGemKey(buffer)
            val hasMaterials = buffer.readBoolean()
            val materialsLocked = buffer.readBoolean()
            val useBackpackMaterials = buffer.readBoolean()
            val settingsLocked = buffer.readBoolean()
            val reason = readReason(buffer)
            val recipeId = if (buffer.readBoolean()) ResourceLocation.STREAM_CODEC.decode(buffer) else null
            val costs = readCosts(buffer)
            return GemProcessingPreview(
                output,
                hasMaterials,
                materialsLocked,
                useBackpackMaterials,
                settingsLocked,
                reason,
                recipeId,
                costs,
            )
        }

        private fun readReason(buffer: RegistryFriendlyByteBuf): GemProcessingReason =
            GemProcessingReason.entries.getOrNull(buffer.readVarInt())
                ?: throw DecoderException("Unknown Gem processing reason")

        private fun readCosts(buffer: RegistryFriendlyByteBuf): List<GemMaterialCost> {
            val costCount = buffer.readVarInt()
            if (costCount !in 0..MAX_COSTS) throw DecoderException("Gem processing preview cost limit")
            return List(costCount) { readCost(buffer) }
        }

        private fun readCost(buffer: RegistryFriendlyByteBuf): GemMaterialCost {
            val itemId = ResourceLocation.STREAM_CODEC.decode(buffer)
            val count = buffer.readVarInt()
            val side =
                GemMaterialSide.entries.getOrNull(buffer.readVarInt())
                    ?: throw DecoderException("Unknown Gem material side")
            if (count <= 0) throw DecoderException("Invalid Gem material cost")
            return GemMaterialCost(itemId, count, side)
        }

        private fun writeGemKey(
            buffer: RegistryFriendlyByteBuf,
            key: GemKey,
        ) {
            require(validOutput(key)) { "Invalid Gem processing output" }
            buffer.writeUtf(key.gemId, MAX_ID_BYTES)
            buffer.writeUtf(key.purity, MAX_ID_BYTES)
        }

        private fun readGemKey(buffer: RegistryFriendlyByteBuf): GemKey {
            val key = GemKey(buffer.readUtf(MAX_ID_BYTES), buffer.readUtf(MAX_ID_BYTES))
            if (!validOutput(key)) throw DecoderException("Invalid Gem processing output")
            return key
        }

        private fun validHeader(value: GemProcessingPreviewPayload): Boolean =
            value.menuId >= 0 &&
                value.upgradeSlot >= 0 &&
                value.revision >= 0 &&
                value.recipeEpoch >= 0 &&
                value.preview.costs.size <= MAX_COSTS &&
                validPreview(value.preview)

        private fun validPreview(preview: GemProcessingPreview): Boolean =
            GemProcessingPreviewLimits.isEncodable(preview)

        private fun validOutput(key: GemKey): Boolean =
            validGemResourceLocation(key.gemId) &&
                key.purity.toByteArray(StandardCharsets.UTF_8).size <= MAX_ID_BYTES &&
                Purity.entries.any { it != Purity.CRACKED && it.serializedName == key.purity }
    }
}

private fun validGemResourceLocation(value: String): Boolean =
    value.toByteArray(StandardCharsets.UTF_8).size <= GemProcessingPreviewPayload.MAX_ID_BYTES &&
        ResourceLocation.tryParse(value)?.toString() == value

/** plannerとwireが同じ上限でpreview全体を事前判定する。 */
internal object GemProcessingPreviewLimits {
    const val MAX_PAYLOAD_BYTES: Int = 32 * 1024
    const val MAX_COSTS: Int = 4096
    const val MAX_ID_BYTES: Int = 256
    private const val MAX_HEADER_BYTES = 5 + 5 + 16 + 8 + 8
    private const val PREVIEW_BOOLEAN_BYTES = 4
    private const val RECIPE_FLAG_BYTES = 1
    private const val VARINT_CONTINUATION_MASK = -128
    private const val VARINT_BITS_PER_BYTE = 7

    fun isEncodable(preview: GemProcessingPreview): Boolean {
        val outputValid = validOutput(preview.output)
        val sizeValid = preview.costs.size <= MAX_COSTS
        val recipeValid = preview.recipeId == null || validResourceLocation(preview.recipeId.toString())
        val costsValid = preview.costs.all { it.count > 0 && validResourceLocation(it.itemId.toString()) }
        return outputValid &&
            sizeValid &&
            recipeValid &&
            costsValid &&
            encodedPreviewSize(preview) + MAX_HEADER_BYTES <= MAX_PAYLOAD_BYTES
    }

    fun encodedPreviewSize(preview: GemProcessingPreview): Long {
        var bytes =
            encodedStringSize(preview.output.gemId) +
                encodedStringSize(preview.output.purity) +
                PREVIEW_BOOLEAN_BYTES +
                varIntSize(preview.reason.ordinal) +
                RECIPE_FLAG_BYTES +
                varIntSize(preview.costs.size)
        preview.recipeId?.let { bytes += encodedStringSize(it.toString()) }
        preview.costs.forEach { cost ->
            bytes += encodedStringSize(cost.itemId.toString()) + varIntSize(cost.count) + varIntSize(cost.side.ordinal)
        }
        return bytes
    }

    private fun encodedStringSize(value: String): Long {
        val bytes = value.toByteArray(StandardCharsets.UTF_8).size
        return varIntSize(bytes) + bytes.toLong()
    }

    private fun varIntSize(value: Int): Long {
        var remaining = value
        var size = 1L
        while ((remaining and VARINT_CONTINUATION_MASK) != 0) {
            size++
            remaining = remaining ushr VARINT_BITS_PER_BYTE
        }
        return size
    }

    private fun validOutput(key: GemKey): Boolean =
        validResourceLocation(key.gemId) &&
            key.purity.toByteArray(StandardCharsets.UTF_8).size <= MAX_ID_BYTES &&
            Purity.entries.any { it != Purity.CRACKED && it.serializedName == key.purity }

    private fun validResourceLocation(value: String): Boolean =
        value.toByteArray(StandardCharsets.UTF_8).size <= MAX_ID_BYTES &&
            ResourceLocation.tryParse(value)?.toString() == value
}
