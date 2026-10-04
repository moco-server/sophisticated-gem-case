package org.mocoserver.sophisticatedgemcase.tests

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemFilter
import org.mocoserver.sophisticatedgemcase.core.GemFilterMode
import org.mocoserver.sophisticatedgemcase.core.GemTransferRule
import org.mocoserver.sophisticatedgemcase.core.GemTransferSettings
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsCodec
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsCodec
import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** summaryを厳密に検査し、転送設定の保存失敗でcomponent原文を保つ。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemAcquisitionStorageGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsMalformedCountSummariesWithoutReplacingRaw(helper: GameTestHelper) {
        val malformed = mutableListOf<CompoundTag>()
        val fields = listOf("client_summary", "has_contents", "locked")
        for (field in fields) {
            malformed += summary().apply { putInt(field, 1) }
            malformed += summary().apply { putString(field, "1") }
        }
        for (field in fields) {
            for (value in listOf(-1, 2)) {
                malformed += summary().apply { putByte(field, value.toByte()) }
            }
        }
        malformed += summary().apply { putByte("client_summary", 0) }
        for (field in fields) {
            malformed += summary().apply { remove(field) }
        }
        malformed += summary().apply { putBoolean("extra", true) }

        val backpack = GemTestFixtures.backpack()
        val wrapper = GemTestFixtures.install(backpack)
        val storageType = ModDataComponents.gemStorage.get()
        for ((index, tag) in malformed.withIndex()) {
            val raw = CustomData.of(tag)
            wrapper.upgradeStack.set(storageType, raw)
            helper.assertTrue(
                wrapper.snapshot() is GemStorageState.Locked,
                "Malformed summary $index was accepted",
            )
            helper.assertTrue(wrapper.upgradeStack.get(storageType) == raw, "Malformed summary $index was replaced")
        }

        for (hasContents in listOf(false, true)) {
            for (locked in listOf(false, true)) {
                val raw = CustomData.of(summary(hasContents, locked))
                wrapper.upgradeStack.set(storageType, raw)
                helper.assertTrue(
                    wrapper.snapshot() == GemStorageState.ClientSummary(hasContents, locked),
                    "Valid summary has_contents=$hasContents locked=$locked was decoded incorrectly",
                )
                helper.assertTrue(wrapper.upgradeStack.get(storageType) == raw, "Valid summary was replaced")
            }
        }

        val normal = GemStorageCodec.write(GemCounts.of(emptyMap()))
        wrapper.upgradeStack.set(storageType, normal)
        helper.assertTrue(
            wrapper.snapshot() == GemStorageState.Ready(GemCounts.of(emptyMap())),
            "Normal schema 1 counts regression",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restoresTransferSettingsRawWhenSaveFails(helper: GameTestHelper) {
        for (item in listOf(ModItems.gemCaseUpgrade.get(), ModItems.advancedGemCaseUpgrade.get())) {
            for (oldSettingsPresent in listOf(false, true)) {
                val primary = IllegalStateException("transfer settings save failed")
                val fixture = failureFixture(item, oldSettingsPresent, primary) { _, _ -> }
                val before = captureRaw(fixture.wrapper.upgradeStack)
                val failure = captureFailure { fixture.wrapper.replaceSettings(DEFAULT_SETTINGS, UPDATED_SETTINGS) }

                helper.assertTrue(failure === primary, "Primary save exception was replaced")
                helper.assertRawUnchanged(fixture.wrapper.upgradeStack, before)
                helper.assertTrue(fixture.calls() == 2, "Recovery save was not attempted")
            }
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun retainsPrimaryFailureWhenRecoverySaveFails(helper: GameTestHelper) {
        for (item in listOf(ModItems.gemCaseUpgrade.get(), ModItems.advancedGemCaseUpgrade.get())) {
            for (oldSettingsPresent in listOf(false, true)) {
                val primary = IllegalStateException("transfer settings save failed")
                val recovery = IllegalArgumentException("transfer settings recovery failed")
                val fixture =
                    failureFixture(item, oldSettingsPresent, primary) { call, _ ->
                        if (call == 2) throw recovery
                    }
                val before = captureRaw(fixture.wrapper.upgradeStack)
                val failure = captureFailure { fixture.wrapper.replaceSettings(DEFAULT_SETTINGS, UPDATED_SETTINGS) }

                helper.assertTrue(failure === primary, "Recovery failure replaced the primary exception")
                helper.assertTrue(primary.suppressed.singleOrNull() === recovery, "Recovery failure was not suppressed")
                helper.assertRawUnchanged(fixture.wrapper.upgradeStack, before)
            }
        }
        helper.succeed()
    }

    private fun summary(
        hasContents: Boolean = false,
        locked: Boolean = false,
    ): CompoundTag =
        CompoundTag().apply {
            putByte("client_summary", 1)
            putByte("has_contents", if (hasContents) 1 else 0)
            putByte("locked", if (locked) 1 else 0)
        }

    private fun failureFixture(
        item: net.minecraft.world.item.Item,
        oldSettingsPresent: Boolean,
        primary: RuntimeException,
        extraFailure: (Int, RuntimeException) -> Unit,
    ): FailureFixture {
        val backpack = GemTestFixtures.backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(item))
        val stack = backpack.upgradeHandler.getStackInSlot(0)
        stack.set(ModDataComponents.gemStorage.get(), GemStorageCodec.write(GemCounts.of(emptyMap())))
        stack.set(
            ModDataComponents.gemProcessingMaterials.get(),
            CustomData.of(
                CompoundTag().apply {
                    putString("kept", "materials")
                },
            ),
        )
        stack.set(ModDataComponents.gemProcessingSettings.get(), GemProcessingSettingsCodec.write(true))
        if (oldSettingsPresent) {
            stack.set(
                ModDataComponents.gemTransferSettings.get(),
                GemSettingsCodec.write(DEFAULT_SETTINGS),
            )
        }

        var calls = 0
        val wrapper =
            GemCaseUpgradeWrapper(backpack, stack) {
                calls += 1
                if (calls == 1) throw primary
                extraFailure(calls, primary)
            }
        backpack.upgradeHandler.slotWrappers[0] = wrapper
        return FailureFixture(wrapper) { calls }
    }

    private fun captureRaw(stack: ItemStack): RawComponents =
        RawComponents(
            stack.get(ModDataComponents.gemTransferSettings.get()),
            stack.get(ModDataComponents.gemStorage.get()),
            stack.get(ModDataComponents.gemProcessingMaterials.get()),
            stack.get(ModDataComponents.gemProcessingSettings.get()),
        )

    private fun GameTestHelper.assertRawUnchanged(
        stack: ItemStack,
        expected: RawComponents,
    ) {
        val actual = captureRaw(stack)
        assertTrue(actual.transferSettings == expected.transferSettings, "Transfer settings raw/presence changed")
        assertTrue(actual.counts == expected.counts, "Gem counts changed")
        assertTrue(actual.materials == expected.materials, "Processing materials changed")
        assertTrue(actual.processingSettings == expected.processingSettings, "Processing settings changed")
    }

    private fun captureFailure(block: () -> Boolean): RuntimeException? =
        try {
            block()
            null
        } catch (failure: RuntimeException) {
            failure
        }

    private data class FailureFixture(
        val wrapper: GemCaseUpgradeWrapper,
        val calls: () -> Int,
    )

    private data class RawComponents(
        val transferSettings: CustomData?,
        val counts: CustomData?,
        val materials: CustomData?,
        val processingSettings: CustomData?,
    )

    private val DEFAULT_SETTINGS = GemTransferSettings.DEFAULT
    private val UPDATED_SETTINGS =
        DEFAULT_SETTINGS.copy(
            deposit = GemTransferRule(true, GemFilter.of(GemFilterMode.ALL, emptySet(), emptySet())),
        )
}
