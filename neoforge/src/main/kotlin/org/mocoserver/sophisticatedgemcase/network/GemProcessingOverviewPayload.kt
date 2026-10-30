package org.mocoserver.sophisticatedgemcase.network

import io.netty.handler.codec.DecoderException
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingPreview
import java.nio.charset.StandardCharsets
import java.util.UUID

/** 最大5強化先と無選択でも使える素材状態を、一つの選択連番・保存世代へ束縛する。 */
internal data class GemProcessingOverviewPayload(
    val menuId: Int,
    val upgradeSlot: Int,
    val sessionId: UUID,
    val revision: Long,
    val recipeEpoch: Long,
    val gemId: String?,
    val selectionId: Long,
    val hasMaterials: Boolean,
    val materialsLocked: Boolean,
    val useBackpackMaterials: Boolean,
    val settingsLocked: Boolean,
    val previews: List<GemProcessingPreview>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GemProcessingOverviewPayload> = TYPE

    fun matchesSelection(
        target: GemProcessingTarget,
        session: UUID?,
        revision: Long,
        gemId: String?,
        selectionId: Long,
    ): Boolean =
        this.menuId == target.menuId &&
            upgradeSlot == target.upgradeSlot &&
            sessionId == session &&
            this.revision == revision &&
            this.gemId == gemId &&
            this.selectionId == selectionId

    companion object {
        const val MAX_OUTPUTS: Int = 5
        const val MAX_PAYLOAD_BYTES: Int = GemProcessingPreviewPayload.MAX_PAYLOAD_BYTES

        // 最大ID・varint・選択連番・状態flagを含むheaderの保守的上限。
        const val MAX_HEADER_BYTES: Int = 320
        val TYPE: CustomPacketPayload.Type<GemProcessingOverviewPayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ModTemplate.ID, "gem_processing_overview"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, GemProcessingOverviewPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, GemProcessingOverviewPayload> {
                override fun encode(
                    buffer: RegistryFriendlyByteBuf,
                    value: GemProcessingOverviewPayload,
                ) {
                    require(valid(value)) { "Invalid Gem processing overview" }
                    val start = buffer.writerIndex()
                    buffer.writeVarInt(value.menuId)
                    buffer.writeVarInt(value.upgradeSlot)
                    buffer.writeUUID(value.sessionId)
                    buffer.writeLong(value.revision)
                    buffer.writeLong(value.recipeEpoch)
                    buffer.writeBoolean(value.gemId != null)
                    value.gemId?.let { buffer.writeUtf(it, GemProcessingPreviewPayload.MAX_ID_BYTES) }
                    buffer.writeLong(value.selectionId)
                    buffer.writeBoolean(value.hasMaterials)
                    buffer.writeBoolean(value.materialsLocked)
                    buffer.writeBoolean(value.useBackpackMaterials)
                    buffer.writeBoolean(value.settingsLocked)
                    buffer.writeVarInt(value.previews.size)
                    value.previews.forEach { GemProcessingPreviewPayload.writePreview(buffer, it) }
                    require(buffer.writerIndex() - start <= MAX_PAYLOAD_BYTES) { "Gem overview byte limit" }
                }

                override fun decode(buffer: RegistryFriendlyByteBuf): GemProcessingOverviewPayload {
                    if (buffer.readableBytes() > MAX_PAYLOAD_BYTES) throw DecoderException("Gem overview byte limit")
                    val menuId = buffer.readVarInt()
                    val slot = buffer.readVarInt()
                    val session = buffer.readUUID()
                    val revision = buffer.readLong()
                    val epoch = buffer.readLong()
                    val gem =
                        if (buffer.readBoolean()) {
                            buffer.readUtf(
                                GemProcessingPreviewPayload.MAX_ID_BYTES,
                            )
                        } else {
                            null
                        }
                    val selection = buffer.readLong()
                    val hasMaterials = buffer.readBoolean()
                    val materialsLocked = buffer.readBoolean()
                    val useBackpack = buffer.readBoolean()
                    val settingsLocked = buffer.readBoolean()
                    val size = buffer.readVarInt()
                    if (size !in 0..MAX_OUTPUTS) throw DecoderException("Gem overview output limit")
                    val previews = List(size) { GemProcessingPreviewPayload.readPreview(buffer) }
                    val result =
                        GemProcessingOverviewPayload(
                            menuId,
                            slot,
                            session,
                            revision,
                            epoch,
                            gem,
                            selection,
                            hasMaterials,
                            materialsLocked,
                            useBackpack,
                            settingsLocked,
                            previews,
                        )
                    if (!valid(result) || buffer.isReadable) throw DecoderException("Invalid Gem overview")
                    return result
                }
            }

        private fun valid(value: GemProcessingOverviewPayload): Boolean {
            val header =
                value.menuId >= 0 &&
                    value.upgradeSlot >= 0 &&
                    value.revision >= 0 &&
                    value.recipeEpoch >= 0 &&
                    value.selectionId >= 0
            val bytes = MAX_HEADER_BYTES + value.previews.sumOf(GemProcessingPreviewLimits::encodedPreviewSize)
            return header && validGem(value.gemId) && validOutputs(value) && bytes <= MAX_PAYLOAD_BYTES
        }

        private fun validGem(gem: String?): Boolean =
            gem == null ||
                (
                    gem.toByteArray(StandardCharsets.UTF_8).size <= GemProcessingPreviewPayload.MAX_ID_BYTES &&
                        ResourceLocation.tryParse(gem)?.toString() == gem
                )

        private fun validOutputs(value: GemProcessingOverviewPayload): Boolean {
            val bounded =
                value.previews.size <= MAX_OUTPUTS &&
                    value.previews
                        .map { it.output }
                        .distinct()
                        .size == value.previews.size
            val matching =
                value.previews.all {
                    it.output.gemId == value.gemId && GemProcessingPreviewLimits.isEncodable(it)
                }
            return bounded && matching && (value.gemId != null || value.previews.isEmpty())
        }
    }
}

/** menu IDとupgrade slotは一つの操作対象として照合する。 */
internal data class GemProcessingTarget(
    val menuId: Int,
    val upgradeSlot: Int,
)
