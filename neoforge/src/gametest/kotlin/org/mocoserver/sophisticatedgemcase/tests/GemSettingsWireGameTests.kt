package org.mocoserver.sophisticatedgemcase.tests

import io.netty.buffer.Unpooled
import io.netty.handler.codec.DecoderException
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.RegistryFriendlyByteBuf
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemFilter
import org.mocoserver.sophisticatedgemcase.core.GemFilterMode
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.core.GemTransferDirection
import org.mocoserver.sophisticatedgemcase.core.GemTransferRule
import org.mocoserver.sophisticatedgemcase.core.GemTransferSettings
import org.mocoserver.sophisticatedgemcase.network.GemPage
import org.mocoserver.sophisticatedgemcase.network.GemPageAccumulator
import org.mocoserver.sophisticatedgemcase.network.GemPageEntry
import org.mocoserver.sophisticatedgemcase.network.GemSettingsAction
import org.mocoserver.sophisticatedgemcase.network.GemSettingsEntry
import org.mocoserver.sophisticatedgemcase.network.GemSettingsPage
import org.mocoserver.sophisticatedgemcase.network.GemSettingsPageAccumulator
import org.mocoserver.sophisticatedgemcase.network.GemSettingsPages
import org.mocoserver.sophisticatedgemcase.network.GemSettingsRequest
import org.mocoserver.sophisticatedgemcase.network.GemSettingsSync
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsState
import java.util.UUID

