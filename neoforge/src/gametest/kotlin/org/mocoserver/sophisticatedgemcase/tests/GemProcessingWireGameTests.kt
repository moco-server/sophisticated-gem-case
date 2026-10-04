package org.mocoserver.sophisticatedgemcase.tests

import io.netty.buffer.Unpooled
import io.netty.handler.codec.DecoderException
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.network.GemProcessingAction
import org.mocoserver.sophisticatedgemcase.network.GemProcessingPreviewPayload
import org.mocoserver.sophisticatedgemcase.network.GemProcessingRequest
import org.mocoserver.sophisticatedgemcase.processing.GemMaterialCost
import org.mocoserver.sophisticatedgemcase.processing.GemMaterialSide
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingPreview
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingReason
import java.util.UUID

/** 加工requestのfield shapeとpreview payloadのwire上限を専用サーバーで検査する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemProcessingWireGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun roundTripsOutputRequestAndSettingsToggleShape(helper: GameTestHelper) {
        val outputRequest = request(output = GemKey("test:${"g".repeat(251)}", "perfect"))
        helper.assertTrue(roundTrip(outputRequest) == outputRequest, "Maximum 256-byte output ID changed on wire")

        val settingRequest =
            request(
                action = GemProcessingAction.SET_BACKPACK_MATERIALS,
                output = null,
                enabled = true,
            )
        helper.assertTrue(roundTrip(settingRequest) == settingRequest, "Processing setting request changed on wire")

        val viewRequest =
            request(
                action = GemProcessingAction.SET_PROCESSING_VIEW,
                output = null,
                enabled = false,
                revision = 0,
            )
        helper.assertTrue(roundTrip(viewRequest) == viewRequest, "Processing view request changed on wire")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsInvalidRequestShapesAndOutputValues(helper: GameTestHelper) {
        val base = request()
        val invalid =
            listOf(
                base.copy(enabled = false),
                base.copy(action = GemProcessingAction.SET_BACKPACK_MATERIALS),
                base.copy(action = GemProcessingAction.SET_PROCESSING_VIEW),
                base.copy(action = GemProcessingAction.SET_PROCESSING_VIEW, output = null, enabled = null),
                base.copy(action = GemProcessingAction.SET_PROCESSING_VIEW, output = GemKey("test:gem", "chipped")),
                base.copy(output = GemKey("test:gem", "unknown")),
                base.copy(output = GemKey("test:gem", "cracked")),
                base.copy(output = GemKey("test:${"g".repeat(252)}", "chipped")),
                base.copy(menuId = -1),
            )
        invalid.forEach { candidate ->
            helper.assertTrue(rejectsEncode(candidate), "Invalid request was encoded: $candidate")
        }
        helper.assertTrue(rejectsRequestEnum(base, 26, helper), "Unknown processing action was decoded")
        val trailing = buffer(helper)
        val rejectsTrailing =
            try {
                GemProcessingRequest.CODEC.encode(trailing, base)
                trailing.writeBoolean(true)
                GemProcessingRequest.CODEC.decode(trailing)
                false
            } catch (_: DecoderException) {
                true
            } finally {
                trailing.release()
            }
        helper.assertTrue(rejectsTrailing, "Unexpected request field was accepted")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun roundTripsPreviewCostsWithResourceLocationIds(helper: GameTestHelper) {
        val preview =
            preview(
                costs =
                    listOf(
                        GemMaterialCost(ResourceLocation.parse("test:iron_ingot"), 3, GemMaterialSide.LEFT),
                        GemMaterialCost(ResourceLocation.parse("test:quartz"), 1, GemMaterialSide.RIGHT),
                    ),
            )
        val payload = GemProcessingPreviewPayload(3, 0, UUID.randomUUID(), 8, 14, preview)
        val restored = roundTrip(payload)
        helper.assertTrue(
            restored.menuId == payload.menuId &&
                restored.upgradeSlot == payload.upgradeSlot &&
                restored.sessionId == payload.sessionId &&
                restored.revision == payload.revision &&
                restored.recipeEpoch == payload.recipeEpoch,
            "Gem processing preview header changed on wire",
        )
        helper.assertTrue(
            restored.preview.output == preview.output &&
                restored.preview.reason == preview.reason &&
                restored.preview.recipeId == preview.recipeId &&
                restored.preview.costs == preview.costs,
            "Gem processing preview content changed on wire",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsOversizedPayloadAndBoundsCostCountBeforeAllocation(helper: GameTestHelper) {
        val oversized = buffer(helper)
        try {
            oversized.writeZero(GemProcessingPreviewPayload.MAX_PAYLOAD_BYTES + 1)
            helper.assertTrue(rejectsPreviewDecode(oversized), "Oversized payload was decoded")
        } finally {
            oversized.release()
        }

        val tooManyCosts = buffer(helper)
        try {
            writePreviewHeader(tooManyCosts, GemProcessingPreviewPayload.MAX_COSTS + 1)
            helper.assertTrue(rejectsPreviewDecode(tooManyCosts), "Oversized cost list was decoded")
        } finally {
            tooManyCosts.release()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun refusesToTruncatePreviewThatCannotFitTheWireBudget(helper: GameTestHelper) {
        val repeatedCost = GemMaterialCost(ResourceLocation.parse("test:ore"), 64, GemMaterialSide.LEFT)
        val tooLarge = preview(costs = List(GemProcessingPreviewPayload.MAX_COSTS) { repeatedCost })
        val payload = GemProcessingPreviewPayload(1, 0, UUID.randomUUID(), 0, 0, tooLarge)
        val buffer = buffer(helper)
        val rejected =
            try {
                GemProcessingPreviewPayload.CODEC.encode(buffer, payload)
                false
            } catch (_: IllegalArgumentException) {
                true
            } finally {
                buffer.release()
            }
        helper.assertTrue(rejected, "Oversized preview was silently truncated or encoded")
        helper.succeed()
    }

    private fun request(
        action: GemProcessingAction = GemProcessingAction.REQUEST_PREVIEW,
        output: GemKey? = GemKey("test:gem", "chipped"),
        enabled: Boolean? = null,
        revision: Long = 9,
    ): GemProcessingRequest = GemProcessingRequest(2, 0, UUID.randomUUID(), revision, action, output, enabled)

    private fun preview(costs: List<GemMaterialCost> = emptyList()): GemProcessingPreview =
        GemProcessingPreview(
            GemKey("test:gem", "chipped"),
            hasMaterials = true,
            materialsLocked = false,
            useBackpackMaterials = false,
            settingsLocked = false,
            reason = GemProcessingReason.READY,
            recipeId = ResourceLocation.parse("test:recipe"),
            costs = costs,
        )

    private fun rejectsEncode(request: GemProcessingRequest): Boolean {
        val buffer = Unpooled.buffer()
        return try {
            GemProcessingRequest.CODEC.encode(wrap(buffer), request)
            false
        } catch (_: IllegalArgumentException) {
            true
        } finally {
            buffer.release()
        }
    }

    private fun rejectsRequestEnum(
        request: GemProcessingRequest,
        offset: Int,
        helper: GameTestHelper,
    ): Boolean {
        val buffer = buffer(helper)
        return try {
            GemProcessingRequest.CODEC.encode(buffer, request)
            buffer.setByte(offset, 127)
            GemProcessingRequest.CODEC.decode(buffer)
            false
        } catch (_: DecoderException) {
            true
        } finally {
            buffer.release()
        }
    }

    private fun rejectsPreviewDecode(buffer: RegistryFriendlyByteBuf): Boolean =
        try {
            GemProcessingPreviewPayload.CODEC.decode(buffer)
            false
        } catch (_: DecoderException) {
            true
        }

    private fun roundTrip(value: GemProcessingRequest): GemProcessingRequest {
        val buffer = wrap(Unpooled.buffer())
        return try {
            GemProcessingRequest.CODEC.encode(buffer, value)
            GemProcessingRequest.CODEC.decode(buffer)
        } finally {
            buffer.release()
        }
    }

    private fun roundTrip(value: GemProcessingPreviewPayload): GemProcessingPreviewPayload {
        val buffer = wrap(Unpooled.buffer())
        return try {
            GemProcessingPreviewPayload.CODEC.encode(buffer, value)
            GemProcessingPreviewPayload.CODEC.decode(buffer)
        } finally {
            buffer.release()
        }
    }

    private fun writePreviewHeader(
        buffer: RegistryFriendlyByteBuf,
        costs: Int,
    ) {
        buffer.writeVarInt(1)
        buffer.writeVarInt(0)
        buffer.writeUUID(UUID.randomUUID())
        buffer.writeLong(0)
        buffer.writeLong(0)
        buffer.writeUtf("test:gem", GemProcessingPreviewPayload.MAX_ID_BYTES)
        buffer.writeUtf("chipped", GemProcessingPreviewPayload.MAX_ID_BYTES)
        repeat(4) { buffer.writeBoolean(false) }
        buffer.writeVarInt(GemProcessingReason.READY.ordinal)
        buffer.writeBoolean(false)
        buffer.writeVarInt(costs)
    }

    private fun buffer(helper: GameTestHelper): RegistryFriendlyByteBuf =
        RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            helper.level.registryAccess(),
            net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE,
        )

    private fun wrap(buffer: io.netty.buffer.ByteBuf): RegistryFriendlyByteBuf =
        RegistryFriendlyByteBuf(
            buffer,
            net.minecraft.core.RegistryAccess.EMPTY,
            net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE,
        )
}
