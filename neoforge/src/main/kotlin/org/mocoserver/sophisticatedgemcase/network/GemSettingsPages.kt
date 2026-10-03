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
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsState
import java.nio.charset.StandardCharsets
import java.util.UUID

internal enum class GemFilterAxis {
    GEM_ID,
    PURITY,
}

internal data class GemSettingsEntry(
    val direction: GemTransferDirection,
    val axis: GemFilterAxis,
    val value: String,
)

/** 設定値は通常slot同期へ載せず、menu世代に束縛した小さなpageで送る。 */
internal data class GemSettingsPage(
    val menuId: Int,
    val upgradeSlot: Int,
    val sessionId: UUID,
    val revision: Long,
    val page: Int,
    val totalPages: Int,
    val locked: Boolean,
    val depositEnabled: Boolean,
    val restockEnabled: Boolean,
    val depositMode: GemFilterMode,
    val restockMode: GemFilterMode,
    val entries: List<GemSettingsEntry>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GemSettingsPage> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<GemSettingsPage> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ModTemplate.ID, "gem_settings_page"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, GemSettingsPage> =
            object : StreamCodec<RegistryFriendlyByteBuf, GemSettingsPage> {
                override fun encode(
                    buffer: RegistryFriendlyByteBuf,
                    value: GemSettingsPage,
                ) {
                    require(validHeader(value) && value.entries.size <= GemSettingsPages.MAX_PAGE_ENTRIES)
                    val start = buffer.writerIndex()
                    buffer.writeVarInt(value.menuId)
                    buffer.writeVarInt(value.upgradeSlot)
                    buffer.writeUUID(value.sessionId)
                    buffer.writeLong(value.revision)
                    buffer.writeVarInt(value.page)
                    buffer.writeVarInt(value.totalPages)
                    buffer.writeBoolean(value.locked)
                    buffer.writeBoolean(value.depositEnabled)
                    buffer.writeBoolean(value.restockEnabled)
                    buffer.writeVarInt(value.depositMode.ordinal)
                    buffer.writeVarInt(value.restockMode.ordinal)
                    buffer.writeVarInt(value.entries.size)
                    value.entries.forEach { entry ->
                        require(validText(entry.value))
                        buffer.writeVarInt(entry.direction.ordinal)
                        buffer.writeVarInt(entry.axis.ordinal)
                        buffer.writeUtf(entry.value, GemSettingsCodec.MAX_TEXT_BYTES)
                    }
                    require(buffer.writerIndex() - start <= GemSettingsPages.MAX_PAGE_BYTES)
                }

                override fun decode(buffer: RegistryFriendlyByteBuf): GemSettingsPage {
                    val start = buffer.readerIndex()
                    val menuId = buffer.readVarInt()
                    val slot = buffer.readVarInt()
                    val session = buffer.readUUID()
                    val revision = buffer.readLong()
                    val page = buffer.readVarInt()
                    val total = buffer.readVarInt()
                    val locked = buffer.readBoolean()
                    val depositEnabled = buffer.readBoolean()
                    val restockEnabled = buffer.readBoolean()
                    val depositMode =
                        GemFilterMode.entries.getOrNull(buffer.readVarInt())
                            ?: throw DecoderException("Unknown deposit filter mode")
                    val restockMode =
                        GemFilterMode.entries.getOrNull(buffer.readVarInt())
                            ?: throw DecoderException("Unknown restock filter mode")
                    val size = buffer.readVarInt()
                    if (size !in
                        0..GemSettingsPages.MAX_PAGE_ENTRIES
                    ) {
                        throw DecoderException("Gem settings page entry limit")
                    }
                    val entries =
                        List(size) {
                            val direction =
                                GemTransferDirection.entries.getOrNull(buffer.readVarInt())
                                    ?: throw DecoderException("Unknown Gem transfer direction")
                            val axis =
                                GemFilterAxis.entries.getOrNull(buffer.readVarInt())
                                    ?: throw DecoderException("Unknown Gem filter axis")
                            val value = buffer.readUtf(GemSettingsCodec.MAX_TEXT_BYTES)
                            if (!validText(value)) throw DecoderException("Invalid Gem setting value")
                            GemSettingsEntry(direction, axis, value)
                        }
                    val result =
                        GemSettingsPage(
                            menuId,
                            slot,
                            session,
                            revision,
                            page,
                            total,
                            locked,
                            depositEnabled,
                            restockEnabled,
                            depositMode,
                            restockMode,
                            entries,
                        )
                    if (!validHeader(result) || buffer.readerIndex() - start > GemSettingsPages.MAX_PAGE_BYTES) {
                        throw DecoderException("Invalid Gem settings page")
                    }
                    return result
                }
            }

        private fun validHeader(page: GemSettingsPage): Boolean =
            page.menuId >= 0 &&
                page.upgradeSlot >= 0 &&
                page.revision >= 0 &&
                page.totalPages in 1..GemSettingsCodec.MAX_ENTRIES &&
                page.page in 0 until page.totalPages

        private fun validText(value: String): Boolean =
            value.isNotEmpty() && value.toByteArray(StandardCharsets.UTF_8).size <= GemSettingsCodec.MAX_TEXT_BYTES
    }
}

/** UTF-8 byte数とentry数の小さい方で分ける。Locked／空設定もpage 0を持つ。 */
internal object GemSettingsPages {
    const val MAX_PAGE_ENTRIES = 64
    const val MAX_PAGE_BYTES = 32_768
    private const val HEADER_RESERVE_BYTES = 128

    fun partition(state: GemSettingsState): List<List<GemSettingsEntry>> {
        val ready = state as? GemSettingsState.Ready
        val entries =
            ready
                ?.settings
                ?.let { settings ->
                    buildList {
                        addRule(GemTransferDirection.DEPOSIT, settings.deposit)
                        addRule(GemTransferDirection.RESTOCK, settings.restock)
                    }
                }.orEmpty()
                .sortedWith(compareBy({ it.direction.ordinal }, { it.axis.ordinal }, { it.value }))
        val result = mutableListOf<List<GemSettingsEntry>>()
        val current = mutableListOf<GemSettingsEntry>()
        var bytes = HEADER_RESERVE_BYTES
        for (entry in entries) {
            val needed = entry.value.toByteArray(StandardCharsets.UTF_8).size + ENTRY_HEADER_BYTES
            if (current.size == MAX_PAGE_ENTRIES || bytes + needed > MAX_PAGE_BYTES) {
                result.add(current.toList())
                current.clear()
                bytes = HEADER_RESERVE_BYTES
            }
            current.add(entry)
            bytes += needed
        }
        if (current.isNotEmpty() || result.isEmpty()) result.add(current.toList())
        return result
    }

    private fun MutableList<GemSettingsEntry>.addRule(
        direction: GemTransferDirection,
        rule: org.mocoserver.sophisticatedgemcase.core.GemTransferRule,
    ) {
        rule.filter.gemIds.forEach { add(GemSettingsEntry(direction, GemFilterAxis.GEM_ID, it)) }
        rule.filter.purities.forEach { add(GemSettingsEntry(direction, GemFilterAxis.PURITY, it)) }
    }

    private const val ENTRY_HEADER_BYTES = 16
}
