package org.mocoserver.sophisticatedgemcase.tests

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemFilterMode
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.network.GemPage
import org.mocoserver.sophisticatedgemcase.network.GemPageAccumulator
import org.mocoserver.sophisticatedgemcase.network.GemProcessingPreviewPayload
import org.mocoserver.sophisticatedgemcase.network.GemProcessingSync
import org.mocoserver.sophisticatedgemcase.network.GemSettingsPage
import org.mocoserver.sophisticatedgemcase.network.GemSettingsPageAccumulator
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingPreview
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingReason
import java.util.UUID

/** 加工のclient送信可否がcounts/settings/previewの一世代に揃うことを確認する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemProcessingSyncGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun permitsPreviewAndSupplementToggleThroughLockedCounts(helper: GameTestHelper) {
        val session = UUID.randomUUID()
        val counts = counts(session, revision = 3, locked = true)
        val settings = settings(session, revision = 3, locked = false)
        helper.assertTrue(
            GemProcessingSync.canRequestPreview(counts, settings, OUTPUT),
            "Locked counts blocked requesting a reason preview",
        )
        helper.assertTrue(
            GemProcessingSync.canSetBackpackMaterials(
                counts,
                settings(session, revision = 3, locked = true),
                payload(session, 3, OUTPUT, GemProcessingReason.LOCKED_COUNTS),
            ),
            "Locked counts and transfer settings blocked a ready processing setting change",
        )
        helper.assertTrue(
            !GemProcessingSync.canSetBackpackMaterials(
                counts,
                settings,
                payload(
                    session,
                    3,
                    OUTPUT,
                    GemProcessingReason.LOCKED_COUNTS,
                    settingsLocked = true,
                ),
            ),
            "Locked processing settings allowed editing",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun allowsProcessingWithLockedTransferSettingsButReadyMatchingPreview(helper: GameTestHelper) {
        val session = UUID.randomUUID()
        val counts = counts(session, revision = 4, locked = false)
        val settings = settings(session, revision = 4, locked = true)
        val preview = payload(session, revision = 4, output = OUTPUT, reason = GemProcessingReason.READY)
        helper.assertTrue(
            GemProcessingSync.canProcess(counts, settings, preview, OUTPUT),
            "Transfer settings lock blocked processing despite complete counts and ready preview",
        )
        helper.assertTrue(
            GemProcessingSync.canProcess(
                counts,
                settings,
                payload(
                    session,
                    revision = 4,
                    output = OUTPUT,
                    reason = GemProcessingReason.READY,
                    hasMaterials = false,
                    useBackpackMaterials = true,
                ),
                OUTPUT,
            ),
            "Ready backpack-only material plan was blocked when dedicated slots were empty",
        )
        helper.assertTrue(
            !GemProcessingSync.canProcess(
                counts,
                settings,
                payload(session, revision = 4, output = OUTPUT, reason = GemProcessingReason.MATERIAL_SHORTAGE),
                OUTPUT,
            ),
            "Nonready preview allowed processing",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsMixedIncompleteLockedAndStaleGenerations(helper: GameTestHelper) {
        val session = UUID.randomUUID()
        val counts = counts(session, revision = 8, locked = false)
        val settings = settings(session, revision = 8, locked = false)
        val ready = payload(session, revision = 8, output = OUTPUT, reason = GemProcessingReason.READY)
        helper.assertTrue(
            !GemProcessingSync.canProcess(counts(session, revision = 8, locked = true), settings, ready, OUTPUT),
            "Locked counts allowed processing",
        )
        helper.assertTrue(
            !GemProcessingSync.canProcess(counts, settings(session, revision = 9, locked = false), ready, OUTPUT),
            "Mixed settings revision allowed processing",
        )
        helper.assertTrue(
            !GemProcessingSync.canProcess(
                counts,
                settings(UUID.randomUUID(), revision = 8, locked = false),
                ready,
                OUTPUT,
            ),
            "Foreign settings session allowed processing",
        )
        helper.assertTrue(
            !GemProcessingSync.canProcess(
                counts,
                settings,
                payload(session, 7, OUTPUT, GemProcessingReason.READY),
                OUTPUT,
            ),
            "Stale preview allowed processing",
        )
        helper.assertTrue(
            !GemProcessingSync.canProcess(counts, settings, ready, OTHER_OUTPUT),
            "Preview for a different selected output allowed processing",
        )
        helper.assertTrue(
            !GemProcessingSync.canProcess(counts, settings, ready, output = null),
            "Missing selected output allowed processing",
        )
        helper.assertTrue(
            !GemProcessingSync.canRequestPreview(counts, settings(session, revision = 8, locked = false), null),
            "Missing output allowed preview request",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun doesNotSendUntilCountsAndSettingsPagesAreComplete(helper: GameTestHelper) {
        val session = UUID.randomUUID()
        val counts = GemPageAccumulator()
        counts.accept(GemPage(1, 0, session, 2, 0, 2, emptyList(), false))
        val settings = GemSettingsPageAccumulator()
        settings.accept(settingsPage(session, 2, 0, 2, locked = false))
        helper.assertTrue(
            !GemProcessingSync.canRequestPreview(counts, settings, OUTPUT),
            "Partial pages allowed a preview request",
        )
        helper.assertTrue(
            !GemProcessingSync.canSetBackpackMaterials(
                counts,
                settings,
                payload(session, 2, OUTPUT, GemProcessingReason.READY),
            ),
            "Partial pages allowed a setting change",
        )
        helper.succeed()
    }

    private fun counts(
        session: UUID,
        revision: Long,
        locked: Boolean,
    ): GemPageAccumulator =
        GemPageAccumulator().apply {
            accept(GemPage(1, 0, session, revision, 0, 1, emptyList(), locked))
        }

    private fun settings(
        session: UUID,
        revision: Long,
        locked: Boolean,
    ): GemSettingsPageAccumulator =
        GemSettingsPageAccumulator().apply {
            accept(settingsPage(session, revision, 0, 1, locked))
        }

    private fun settingsPage(
        session: UUID,
        revision: Long,
        page: Int,
        total: Int,
        locked: Boolean,
    ): GemSettingsPage =
        GemSettingsPage(
            1,
            0,
            session,
            revision,
            page,
            total,
            locked,
            depositEnabled = false,
            restockEnabled = false,
            depositMode = GemFilterMode.ALL,
            restockMode = GemFilterMode.ALL,
            entries = emptyList(),
        )

    private fun payload(
        session: UUID,
        revision: Long,
        output: GemKey,
        reason: GemProcessingReason,
        hasMaterials: Boolean = reason == GemProcessingReason.READY,
        useBackpackMaterials: Boolean = false,
        settingsLocked: Boolean = false,
    ): GemProcessingPreviewPayload =
        GemProcessingPreviewPayload(
            1,
            0,
            session,
            revision,
            recipeEpoch = 0,
            preview =
                GemProcessingPreview(
                    output,
                    hasMaterials = hasMaterials,
                    materialsLocked = false,
                    useBackpackMaterials = useBackpackMaterials,
                    settingsLocked = settingsLocked,
                    reason = reason,
                    recipeId = ResourceLocation.parse("test:recipe"),
                    costs = emptyList(),
                ),
        )

    private val OUTPUT = GemKey("test:gem", "chipped")
    private val OTHER_OUTPUT = GemKey("test:gem", "flawed")
}
