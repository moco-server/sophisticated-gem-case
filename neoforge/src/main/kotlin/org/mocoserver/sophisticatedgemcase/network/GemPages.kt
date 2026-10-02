package org.mocoserver.sophisticatedgemcase.network

import io.netty.handler.codec.DecoderException
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import java.util.UUID

internal data class GemPageEntry(
    val key: GemKey,
    val count: Int,
    val resolved: Boolean,
)

/** 同じsession/revisionのページだけを一覧として扱う。raw保存は含めない。 */
internal data class GemPage(
    val menuId: Int,
    val upgradeSlot: Int,
    val sessionId: UUID,
    val revision: Long,
    val page: Int,
    val totalPages: Int,
    val entries: List<GemPageEntry>,
    val locked: Boolean,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GemPage> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<GemPage> =
            CustomPacketPayload.Type(
                ResourceLocation.fromNamespaceAndPath(ModTemplate.ID, "gem_page"),
            )
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, GemPage> =
            object : StreamCodec<RegistryFriendlyByteBuf, GemPage> {
                override fun encode(
                    buffer: RegistryFriendlyByteBuf,
                    value: GemPage,
                ) {
                    require(value.entries.size <= GemPages.MAX_PAGE_ENTRIES)
                    val start = buffer.writerIndex()
                    buffer.writeVarInt(value.menuId)
                    buffer.writeVarInt(value.upgradeSlot)
                    buffer.writeUUID(value.sessionId)
                    buffer.writeLong(value.revision)
                    buffer.writeVarInt(value.page)
                    buffer.writeVarInt(value.totalPages)
                    buffer.writeBoolean(value.locked)
                    buffer.writeVarInt(value.entries.size)
                    value.entries.forEach { entry ->
                        GemWire.writeKey(buffer, entry.key)
                        buffer.writeVarInt(entry.count)
                        buffer.writeBoolean(entry.resolved)
                    }
                    require(buffer.writerIndex() - start <= GemPages.MAX_PAGE_BYTES)
                }

                override fun decode(buffer: RegistryFriendlyByteBuf): GemPage {
                    val start = buffer.readerIndex()
                    val menu = buffer.readVarInt()
                    val slot = buffer.readVarInt()
                    val session = buffer.readUUID()
                    val revision = buffer.readLong()
                    val page = buffer.readVarInt()
                    val total = buffer.readVarInt()
                    val locked = buffer.readBoolean()
                    val size = buffer.readVarInt()
                    if (size !in 0..GemPages.MAX_PAGE_ENTRIES) throw DecoderException("Gem page entry limit")
                    val entries =
                        List(size) {
                            val key = GemWire.readKey(buffer)
                            val count = buffer.readVarInt()
                            if (count !in 1..GemCounts.CAPACITY) throw DecoderException("Gem count limit")
                            GemPageEntry(key, count, buffer.readBoolean())
                        }
                    if (buffer.readerIndex() - start >
                        GemPages.MAX_PAGE_BYTES
                    ) {
                        throw DecoderException("Gem page byte limit")
                    }
                    return GemPage(menu, slot, session, revision, page, total, entries, locked)
                }
            }
    }
}

/** 64 entryとwire byte上限の小さい方で区切り、空／ロックにもpage 0を用意する。 */
internal object GemPages {
    const val MAX_PAGE_ENTRIES = 64
    const val MAX_PAGE_BYTES = 32_768
    private const val HEADER_RESERVE_BYTES = 1024
    private const val ENTRY_OVERHEAD_BYTES = 16

    fun partition(state: GemStorageState): List<List<GemPageEntry>> {
        val ready = state as? GemStorageState.Ready
        val entries =
            ready
                ?.counts
                ?.entries()
                ?.entries
                ?.sortedWith(
                    compareBy({ it.key.gemId }, { it.key.purity }),
                ).orEmpty()
        val pages = mutableListOf<List<GemPageEntry>>()
        val current = mutableListOf<GemPageEntry>()
        var bytes = HEADER_RESERVE_BYTES
        for ((key, count) in entries) {
            val needed =
                key.gemId.toByteArray(Charsets.UTF_8).size + key.purity.toByteArray(Charsets.UTF_8).size +
                    ENTRY_OVERHEAD_BYTES
            if (current.size == MAX_PAGE_ENTRIES || bytes + needed > MAX_PAGE_BYTES) {
                pages.add(current.toList())
                current.clear()
                bytes = HEADER_RESERVE_BYTES
            }
            current.add(GemPageEntry(key, count, StandardGem.stackOf(key) != null))
            bytes += needed
        }
        if (current.isNotEmpty() || pages.isEmpty()) pages.add(current.toList())
        return pages.toList()
    }
}
