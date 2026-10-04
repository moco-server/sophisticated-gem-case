package org.mocoserver.sophisticatedgemcase.tests

import io.netty.buffer.Unpooled
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.integration.GemOperationContext
import org.mocoserver.sophisticatedgemcase.processing.GemProcessingCommit
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialCodec
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialContents
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialHandler
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialState
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsCodec
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** 加工設定の破損時にも、正常な材料の回収経路を失わない。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemMaterialStorageGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsMaterialSimulationDuringExclusiveProcessing(helper: GameTestHelper) {
        val bag = GemTestFixtures.backpack()
        bag.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
        val handler = GemMaterialHandler(bag, helper.level) { it.`is`(Items.DIAMOND) }
        handler.insertItem(0, ItemStack(Items.DIAMOND, 3), false)
        GemOperationContext.exclusive(bag) {
            helper.assertTrue(
                handler.insertItem(1, ItemStack(Items.DIAMOND), true).count == 1,
                "Simulation promised an insert rejected during exclusive processing",
            )
            helper.assertTrue(handler.extractItem(0, 1, true).isEmpty, "Simulation promised an exclusive extraction")
        }
        helper.assertTrue(handler.getStackInSlot(0).count == 3, "Exclusive rejection changed materials")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restoresSupplementSettingWhenItsSaveCallbackThrows(helper: GameTestHelper) {
        val bag = GemTestFixtures.backpack()
        bag.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
        val stack = bag.upgradeHandler.getStackInSlot(0)
        var callbacks = 0
        val wrapper =
            GemCaseUpgradeWrapper(bag, stack) {
                callbacks++
                if (callbacks == 1) throw IllegalStateException("setting save failure")
            }
        bag.upgradeHandler.slotWrappers[0] = wrapper
        var failed = false
        try {
            wrapper.replaceProcessingSettings(false, true)
        } catch (_: IllegalStateException) {
            failed = true
        }
        helper.assertTrue(failed, "Setting save failure was swallowed")
        helper.assertTrue(stack.get(ModDataComponents.gemProcessingSettings.get()) == null, "Failed setting persisted")
        helper.assertTrue(callbacks == 2, "Setting recovery callback missing")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun boundsBothNewComponentSummariesToTwoBytes(helper: GameTestHelper) {
        val components =
            listOf(
                ModDataComponents.gemProcessingMaterials.get() to
                    GemMaterialCodec.write(GemMaterialContents.empty(), helper.level.registryAccess()),
                ModDataComponents.gemProcessingSettings.get() to GemProcessingSettingsCodec.write(true),
            )
        for ((type, data) in components) {
            val buffer =
                RegistryFriendlyByteBuf(
                    Unpooled.buffer(),
                    helper.level.registryAccess(),
                    net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE,
                )
            try {
                val codec = type.streamCodec()
                codec.encode(buffer, data)
                helper.assertTrue(buffer.readableBytes() == 2, "Raw material or settings leaked through slot sync")
                val summary = codec.decode(buffer)
                helper.assertTrue(summary.copyTag().getBoolean("client_summary"), "Missing client summary marker")
                helper.assertTrue(!buffer.isReadable, "Unexpected summary bytes")
            } finally {
                buffer.release()
            }
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun sharesInstallationGroupAndRejectsSummaryWrites(helper: GameTestHelper) {
        val bag = GemTestFixtures.backpack()
        val upper = ModItems.advancedGemCaseUpgrade.get()
        bag.upgradeHandler.setStackInSlot(0, ItemStack(upper))
        val wrapper = bag.upgradeHandler.slotWrappers.getValue(0) as GemCaseUpgradeWrapper
        helper.assertTrue(wrapper.supportsProcessing, "Upper wrapper lacks processing")
        helper.assertTrue(
            !ModItems.gemCaseUpgrade
                .get()
                .canAddUpgradeTo(
                    bag,
                    ItemStack(ModItems.gemCaseUpgrade.get()),
                    true,
                    false,
                ).successful(),
            "Lower could coexist with upper",
        )
        helper.assertTrue(
            upper.upgradeGroup === ModItems.gemCaseUpgrade.get().upgradeGroup,
            "Upper and lower groups have separate identity",
        )
        val summary =
            CustomData.of(
                CompoundTag().apply {
                    putBoolean("client_summary", true)
                    putBoolean("has_contents", false)
                    putBoolean("locked", false)
                },
            )
        wrapper.upgradeStack.set(ModDataComponents.gemProcessingMaterials.get(), summary)
        helper.assertTrue(
            !wrapper.replaceMaterials(
                GemMaterialContents.empty(),
                GemMaterialContents.empty(),
                helper.level.registryAccess(),
            ),
            "Client summary became a material source",
        )
        helper.assertTrue(wrapper.hasContentsOrLocked(), "Server summary did not guard removal")
        helper.assertTrue(
            wrapper.upgradeStack.get(ModDataComponents.gemProcessingMaterials.get()) == summary,
            "Rejected summary write replaced raw",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restoresBothComponentsWhenProcessingSaveThrows(helper: GameTestHelper) {
        val bag = GemTestFixtures.backpack()
        bag.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
        val original = bag.upgradeHandler.getStackInSlot(0)
        var callbacks = 0
        val wrapper =
            GemCaseUpgradeWrapper(bag, original) {
                callbacks++
                if (callbacks == 1) throw IllegalStateException("save failure")
            }
        bag.upgradeHandler.slotWrappers[0] = wrapper
        val empty = GemCounts.of(emptyMap())
        val change =
            GemProcessingCommit(
                empty,
                GemMaterialContents.empty(),
                false,
                GemCounts.of(mapOf(GemKey("missing:retained", "normal") to 1)),
                GemMaterialContents.of(List(6) { if (it == 0) ItemStack(Items.DIAMOND) else ItemStack.EMPTY }),
            )
        var failed = false
        try {
            GemOperationContext.exclusive(bag) { wrapper.commitProcessing(change, helper.level.registryAccess(), it) }
        } catch (_: IllegalStateException) {
            failed = true
        }
        helper.assertTrue(failed && callbacks == 2, "Save error was swallowed or recovery missing")
        helper.assertTrue(original.get(ModDataComponents.gemStorage.get()) == null, "Counts component was not restored")
        helper.assertTrue(
            original.get(ModDataComponents.gemProcessingMaterials.get()) == null,
            "Materials component was not restored",
        )
        helper.assertTrue(!GemOperationContext.isExclusive(bag), "Failure leaked exclusive context")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun commitsMatchingCountsAndMaterialsOnceAndRejectsStaleState(helper: GameTestHelper) {
        val bag = GemTestFixtures.backpack()
        bag.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
        val wrapper = bag.upgradeHandler.slotWrappers.getValue(0) as GemCaseUpgradeWrapper
        val empty = GemCounts.of(emptyMap())
        val materials = GemMaterialContents.of(List(6) { if (it == 0) ItemStack(Items.DIAMOND) else ItemStack.EMPTY })
        val updated = GemCounts.of(mapOf(GemKey("missing:retained", "normal") to 1))
        val change = GemProcessingCommit(empty, GemMaterialContents.empty(), false, updated, materials)
        val first =
            GemOperationContext.exclusive(
                bag,
            ) { wrapper.commitProcessing(change, helper.level.registryAccess(), it) }
        helper.assertTrue(first == true, "Matching processing commit failed")
        val stale =
            GemOperationContext.exclusive(
                bag,
            ) { wrapper.commitProcessing(change, helper.level.registryAccess(), it) }
        helper.assertTrue(stale == false, "Stale processing commit succeeded")
        helper.assertTrue(
            (wrapper.materialsSnapshot(helper.level.registryAccess()) as GemMaterialState.Ready).contents == materials,
            "Stale commit changed materials",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun takesValidMaterialsOutDespiteLockedProcessingSettings(helper: GameTestHelper) {
        val bag = GemTestFixtures.backpack()
        bag.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.advancedGemCaseUpgrade.get()))
        val wrapper = bag.upgradeHandler.slotWrappers.getValue(0) as GemCaseUpgradeWrapper
        val handler = GemMaterialHandler(bag, helper.level) { it.`is`(Items.DIAMOND) }
        val offered = ItemStack(Items.DIAMOND, 70)
        helper.assertTrue(handler.insertItem(0, offered, true).count == 6, "Simulation has wrong limit")
        helper.assertTrue(handler.getStackInSlot(0).isEmpty, "Simulation changed material")
        helper.assertTrue(handler.insertItem(0, offered, false).count == 6, "Insert lost remainder")
        helper.assertTrue(offered.count == 70, "Caller stack changed")
        val future = CustomData.of(CompoundTag().apply { putInt("schema_version", 99) })
        wrapper.upgradeStack.set(ModDataComponents.gemProcessingSettings.get(), future)
        val extracted = handler.extractItem(0, 3, false)
        helper.assertTrue(extracted.count == 3 && extracted.`is`(Items.DIAMOND), "Locked settings blocked recovery")
        helper.assertTrue(handler.getStackInSlot(0).count == 61, "Material count changed incorrectly")
        helper.assertTrue(
            wrapper.upgradeStack.get(ModDataComponents.gemProcessingSettings.get()) == future,
            "Recovery replaced invalid settings",
        )
        helper.assertTrue(wrapper.hasContentsOrLocked(), "Material did not guard removal")
        handler.extractItem(0, 64, false)
        helper.assertTrue(!wrapper.hasContentsOrLocked(), "Settings alone guarded removal")
        helper.assertTrue(
            wrapper.materialsSnapshot(helper.level.registryAccess()) is GemMaterialState.Ready,
            "Material state became invalid",
        )
        helper.succeed()
    }
}
