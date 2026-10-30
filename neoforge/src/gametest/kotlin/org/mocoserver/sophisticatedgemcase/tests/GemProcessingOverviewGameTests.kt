package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingMenu
import dev.shadowsoffire.apotheosis.socket.gem.cutting.PurityUpgradeRecipe
import io.netty.buffer.Unpooled
import io.netty.handler.codec.DecoderException
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeHolder
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.menu.GemCaseContainer
import org.mocoserver.sophisticatedgemcase.menu.GemProcessingActions
import org.mocoserver.sophisticatedgemcase.network.GemProcessingAction
import org.mocoserver.sophisticatedgemcase.network.GemProcessingOverview
import org.mocoserver.sophisticatedgemcase.network.GemProcessingOverviewPayload
import org.mocoserver.sophisticatedgemcase.network.GemProcessingRequest
import org.mocoserver.sophisticatedgemcase.network.GemProcessingTarget
import org.mocoserver.sophisticatedgemcase.processing.GemMaterialCost
import org.mocoserver.sophisticatedgemcase.processing.GemMaterialSide
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingPreview
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingReason
import org.mocoserver.sophisticatedgemcase.processing.GemRecipeEpoch
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialHandler
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import java.util.UUID

/** 全強化先を同じ選択・保存世代へ束縛し、無選択でも素材を同期する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemProcessingOverviewGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun sharesRecipeExplorationBudgetAcrossFiveOutputs(helper: GameTestHelper) {
        val (old, player, _) = GemMenuFixtures.menu(helper)
        val manager = helper.level.recipeManager
        val original = manager.recipes.toList()
        try {
            old.storageWrapper.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
            val menu = BackpackContainer(18, player, old.backpackContext)
            player.containerMenu = menu
            val container = menu.upgradeContainers[0] as GemCaseContainer
            val gem =
                GemRegistry.INSTANCE.values
                    .first { it.minPurity == Purity.CRACKED }
                    .id
                    .toString()
            val synthetic =
                List(300) {
                    RecipeHolder(
                        ResourceLocation.parse("sophisticated_gem_case:test/batch_budget_$it"),
                        PurityUpgradeRecipe(Purity.CRACKED, emptyList(), emptyList()),
                    )
                }
            manager.replaceRecipes(original + synthetic)
            GemRecipeEpoch.advance()
            val counts = GemCounts.of(Purity.entries.dropLast(1).associate { GemKey(gem, it.serializedName) to 2 })
            helper.assertTrue(
                container.upgradeWrapper.replace(GemCounts.of(emptyMap()), counts),
                "Could not prepare all inputs",
            )
            val session = container.sessionFor(menu)
            val request =
                GemProcessingRequest(
                    menu.containerId,
                    0,
                    session.id,
                    session.revision,
                    GemProcessingAction.REQUEST_GEM_PREVIEWS,
                    null,
                    null,
                    gem,
                    1,
                )
            helper.assertTrue(GemProcessingActions.perform(player, request), "Batch request was rejected")
            val overview = GemProcessingOverview.create(menu, container, session, player.serverLevel())
            helper.assertTrue(
                overview.previews.first().reason == GemProcessingReason.MATERIAL_SHORTAGE,
                "First output did not receive the shared budget",
            )
            helper.assertTrue(
                overview.previews.drop(1).all { it.reason == GemProcessingReason.BUDGET_EXCEEDED },
                "Later outputs reset the exploration budget",
            )
            helper.assertTrue(
                (container.upgradeWrapper.snapshot() as GemStorageState.Ready).counts == counts,
                "Preview changed counts",
            )
            helper.succeed()
        } finally {
            manager.replaceRecipes(original)
            GemRecipeEpoch.advance()
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun boundsBatchBeforeAllocatingAndRejectsMixedKinds(helper: GameTestHelper) {
        val previews =
            Purity.entries.filter { it != Purity.CRACKED }.map {
                GemProcessingPreview(
                    GemKey("test:gem", it.serializedName),
                    false,
                    false,
                    false,
                    false,
                    GemProcessingReason.INPUT_SHORTAGE,
                    null,
                    emptyList(),
                )
            }
        val payload =
            GemProcessingOverviewPayload(
                1,
                0,
                UUID.randomUUID(),
                0,
                0,
                "test:gem",
                1,
                false,
                false,
                false,
                false,
                previews,
            )
        val buffer =
            RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                helper.level.registryAccess(),
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE,
            )
        try {
            val largePreviews =
                previews.map {
                    GemProcessingPreview(
                        it.output,
                        false,
                        false,
                        false,
                        false,
                        GemProcessingReason.READY,
                        null,
                        List(1000) { GemMaterialCost(ResourceLocation.parse("test:iron"), 64, GemMaterialSide.LEFT) },
                    )
                }
            val invalid =
                listOf(
                    payload.copy(previews = previews + previews.first()),
                    payload.copy(gemId = "test:other"),
                    payload.copy(gemId = null),
                    payload.copy(selectionId = -1),
                    payload.copy(previews = largePreviews),
                )
            invalid.forEach { candidate ->
                val rejected =
                    try {
                        GemProcessingOverviewPayload.CODEC.encode(buffer, candidate)
                        false
                    } catch (_: IllegalArgumentException) {
                        true
                    }
                helper.assertTrue(rejected, "Invalid batch was encoded")
                buffer.clear()
            }
            GemProcessingOverviewPayload.CODEC.encode(buffer, payload)
            // 固定headerではkindの直後に連番8byte・4flag・件数が続く。
            buffer.readVarInt()
            buffer.readVarInt()
            buffer.readUUID()
            buffer.readLong()
            buffer.readLong()
            buffer.readBoolean()
            buffer.readUtf(256)
            buffer.readLong()
            repeat(4) { buffer.readBoolean() }
            buffer.setByte(buffer.readerIndex(), 6)
            buffer.readerIndex(0)
            val rejected =
                try {
                    GemProcessingOverviewPayload.CODEC.decode(buffer)
                    false
                } catch (_: DecoderException) {
                    true
                }
            helper.assertTrue(rejected, "Six outputs were allocated")
            buffer.clear()
            buffer.writeZero(GemProcessingOverviewPayload.MAX_PAYLOAD_BYTES + 1)
            val oversizeRejected =
                try {
                    GemProcessingOverviewPayload.CODEC.decode(buffer)
                    false
                } catch (_: DecoderException) {
                    true
                }
            helper.assertTrue(oversizeRejected, "Oversized batch was decoded")
            helper.succeed()
        } finally {
            buffer.release()
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun processesEachOutputAndRejectsForeignOrStaleSelection(helper: GameTestHelper) {
        val (old, player, _) = GemMenuFixtures.menu(helper)
        try {
            old.storageWrapper.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
            val menu = BackpackContainer(18, player, old.backpackContext)
            player.containerMenu = menu
            val container = menu.upgradeContainers[0] as GemCaseContainer
            val gem =
                GemRegistry.INSTANCE.values
                    .first { it.minPurity == Purity.CRACKED }
                    .id
                    .toString()
            val materials = GemMaterialHandler(menu.storageWrapper, helper.level) { true }
            Purity.entries.dropLast(1).forEachIndexed { index, inputPurity ->
                val input = GemKey(gem, inputPurity.serializedName)
                val output = GemKey(gem, inputPurity.next().serializedName)
                val wrapper = container.upgradeWrapper
                val before = (wrapper.snapshot() as GemStorageState.Ready).counts
                helper.assertTrue(wrapper.replace(before, GemCounts.of(mapOf(input to 2))), "Could not prepare input")
                val recipe =
                    GemCuttingMenu
                        .getRecipes(helper.level)
                        .first {
                            (it.value as? PurityUpgradeRecipe)?.purity() == inputPurity
                        }.value as PurityUpgradeRecipe
                val left = recipe.left().first()
                val right = recipe.right().first()
                materials.setStackInSlot(0, left.getItems().first().copyWithCount(left.count()))
                materials.setStackInSlot(1, right.getItems().first().copyWithCount(right.count()))
                val session = container.sessionFor(menu)
                val selection = index.toLong() + 1
                val request =
                    GemProcessingRequest(
                        menu.containerId,
                        0,
                        session.id,
                        session.revision,
                        GemProcessingAction.REQUEST_GEM_PREVIEWS,
                        null,
                        null,
                        gem,
                        selection,
                    )
                helper.assertTrue(GemProcessingActions.perform(player, request), "Could not preview kind")
                GemProcessingOverview.create(menu, container, session, player.serverLevel())
                val process = request.copy(action = GemProcessingAction.PROCESS_ONE, output = output, gemId = null)
                helper.assertTrue(
                    !GemProcessingActions.perform(player, process.copy(output = GemKey("test:other", output.purity))) &&
                        !GemProcessingActions.perform(player, process.copy(selectionId = selection - 1)),
                    "Foreign or old selection processed",
                )
                helper.assertTrue(GemProcessingActions.perform(player, process), "Output $output did not process")
                val after = (wrapper.snapshot() as GemStorageState.Ready).counts
                helper.assertTrue(after.count(input) == 0 && after.count(output) == 1, "Incorrect per-output counts")
                helper.assertTrue(!GemProcessingActions.perform(player, process), "Old revision processed twice")
            }
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun synchronizesMaterialsWithoutSelectionAndRejectsOldSelection(helper: GameTestHelper) {
        val (old, player, _) = GemMenuFixtures.menu(helper)
        try {
            old.storageWrapper.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
            val menu = BackpackContainer(18, player, old.backpackContext)
            player.containerMenu = menu
            val container = menu.upgradeContainers[0] as GemCaseContainer
            val session = container.sessionFor(menu)
            val request =
                GemProcessingRequest(
                    menu.containerId,
                    0,
                    session.id,
                    session.revision,
                    GemProcessingAction.REQUEST_GEM_PREVIEWS,
                    null,
                    null,
                    gemId = null,
                    selectionId = 1,
                )
            helper.assertTrue(GemProcessingActions.perform(player, request), "Null selection was rejected")
            val empty = GemProcessingOverview.create(menu, container, session, player.serverLevel())
            helper.assertTrue(empty.gemId == null && empty.previews.isEmpty(), "Null selection fabricated an output")
            helper.assertTrue(!empty.materialsLocked && !empty.settingsLocked, "Null selection locked ready materials")
            val selected = request.copy(gemId = "test:gem", selectionId = 2)
            helper.assertTrue(GemProcessingActions.perform(player, selected), "Gem selection was rejected")
            val overview = GemProcessingOverview.create(menu, container, session, player.serverLevel())
            helper.assertTrue(overview.previews.size == 5, "Did not return five output purities")
            helper.assertTrue(
                overview.previews
                    .map { it.output }
                    .distinct()
                    .size == 5,
                "Repeated output purity",
            )
            helper.assertTrue(overview.previews.all { it.output.gemId == selected.gemId }, "Mixed gem kinds")
            helper.assertTrue(
                overview.matchesSelection(
                    GemProcessingTarget(menu.containerId, 0),
                    session.id,
                    session.revision,
                    selected.gemId,
                    2,
                ),
                "Current batch did not match its selection",
            )
            helper.assertTrue(
                !overview.matchesSelection(
                    GemProcessingTarget(menu.containerId, 0),
                    session.id,
                    session.revision,
                    selected.gemId,
                    3,
                ) &&
                    !overview.matchesSelection(
                        GemProcessingTarget(menu.containerId, 0),
                        session.id,
                        session.revision + 1,
                        selected.gemId,
                        2,
                    ) &&
                    !overview.matchesSelection(
                        GemProcessingTarget(menu.containerId, 0),
                        session.id,
                        session.revision,
                        "test:other",
                        2,
                    ),
                "Stale selection, revision or kind was accepted",
            )
            helper.assertTrue(
                !GemProcessingActions.perform(player, request),
                "Old null selection replaced current kind",
            )
            helper.assertTrue(
                !GemProcessingActions.perform(player, selected.copy(gemId = "test:other")),
                "Same selection generation changed gem kind",
            )
            val buffer =
                RegistryFriendlyByteBuf(
                    Unpooled.buffer(),
                    helper.level.registryAccess(),
                    net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE,
                )
            try {
                GemProcessingRequest.CODEC.encode(buffer, request)
                helper.assertTrue(
                    GemProcessingRequest.CODEC.decode(buffer) == request,
                    "Null selection changed on wire",
                )
                buffer.clear()
                GemProcessingRequest.CODEC.encode(buffer, selected)
                helper.assertTrue(
                    GemProcessingRequest.CODEC.decode(buffer) == selected,
                    "Gem selection changed on wire",
                )
                buffer.clear()
                GemProcessingOverviewPayload.CODEC.encode(buffer, overview)
                helper.assertTrue(buffer.readableBytes() <= 32768, "Batch exceeded the shared byte budget")
                val restored = GemProcessingOverviewPayload.CODEC.decode(buffer)
                helper.assertTrue(
                    restored.matchesSelection(
                        GemProcessingTarget(menu.containerId, 0),
                        session.id,
                        session.revision,
                        selected.gemId,
                        2,
                    ) &&
                        restored.previews.map { it.output } == overview.previews.map { it.output } &&
                        restored.previews.map { it.reason } == overview.previews.map { it.reason },
                    "Batch changed on wire",
                )
            } finally {
                buffer.release()
            }
            helper.succeed()
        } finally {
            player.server.playerList.remove(player)
        }
    }
}
