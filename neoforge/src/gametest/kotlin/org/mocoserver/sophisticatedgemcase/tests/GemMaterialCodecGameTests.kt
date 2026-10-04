package org.mocoserver.sophisticatedgemcase.tests

import net.minecraft.core.component.DataComponents
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialCodec
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialContents
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialState
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsCodec
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsState

/** 上位加工用の材料と補充設定の保存codec境界を専用サーバーで検査する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemMaterialCodecGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun persistsSixCopiedStacksAndComponents(helper: GameTestHelper) {
        val stacks =
            (0 until GemMaterialContents.SLOT_COUNT).map { slot ->
                ItemStack(Items.DIAMOND, slot + 1).apply {
                    set(DataComponents.CUSTOM_NAME, Component.literal("材料 $slot"))
                }
            }
        val contents = GemMaterialContents.of(stacks)
        stacks[0].count = 8
        contents.stackAt(1).count = 12

        val encoded = GemMaterialCodec.write(contents, helper.level.registryAccess())
        val decoded = GemMaterialCodec.read(encoded, helper.level.registryAccess())
        helper.assertTrue(decoded is GemMaterialState.Ready, "Valid six-slot data was locked")
        val restored = (decoded as GemMaterialState.Ready).contents
        helper.assertTrue(restored.stackAt(0).count == 1, "Input stack mutation escaped into contents")
        helper.assertTrue(restored.stackAt(1).count == 2, "Returned stack mutation escaped from contents")
        helper.assertTrue(
            restored.stackAt(5).get(DataComponents.CUSTOM_NAME) == Component.literal("材料 5"),
            "ItemStack component was lost in persistent codec",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun defaultsMissingComponentsToEmptyAndDisabled(helper: GameTestHelper) {
        val materials = GemMaterialCodec.read(null, helper.level.registryAccess())
        helper.assertTrue(
            materials == GemMaterialState.Ready(GemMaterialContents.empty()),
            "Missing materials did not default to empty",
        )
        val settings = GemProcessingSettingsCodec.read(null)
        helper.assertTrue(
            settings == GemProcessingSettingsState.Ready(useBackpackMaterials = false),
            "Missing processing settings did not default to OFF",
        )
        helper.assertTrue(
            GemProcessingSettingsCodec.read(GemProcessingSettingsCodec.write(true)) ==
                GemProcessingSettingsState.Ready(useBackpackMaterials = true),
            "Processing setting round trip failed",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun classifiesNetworkSummariesSeparatelyFromPersistentData(helper: GameTestHelper) {
        val materialsSummary =
            CustomData.of(
                CompoundTag().apply {
                    putBoolean("client_summary", true)
                    putBoolean("has_contents", true)
                    putBoolean("locked", false)
                },
            )
        helper.assertTrue(
            GemMaterialCodec.read(materialsSummary, helper.level.registryAccess()) ==
                GemMaterialState.ClientSummary(hasContents = true, locked = false),
            "Network summary was treated as persistent material contents",
        )
        val settingsSummary =
            CustomData.of(
                CompoundTag().apply {
                    putBoolean("client_summary", true)
                    putBoolean("use_backpack_materials", true)
                    putBoolean("locked", false)
                },
            )
        helper.assertTrue(
            GemProcessingSettingsCodec.read(settingsSummary) ==
                GemProcessingSettingsState.ClientSummary(useBackpackMaterials = true, locked = false),
            "Network summary was treated as persistent processing settings",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun enforcesEverySlotStackLimit(helper: GameTestHelper) {
        val maximum = ItemStack(Items.DIAMOND).maxStackSize
        val valid = GemMaterialContents.of(List(GemMaterialContents.SLOT_COUNT) { ItemStack(Items.DIAMOND, maximum) })
        helper.assertTrue(
            GemMaterialCodec.canWrite(valid, helper.level.registryAccess()),
            "Maximum legal stack was rejected",
        )
        val tooMany =
            GemMaterialContents.of(
                List(GemMaterialContents.SLOT_COUNT) { ItemStack(Items.DIAMOND, maximum + 1) },
            )
        helper.assertTrue(
            !GemMaterialCodec.canWrite(tooMany, helper.level.registryAccess()),
            "Stack above item maxStackSize was accepted",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun locksMalformedSchemasSlotsItemsAndBudgetsWithoutChangingRaw(helper: GameTestHelper) {
        val registries = helper.level.registryAccess()
        val validRaw =
            GemMaterialCodec.write(
                GemMaterialContents.of(listOf(ItemStack(Items.DIAMOND)) + List(5) { ItemStack.EMPTY }),
                registries,
            )
        val cases =
            listOf(
                "unknown schema" to validRaw.copyTag().apply { putInt("schema_version", 2) },
                "unknown field" to validRaw.copyTag().apply { putBoolean("unexpected", true) },
                "duplicate slot" to rawWithEntries(validRaw.copyTag(), 0, 0),
                "out-of-range slot" to rawWithEntries(validRaw.copyTag(), GemMaterialContents.SLOT_COUNT),
                "wrong slot type" to rawWithWrongSlotType(validRaw.copyTag()),
                "wrong list element type" to rawWithWrongListType(validRaw.copyTag()),
                "unknown item" to rawWithUnknownItem(validRaw.copyTag()),
                "unknown stack field" to rawWithUnknownStackField(validRaw.copyTag()),
                "depth 17" to rawWithDeepComponent(validRaw.copyTag(), registries),
                "stack above 32 KiB" to rawWithLargeStack(validRaw.copyTag(), registries, 32 * 1024 + 1),
                "total above 1 MiB" to
                    CompoundTag().apply {
                        putInt("schema_version", 1)
                        put("entries", ListTag())
                        putString("padding", "x".repeat(1_048_577))
                    },
            )
        cases.forEach { (label, tag) ->
            val raw = CustomData.of(tag)
            val before = raw.copyTag()
            helper.assertTrue(
                GemMaterialCodec.read(raw, registries) is GemMaterialState.Locked,
                "$label data was not locked",
            )
            helper.assertTrue(raw.copyTag() == before, "$label read mutated original raw component")
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun locksUnknownSettingsSchemaAndPreservesRaw(helper: GameTestHelper) {
        val raw = CustomData.of(CompoundTag().apply { putInt("schema_version", 44) })
        val before = raw.copyTag()
        helper.assertTrue(
            GemProcessingSettingsCodec.read(raw) is GemProcessingSettingsState.Locked,
            "Unknown processing settings schema was accepted",
        )
        helper.assertTrue(raw.copyTag() == before, "Reading locked settings mutated original raw component")
        val invalidValues =
            listOf(
                CompoundTag().apply {
                    putInt("schema_version", 1)
                    putInt("use_backpack_materials", 1)
                },
                CompoundTag().apply {
                    putInt("schema_version", 1)
                    putByte("use_backpack_materials", 2)
                },
                CompoundTag().apply {
                    putInt("schema_version", 1)
                    putByte("unexpected", 0)
                },
            )
        invalidValues.forEach { tag ->
            helper.assertTrue(
                GemProcessingSettingsCodec.read(CustomData.of(tag)) is GemProcessingSettingsState.Locked,
                "Malformed processing settings were accepted",
            )
        }
        helper.succeed()
    }

    private fun rawWithEntries(
        source: CompoundTag,
        vararg slots: Int,
    ): CompoundTag =
        source.copy().apply {
            val stack = requireNotNull((getList("entries", 10).get(0) as? CompoundTag)?.getCompound("stack"))
            put(
                "entries",
                ListTag().apply {
                    slots.forEach { slot ->
                        add(
                            CompoundTag().apply {
                                putInt("slot", slot)
                                put("stack", stack.copy())
                            },
                        )
                    }
                },
            )
        }

    private fun rawWithWrongSlotType(source: CompoundTag): CompoundTag =
        source.copy().apply {
            val entries = getList("entries", 10).copy()
            entries.getCompound(0).putString("slot", "zero")
            put("entries", entries)
        }

    private fun rawWithWrongListType(source: CompoundTag): CompoundTag =
        source.copy().apply {
            put("entries", ListTag().apply { add(StringTag.valueOf("not an entry compound")) })
        }

    private fun rawWithUnknownItem(source: CompoundTag): CompoundTag =
        source.copy().apply {
            val entries = getList("entries", 10).copy()
            entries.getCompound(0).getCompound("stack").putString("id", "future:missing_item")
            put("entries", entries)
        }

    private fun rawWithUnknownStackField(source: CompoundTag): CompoundTag =
        source.copy().apply {
            val entries = getList("entries", 10).copy()
            entries.getCompound(0).getCompound("stack").putString("future_field", "unknown")
            put("entries", entries)
        }

    private fun rawWithDeepComponent(
        source: CompoundTag,
        registries: net.minecraft.core.HolderLookup.Provider,
    ): CompoundTag =
        source.copy().apply {
            val stack =
                ItemStack(Items.DIAMOND)
                    .apply { set(DataComponents.CUSTOM_DATA, CustomData.of(deepTag())) }
                    .save(registries) as CompoundTag
            val entries =
                ListTag().apply {
                    add(
                        CompoundTag().apply {
                            putInt("slot", 0)
                            put("stack", stack)
                        },
                    )
                }
            put("entries", entries)
        }

    private fun deepTag(): CompoundTag {
        var cursor = CompoundTag()
        val root = cursor
        repeat(17) {
            val next = CompoundTag()
            cursor.put("nested", next)
            cursor = next
        }
        return root
    }

    private fun rawWithLargeStack(
        source: CompoundTag,
        registries: net.minecraft.core.HolderLookup.Provider,
        bytes: Int,
    ): CompoundTag =
        source.copy().apply {
            val stack =
                ItemStack(Items.DIAMOND)
                    .apply {
                        set(
                            DataComponents.CUSTOM_DATA,
                            CustomData.of(CompoundTag().apply { putString("payload", "x".repeat(bytes)) }),
                        )
                    }.save(registries) as CompoundTag
            val entries =
                ListTag().apply {
                    add(
                        CompoundTag().apply {
                            putInt("slot", 0)
                            put("stack", stack)
                        },
                    )
                }
            put("entries", entries)
        }
}
