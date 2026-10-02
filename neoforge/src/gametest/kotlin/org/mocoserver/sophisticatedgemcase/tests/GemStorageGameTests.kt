package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeType
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModBlocks
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper
import java.util.UUID
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems as BackpackItems

/** アドオンの保存と装着を実際のバッグ・Gemで検収する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemStorageGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun retainsUnknownEntriesAndRejectsCustomizedGems(helper: GameTestHelper) {
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val stack = GemItem.createStack(gem, Purity.CRACKED, 2)
        val key = requireNotNull(StandardGem.keyOf(stack))
        val unknown = GemKey("missing:retained", "future_purity")
        val counts = GemCounts.of(mapOf(key to 2, unknown to 37))
        val encoded = GemStorageCodec.write(counts)
        val restored = GemStorageCodec.read(encoded) as GemStorageState.Ready
        helper.assertTrue(restored.counts == counts, "Unknown entry was lost")
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Keep my data"))
        helper.assertTrue(StandardGem.keyOf(stack) == null, "Customized gem must stay in normal storage")
        helper.assertTrue(StandardGem.stackOf(unknown) == null, "Unknown gem must not be regenerated")
        val backpack = GemTestFixtures.backpack()
        val wrapper = GemTestFixtures.install(backpack)
        helper.assertTrue(wrapper.replace(GemCounts.of(emptyMap()), counts), "Storage write failed")
        val saved = backpack.upgradeHandler.getStackInSlot(0).save(helper.level.registryAccess())
        val parsed = ItemStack.parseOptional(helper.level.registryAccess(), saved as CompoundTag)
        val roundTrip = GemStorageCodec.read(parsed.get(ModDataComponents.gemStorage.get()))
        helper.assertTrue(roundTrip == restored, "Persistent ItemStack round trip lost entries")
        helper.assertTrue(!wrapper.replace(GemCounts.of(emptyMap()), counts), "Stale write must fail")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun locksMalformedStorageWithoutReplacingRawData(helper: GameTestHelper) {
        val future = CustomData.of(CompoundTag().apply { putInt("schema_version", 99) })
        helper.assertTrue(GemStorageCodec.read(future) is GemStorageState.Locked, "Future schema must lock")
        val backpack = GemTestFixtures.backpack()
        val wrapper = GemTestFixtures.install(backpack)
        wrapper.upgradeStack.set(ModDataComponents.gemStorage.get(), future)
        helper.assertTrue(!wrapper.replace(GemCounts.of(emptyMap()), GemCounts.of(emptyMap())), "Locked write allowed")
        helper.assertTrue(wrapper.upgradeStack.get(ModDataComponents.gemStorage.get()) == future, "Raw data changed")
        helper.assertTrue(wrapper.hasContentsOrLocked(), "Corrupt storage was treated as empty")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun locksDuplicateOversizeAndDeepPayloads(helper: GameTestHelper) {
        val entry =
            CompoundTag().apply {
                putString("gem_id", "missing:gem")
                putString("purity", "future")
                putInt("count", 1)
            }
        val duplicate =
            ListTag().apply {
                add(entry)
                add(entry.copy())
            }
        val raw =
            CompoundTag().apply {
                putInt("schema_version", 1)
                put("entries", duplicate)
            }
        helper.assertTrue(GemStorageCodec.read(CustomData.of(raw)) is GemStorageState.Locked, "Duplicate not locked")
        val many = ListTag().apply { repeat(4097) { add(entry.copy()) } }
        raw.put("entries", many)
        helper.assertTrue(GemStorageCodec.read(CustomData.of(raw)) is GemStorageState.Locked, "Entry budget ignored")
        val deep = CompoundTag()
        var cursor = deep
        repeat(9) { cursor = CompoundTag().also { child -> cursor.put("nested", child) } }
        helper.assertTrue(GemStorageCodec.read(CustomData.of(deep)) is GemStorageState.Locked, "Depth budget ignored")
        val huge = CustomData.of(CompoundTag().apply { putString("huge", "x".repeat(1_048_577)) })
        helper.assertTrue(GemStorageCodec.read(huge) is GemStorageState.Locked, "Byte budget ignored")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun sendsOnlySummaryAndRetainsStorageAcrossNativeTierAndPlacement(helper: GameTestHelper) {
        val source =
            BackpackWrapper(
                ItemStack(BackpackItems.GOLD_BACKPACK.get()),
            ).apply { setContentsUuid(UUID.randomUUID()) }
        val wrapper = GemTestFixtures.install(source)
        val counts = GemCounts.of(mapOf(GemKey("missing:keep", "future") to 37))
        helper.assertTrue(wrapper.replace(GemCounts.of(emptyMap()), counts), "Cannot prepare storage")
        val data = requireNotNull(wrapper.upgradeStack.get(ModDataComponents.gemStorage.get()))
        val buffer =
            RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                helper.level.registryAccess(),
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE,
            )
        try {
            val codec = ModDataComponents.gemStorage.get().streamCodec()
            codec.encode(buffer, data)
            helper.assertTrue(buffer.readableBytes() == 2, "Raw storage leaked through slot sync")
            helper.assertTrue(
                GemStorageCodec.read(codec.decode(buffer)) is GemStorageState.ClientSummary,
                "Missing summary",
            )
        } finally {
            buffer.release()
        }
        val input = MutableList(9) { ItemStack(Items.DIAMOND) }
        input[4] = source.backpack
        val grid = CraftingInput.of(3, 3, input)
        val recipe =
            helper.level.recipeManager
                .getRecipeFor(RecipeType.CRAFTING, grid, helper.level)
                .orElseThrow()
        val upgraded = recipe.value().assemble(grid, helper.level.registryAccess())
        helper.assertTrue(upgraded.`is`(BackpackItems.DIAMOND_BACKPACK.get()), "Native tier recipe failed")
        val tier = BackpackWrapper(upgraded)
        helper.assertTrue(
            (tier.upgradeHandler.slotWrappers.getValue(0) as GemCaseUpgradeWrapper).snapshot() ==
                GemStorageState.Ready(counts),
            "Tier lost storage",
        )
        val pos = BlockPos(1, 1, 1)
        helper.setBlock(pos, ModBlocks.BACKPACK.get())
        val tile = helper.getBlockEntity(pos) as BackpackBlockEntity
        tile.setBackpack(upgraded)
        helper.assertTrue(
            (
                tile.backpackWrapper.upgradeHandler.slotWrappers
                    .getValue(0) as GemCaseUpgradeWrapper
            ).snapshot() ==
                GemStorageState.Ready(counts),
            "Placement lost storage",
        )
        val recovered = tile.backpackWrapper.backpack.copy()
        helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR)
        helper.assertTrue(
            (BackpackWrapper(recovered).upgradeHandler.slotWrappers.getValue(0) as GemCaseUpgradeWrapper).snapshot() ==
                GemStorageState.Ready(counts),
            "Recovery lost storage",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsDuplicateInstallationAndNonemptySwap(helper: GameTestHelper) {
        val backpack = GemTestFixtures.backpack()
        val item = ModItems.gemCaseUpgrade.get()
        val wrapper = GemTestFixtures.install(backpack)
        helper.assertTrue(
            !item.canAddUpgradeTo(backpack, ItemStack(item), true, false).successful(),
            "Second upgrade allowed",
        )
        helper.assertTrue(item.canRemoveUpgradeFrom(backpack, false).successful(), "Empty removal blocked")
        val counts = GemCounts.of(mapOf(GemKey("missing:keep", "cracked") to 1))
        helper.assertTrue(wrapper.replace(GemCounts.of(emptyMap()), counts), "Cannot prepare stored gem")
        helper.assertTrue(!item.canRemoveUpgradeFrom(backpack, false).successful(), "Nonempty removal allowed")
        helper.assertTrue(
            !item.canSwapUpgradeFor(ItemStack(item), 0, backpack, false).successful(),
            "Same-item swap lost content",
        )
        helper.succeed()
    }
}

/** UUIDを持つ実バッグでwrapperを取得する共通fixture。 */
internal object GemTestFixtures {
    fun backpack(): BackpackWrapper =
        BackpackWrapper(ItemStack(BackpackItems.DIAMOND_BACKPACK.get())).apply {
            setContentsUuid(UUID.randomUUID())
        }

    fun install(backpack: BackpackWrapper): GemCaseUpgradeWrapper {
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.gemCaseUpgrade.get()))
        return backpack.upgradeHandler.slotWrappers.getValue(0) as GemCaseUpgradeWrapper
    }
}