/** 設定一覧の固定順、entry数とwire byte上限を検査する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemSettingsWireGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun partitionsSettingsIntoBoundedDeterministicPages(helper: GameTestHelper) {
        val ids = (0 until 4096).map(::maximumLengthGemId)
        val depositIds = ids.take(2048).toSet()
        val restockIds = ids.drop(2048).toSet()
        val settings =
            GemTransferSettings(
                GemTransferRule(true, GemFilter.of(GemFilterMode.INCLUDE, depositIds, emptySet())),
                GemTransferRule(false, GemFilter.of(GemFilterMode.EXCLUDE, restockIds, emptySet())),
            )
        val first = GemSettingsPages.partition(GemSettingsState.Ready(settings))
        val second = GemSettingsPages.partition(GemSettingsState.Ready(settings))
        helper.assertTrue(first == second, "Settings page order is nondeterministic")
        helper.assertTrue(first.size == 64, "Maximum settings must use 64 pages")
        helper.assertTrue(first.all { it.size <= GemSettingsPages.MAX_PAGE_ENTRIES }, "Page exceeds entry limit")
        helper.assertTrue(first.flatten().toSet().size == first.sumOf { it.size }, "Settings entries duplicated")
        helper.assertTrue(first.sumOf { it.size } == 4096, "Maximum settings lost entries")
        val accumulator = GemSettingsPageAccumulator()
        val session = UUID.randomUUID()
        first.forEachIndexed { index, entries ->
            val page = settingsPage(session, 0, index, first.size, entries)
            val pageBuffer = buffer(helper)
            try {
                GemSettingsPage.CODEC.encode(pageBuffer, page)
                helper.assertTrue(
                    pageBuffer.readableBytes() <= GemSettingsPages.MAX_PAGE_BYTES,
                    "Settings page exceeds wire byte limit",
                )
                helper.assertTrue(
                    page.entries.size <= GemSettingsPages.MAX_PAGE_ENTRIES,
                    "Settings page exceeds wire entry limit",
                )
                helper.assertTrue(
                    GemSettingsPage.CODEC.decode(pageBuffer) == page,
                    "Maximum-length page changed on wire",
                )
            } finally {
                pageBuffer.release()
            }
            helper.assertTrue(accumulator.accept(page), "Maximum settings page $index was rejected")
        }
        helper.assertTrue(accumulator.complete && accumulator.entries.size == 4096, "Maximum settings did not assemble")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun lockedAndEmptySettingsStillHavePageZero(helper: GameTestHelper) {
        val empty = GemSettingsPages.partition(GemSettingsState.Ready(GemTransferSettings.DEFAULT))
        val locked = GemSettingsPages.partition(GemSettingsState.Locked("damaged"))
        helper.assertTrue(empty.size == 1 && empty.single().isEmpty(), "Empty state must have one empty page")
        helper.assertTrue(locked.size == 1 && locked.single().isEmpty(), "Locked state must have one empty page")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun keepsUnknownRetainedValuesRepresentableAsEntries(helper: GameTestHelper) {
        val settings =
            GemTransferSettings(
                GemTransferRule(true, GemFilter.of(GemFilterMode.EXCLUDE, setOf("future:gem"), setOf("future_purity"))),
                GemTransferSettings.DEFAULT.restock,
            )
        val entries: List<GemSettingsEntry> = GemSettingsPages.partition(GemSettingsState.Ready(settings)).flatten()
        helper.assertTrue(
            entries.any {
                it.direction == GemTransferDirection.DEPOSIT && it.value == "future:gem"
            },
            "Unknown ID disappeared",
        )
        helper.assertTrue(
            entries.any {
                it.direction == GemTransferDirection.DEPOSIT && it.value == "future_purity"
            },
            "Unknown purity disappeared",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun acceptsOnlyUniquePagesFromOneSettingsGeneration(helper: GameTestHelper) {
        val session = UUID.randomUUID()
        val entryA =
            GemSettingsEntry(
                GemTransferDirection.DEPOSIT,
                org.mocoserver.sophisticatedgemcase.network.GemFilterAxis.GEM_ID,
                "test:a",
            )
        val entryB =
            GemSettingsEntry(
                GemTransferDirection.RESTOCK,
                org.mocoserver.sophisticatedgemcase.network.GemFilterAxis.PURITY,
                "future_purity",
            )
        val first = settingsPage(session, 7, 0, 2, listOf(entryA))
        val second = settingsPage(session, 7, 1, 2, listOf(entryB))
        val accumulator = GemSettingsPageAccumulator()
        helper.assertTrue(!accumulator.accept(second), "Page arrived without its page zero")
        helper.assertTrue(accumulator.accept(first), "Page zero was rejected")
        helper.assertTrue(!accumulator.complete, "Partial settings became editable")
        helper.assertTrue(accumulator.nextPage() == 1, "Next page index is incorrect")
        helper.assertTrue(!accumulator.accept(second.copy(revision = 8)), "Mixed revision was accepted")
        helper.assertTrue(accumulator.accept(second), "Page from same generation was rejected")
        helper.assertTrue(
            accumulator.complete && accumulator.entries == listOf(entryA, entryB),
            "Complete settings were not assembled in page order",
        )
        helper.assertTrue(!accumulator.accept(second), "Duplicate page was accepted")
        helper.assertTrue(!accumulator.accept(first.copy(revision = 6)), "Stale page replaced current settings")
        helper.assertTrue(
            !accumulator.accept(second.copy(sessionId = UUID.randomUUID())),
            "Foreign session page was accepted without page zero",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun boundsAndRoundTripsSettingsPageAndRequestWire(helper: GameTestHelper) {
        val value = "test:" + "a".repeat(251)
        val page =
            settingsPage(
                UUID.randomUUID(),
                9,
                0,
                1,
                listOf(
                    GemSettingsEntry(
                        GemTransferDirection.DEPOSIT,
                        org.mocoserver.sophisticatedgemcase.network.GemFilterAxis.GEM_ID,
                        value,
                    ),
                ),
            )
        val pageBuffer = buffer(helper)
        try {
            GemSettingsPage.CODEC.encode(pageBuffer, page)
            helper.assertTrue(
                pageBuffer.readableBytes() <= GemSettingsPages.MAX_PAGE_BYTES,
                "Settings page exceeds byte limit",
            )
            helper.assertTrue(GemSettingsPage.CODEC.decode(pageBuffer) == page, "Settings page round trip changed data")
        } finally {
            pageBuffer.release()
        }

        val request =
            GemSettingsRequest(
                2,
                0,
                UUID.randomUUID(),
                9,
                GemSettingsAction.SET_GEM_ID,
                GemTransferDirection.DEPOSIT,
                null,
                null,
                value,
                true,
                0,
            )
        val requestBuffer = buffer(helper)
        try {
            GemSettingsRequest.CODEC.encode(requestBuffer, request)
            helper.assertTrue(
                GemSettingsRequest.CODEC.decode(requestBuffer) == request,
                "Settings request round trip changed data",
            )
        } finally {
            requestBuffer.release()
        }

        val tooLong = request.copy(value = "test:" + "a".repeat(252))
        val invalidBuffer = buffer(helper)
        val rejected =
            try {
                GemSettingsRequest.CODEC.encode(invalidBuffer, tooLong)
                false
            } catch (_: IllegalArgumentException) {
                true
            } finally {
                invalidBuffer.release()
            }
        helper.assertTrue(rejected, "257-byte setting value was encoded")

        val modeRequest =
            request.copy(
                action = GemSettingsAction.SET_MODE,
                mode = GemFilterMode.INCLUDE,
                value = null,
                selected = null,
            )
        helper.assertTrue(rejectsEnum(modeRequest, 26, helper), "Unknown action enum was decoded")
        helper.assertTrue(rejectsEnum(modeRequest, 27, helper), "Unknown direction enum was decoded")
        helper.assertTrue(rejectsEnum(modeRequest, 30, helper), "Unknown mode enum was decoded")
        helper.succeed()
    }

    private fun rejectsEnum(
        request: GemSettingsRequest,
        offset: Int,
        helper: GameTestHelper,
    ): Boolean {
        val wire = buffer(helper)
        return try {
            GemSettingsRequest.CODEC.encode(wire, request)
            wire.setByte(offset, 127)
            GemSettingsRequest.CODEC.decode(wire)
            false
        } catch (_: DecoderException) {
            true
        } finally {
            wire.release()
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun settingsAreEditableOnlyWhenBothListsShareACompleteUnlockedGeneration(helper: GameTestHelper) {
        val session = UUID.randomUUID()
        val settings = GemSettingsPageAccumulator()
        helper.assertTrue(
            settings.accept(settingsPage(session, 5, 0, 1, emptyList())),
            "Settings page zero was rejected",
        )
        helper.assertTrue(settings.complete, "Single-page settings were not complete")
        val counts = GemPageAccumulator()
        helper.assertTrue(!GemSettingsSync.canEdit(counts, settings), "Settings edited without the counts list")
        helper.assertTrue(counts.accept(countPage(session, 5, 0, 2)), "First counts page was rejected")
        helper.assertTrue(!GemSettingsSync.canEdit(counts, settings), "Partial counts list allowed settings edit")
        helper.assertTrue(counts.accept(countPage(session, 5, 1, 2)), "Second counts page was rejected")
        helper.assertTrue(GemSettingsSync.canEdit(counts, settings), "Complete matching lists were not editable")

        val newerSettings = GemSettingsPageAccumulator()
        helper.assertTrue(
            newerSettings.accept(settingsPage(session, 6, 0, 1, emptyList())),
            "New settings generation was rejected",
        )
        helper.assertTrue(!GemSettingsSync.canEdit(counts, newerSettings), "Different revisions were mixed")

        val lockedSettings = GemSettingsPageAccumulator()
        val lockedPage = settingsPage(session, 5, 0, 1, emptyList()).copy(locked = true)
        helper.assertTrue(lockedSettings.accept(lockedPage), "Locked settings page was rejected")
        helper.assertTrue(!GemSettingsSync.canEdit(counts, lockedSettings), "Locked settings were editable")
        helper.succeed()
    }

    private fun settingsPage(
        session: UUID,
        revision: Long,
        page: Int,
        total: Int,
        entries: List<GemSettingsEntry>,
    ): GemSettingsPage =
        GemSettingsPage(
            1,
            0,
            session,
            revision,
            page,
            total,
            false,
            true,
            false,
            GemFilterMode.INCLUDE,
            GemFilterMode.ALL,
            entries,
        )

    private fun maximumLengthGemId(index: Int): String {
        val suffix = index.toString()
        return "test:${"a".repeat(251 - suffix.length)}$suffix"
    }

    private fun countPage(
        session: UUID,
        revision: Long,
        page: Int,
        total: Int,
    ): GemPage =
        GemPage(
            1,
            0,
            session,
            revision,
            page,
            total,
            listOf(GemPageEntry(GemKey("test:gem_$page", "pure"), 1, false)),
            false,
        )

    private fun buffer(helper: GameTestHelper): RegistryFriendlyByteBuf =
        RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            helper.level.registryAccess(),
            net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE,
        )
}
