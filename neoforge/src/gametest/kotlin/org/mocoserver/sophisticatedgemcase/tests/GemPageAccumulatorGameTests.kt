package org.mocoserver.sophisticatedgemcase.tests

import io.netty.buffer.Unpooled
import io.netty.handler.codec.DecoderException
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.RegistryFriendlyByteBuf
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.network.GemAction
import org.mocoserver.sophisticatedgemcase.network.GemPage
import org.mocoserver.sophisticatedgemcase.network.GemPageAccumulator
import org.mocoserver.sophisticatedgemcase.network.GemPageEntry
import org.mocoserver.sophisticatedgemcase.network.GemPages
import org.mocoserver.sophisticatedgemcase.network.GemRequest
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import java.util.UUID

/** ページ応答を同じ表示世代へ安全に集約できることを確認する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemPageAccumulatorGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun aggregatesPagesAndRejectsDuplicatesAndOldRevisions(helper: GameTestHelper) {
        val session = UUID.randomUUID()
        val accumulator = GemPageAccumulator()
        val first = page(session, revision = 4, index = 0, total = 2, entry = entry("a"))
        val second = page(session, revision = 4, index = 1, total = 2, entry = entry("b"))

        helper.assertTrue(accumulator.accept(first), "First page was rejected")
        helper.assertTrue(accumulator.accept(second), "Page from same revision was rejected")
        helper.assertTrue(accumulator.complete, "Complete page set was not marked complete")
        helper.assertTrue(
            accumulator.entries.map { it.key.gemId } == listOf("test:a", "test:b"),
            "Pages were not aggregated",
        )
        helper.assertTrue(!accumulator.accept(second), "Duplicate page was accepted")
        helper.assertTrue(
            !accumulator.accept(page(session, revision = 3, index = 0, total = 1, entry = entry("old"))),
            "Old revision replaced current pages",
        )

        val nextSession = UUID.randomUUID()
        helper.assertTrue(
            accumulator.accept(page(nextSession, revision = 0, index = 0, total = 1, entry = entry("new"))),
            "New session page was rejected",
        )
        helper.assertTrue(accumulator.complete, "New session did not replace prior generation")
        helper.assertTrue(
            accumulator.entries
                .single()
                .key.gemId == "test:new",
            "Old session entries remained",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsMoreThanStorageEntryLimit(helper: GameTestHelper) {
        val oversized = List(4097) { index -> entry("g$index") }
        val accumulator = GemPageAccumulator()
        val page =
            GemPage(
                menuId = 1,
                upgradeSlot = 0,
                sessionId = UUID.randomUUID(),
                revision = 0,
                page = 0,
                totalPages = 1,
                entries = oversized,
                locked = false,
            )
        helper.assertTrue(!accumulator.accept(page), "More than 4096 entries were accepted")
        helper.assertTrue(accumulator.entries.isEmpty(), "Rejected oversized page was retained")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun measuresPageWireSizeAndRejectsInvalidWireValues(helper: GameTestHelper) {
        val counts =
            GemCounts.of(
                (0 until 65).associate { index ->
                    val suffix = index.toString().padStart(6, '0')
                    GemKey("test:${"a".repeat(245)}$suffix", "p".repeat(256)) to 1
                },
            )
        val partitioned = GemPages.partition(GemStorageState.Ready(counts))
        val session = UUID.randomUUID()
        val pages =
            partitioned.mapIndexed { index, entries ->
                GemPage(3, 0, session, 9, index, partitioned.size, entries, locked = false)
            }
        helper.assertTrue(pages.sumOf { it.entries.size } == 65, "Partition lost entries")
        helper.assertTrue(pages.size > 1, "Long keys were not split into multiple pages")
        for (page in pages) {
            helper.assertTrue(page.entries.size <= GemPages.MAX_PAGE_ENTRIES, "Page exceeds entry limit")
            val buffer = buffer(helper)
            try {
                GemPage.CODEC.encode(buffer, page)
                val encodedBytes = buffer.readableBytes()
                helper.assertTrue(encodedBytes <= GemPages.MAX_PAGE_BYTES, "Encoded page exceeds byte limit")
                helper.assertTrue(GemPage.CODEC.decode(buffer) == page, "Page wire round trip changed data")
            } finally {
                buffer.release()
            }
        }

        val tooLongKey = GemKey("test:${"a".repeat(252)}", "pure")
        val invalidPage = page(session, revision = 1, index = 0, total = 1, entry = GemPageEntry(tooLongKey, 1, false))
        val invalidBuffer = buffer(helper)
        val invalidRejected =
            try {
                GemPage.CODEC.encode(invalidBuffer, invalidPage)
                false
            } catch (_: IllegalArgumentException) {
                true
            } finally {
                invalidBuffer.release()
            }
        helper.assertTrue(invalidRejected, "257-byte key was encoded")

        val malformedBuffer = buffer(helper)
        malformedBuffer.writeVarInt(1)
        malformedBuffer.writeVarInt(0)
        malformedBuffer.writeUUID(session)
        malformedBuffer.writeLong(1)
        malformedBuffer.writeVarInt(GemAction.entries.size)
        val invalidActionRejected =
            try {
                GemRequest.CODEC.decode(malformedBuffer)
                false
            } catch (_: DecoderException) {
                true
            } finally {
                malformedBuffer.release()
            }
        helper.assertTrue(invalidActionRejected, "Unknown action index was decoded")
        helper.succeed()
    }

    private fun buffer(helper: GameTestHelper): RegistryFriendlyByteBuf =
        RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            helper.level.registryAccess(),
            net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE,
        )

    private fun page(
        session: UUID,
        revision: Long,
        index: Int,
        total: Int,
        entry: GemPageEntry,
    ): GemPage =
        GemPage(
            menuId = 1,
            upgradeSlot = 0,
            sessionId = session,
            revision = revision,
            page = index,
            totalPages = total,
            entries = listOf(entry),
            locked = false,
        )

    private fun entry(id: String): GemPageEntry = GemPageEntry(GemKey("test:$id", "pure"), 1, false)
}
