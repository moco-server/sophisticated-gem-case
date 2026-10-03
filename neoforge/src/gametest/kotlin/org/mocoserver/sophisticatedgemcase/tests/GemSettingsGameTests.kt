package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import io.netty.buffer.Unpooled
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.RegistryFriendlyByteBuf
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
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsCodec
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsState
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.GemTransfers
import org.mocoserver.sophisticatedgemcase.storage.StandardGem

/** 設定schema・raw保持・booleanのみの通常slot同期を実ゲーム環境で検査する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemSettingsGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun absentDefaultsRoundTripsAndPreservesUnknownSelections(helper: GameTestHelper) {
        helper.assertTrue(
            GemSettingsCodec.read(null) == GemSettingsState.Ready(GemTransferSettings.DEFAULT),
            "Missing component is not default",
        )
        val settings =
            GemTransferSettings(
                GemTransferRule(true, GemFilter.of(GemFilterMode.INCLUDE, setOf("future:gem"), setOf("ancient"))),
                GemTransferRule(false, GemFilter.of(GemFilterMode.EXCLUDE, emptySet(), setOf("flawed"))),
            )
        helper.assertTrue(
            GemSettingsCodec.read(GemSettingsCodec.write(settings)) == GemSettingsState.Ready(settings),
            "Settings round trip failed",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun locksMalformedSettingsAndKeepsRawWhileCountsRemainWritable(helper: GameTestHelper) {
        val raw = CustomData.of(CompoundTag().apply { putInt("schema_version", 87) })
        helper.assertTrue(GemSettingsCodec.read(raw) is GemSettingsState.Locked, "Unknown schema was accepted")
        val backpack = GemTestFixtures.backpack()
        val wrapper = GemTestFixtures.install(backpack)
        wrapper.upgradeStack.set(ModDataComponents.gemTransferSettings.get(), raw)
        helper.assertTrue(
            !wrapper.replaceSettings(GemTransferSettings.DEFAULT, GemTransferSettings.DEFAULT),
            "Locked settings were overwritten",
        )
        helper.assertTrue(
            wrapper.upgradeStack.get(ModDataComponents.gemTransferSettings.get()) == raw,
            "Raw settings changed",
        )
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        val stack = GemItem.createStack(gem, Purity.CRACKED, 2)
        val key = requireNotNull(StandardGem.keyOf(stack))
        val remainder = GemTransfers.insertDedicated(wrapper, stack, stack.count, false)
        helper.assertTrue(remainder.isEmpty, "Locked settings blocked manual Gem insertion")
        helper.assertTrue(
            wrapper.snapshot() == GemStorageState.Ready(GemCounts.of(mapOf(key to 2))),
            "Manual counts were not saved",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun keepsSettingsThroughItemStackPersistenceAndEmptyRemoval(helper: GameTestHelper) {
        val backpack = GemTestFixtures.backpack()
        val wrapper = GemTestFixtures.install(backpack)
        val settings =
            GemTransferSettings.DEFAULT.copy(
                deposit = GemTransferRule(true, GemFilter.of(GemFilterMode.ALL, emptySet(), emptySet())),
            )
        helper.assertTrue(wrapper.replaceSettings(GemTransferSettings.DEFAULT, settings), "Settings write failed")
        val saved = wrapper.upgradeStack.save(helper.level.registryAccess())
        val restored = ItemStack.parseOptional(helper.level.registryAccess(), saved as CompoundTag)
        helper.assertTrue(
            restored.get(ModDataComponents.gemTransferSettings.get()) == GemSettingsCodec.write(settings),
            "ItemStack persistence lost settings",
        )
        helper.assertTrue(
            ModItems.gemCaseUpgrade
                .get()
                .canRemoveUpgradeFrom(backpack, false)
                .successful(),
            "Empty settings-only upgrade could not be removed",
        )
        val removedStack = wrapper.upgradeStack.copy()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack.EMPTY)
        helper.assertTrue(backpack.upgradeHandler.getStackInSlot(0).isEmpty, "Upgrade slot was not emptied")
        helper.assertTrue(
            removedStack.get(ModDataComponents.gemTransferSettings.get()) == GemSettingsCodec.write(settings),
            "Removal stack lost settings",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun rejectsUnknownFieldsDuplicatesAndBudgets(helper: GameTestHelper) {
        val base = ruleTag()
        val root =
            CompoundTag().apply {
                putInt("schema_version", 1)
                put("deposit", base.copy())
                put("restock", base.copy())
            }
        helper.assertTrue(GemSettingsCodec.read(CustomData.of(root)) is GemSettingsState.Ready, "Valid schema rejected")
        val extra = root.copy().apply { putBoolean("extra", true) }
        helper.assertTrue(
            GemSettingsCodec.read(CustomData.of(extra)) is GemSettingsState.Locked,
            "Extra field accepted",
        )
        val malformedMode = root.copy().apply { put("deposit", base.copy().apply { putInt("mode", 1) }) }
        helper.assertTrue(
            GemSettingsCodec.read(CustomData.of(malformedMode)) is GemSettingsState.Locked,
            "Wrong mode type accepted",
        )
        val malformedList =
            root.copy().apply {
                put(
                    "deposit",
                    base.copy().apply {
                        put(
                            "gem_ids",
                            ListTag().apply {
                                add(
                                    net.minecraft.nbt.IntTag
                                        .valueOf(1),
                                )
                            },
                        )
                    },
                )
            }
        helper.assertTrue(
            GemSettingsCodec.read(CustomData.of(malformedList)) is GemSettingsState.Locked,
            "Wrong list element type accepted",
        )
        val malformedEnabled = root.copy().apply { put("deposit", base.copy().apply { putInt("enabled", 1) }) }
        helper.assertTrue(
            GemSettingsCodec.read(CustomData.of(malformedEnabled)) is GemSettingsState.Locked,
            "Wrong enabled type accepted",
        )
        val duplicateRule =
            base.copy().apply {
                put(
                    "gem_ids",
                    ListTag().apply {
                        add(
                            net.minecraft.nbt.StringTag
                                .valueOf("future:gem"),
                        )
                        add(
                            net.minecraft.nbt.StringTag
                                .valueOf("future:gem"),
                        )
                    },
                )
            }
        val duplicate =
            CompoundTag().apply {
                putInt("schema_version", 1)
                put("deposit", duplicateRule)
                put("restock", base.copy())
            }
        helper.assertTrue(
            GemSettingsCodec.read(CustomData.of(duplicate)) is GemSettingsState.Locked,
            "Duplicate accepted",
        )
        val many =
            base.copy().apply {
                put(
                    "gem_ids",
                    ListTag().apply {
                        repeat(4097) {
                            add(
                                net.minecraft.nbt.StringTag
                                    .valueOf("future:$it"),
                            )
                        }
                    },
                )
            }
        helper.assertTrue(
            settings(many, base).let(GemSettingsCodec::read) is GemSettingsState.Locked,
            "Entry budget ignored",
        )
        val tooLong =
            base.copy().apply {
                put(
                    "purities",
                    ListTag().apply {
                        add(
                            net.minecraft.nbt.StringTag
                                .valueOf("a".repeat(257)),
                        )
                    },
                )
            }
        helper.assertTrue(
            GemSettingsCodec.read(settings(tooLong, base)) is GemSettingsState.Locked,
            "Text limit ignored",
        )
        val deep = CompoundTag()
        var cursor = deep
        repeat(9) {
            val child = CompoundTag()
            cursor.put("nested", child)
            cursor = child
        }
        helper.assertTrue(GemSettingsCodec.read(CustomData.of(deep)) is GemSettingsState.Locked, "Depth budget ignored")
        helper.assertTrue(
            GemSettingsCodec.read(
                CustomData.of(
                    CompoundTag().apply {
                        putString("huge", "x".repeat(1_048_577))
                    },
                ),
            ) is GemSettingsState.Locked,
            "Byte budget ignored",
        )
        val malformedSummary =
            CustomData.of(
                CompoundTag().apply {
                    putBoolean("client_summary", true)
                    putInt("locked", 1)
                },
            )
        helper.assertTrue(
            GemSettingsCodec.read(malformedSummary) is GemSettingsState.Locked,
            "Malformed client summary accepted",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun sendsOneByteAndDoesNotAcceptClientSummaryAsSettings(helper: GameTestHelper) {
        val holder = ModDataComponents.gemTransferSettings.get()
        val raw = GemSettingsCodec.write(GemTransferSettings.DEFAULT)
        val buffer =
            RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                helper.level.registryAccess(),
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE,
            )
        try {
            holder.streamCodec().encode(buffer, raw)
            helper.assertTrue(buffer.readableBytes() == 1, "Settings wire summary was not exactly one byte")
            val summary = holder.streamCodec().decode(buffer)
            helper.assertTrue(
                GemSettingsCodec.read(summary) is GemSettingsState.ClientSummary,
                "Client summary not recognized",
            )
            val backpack = GemTestFixtures.backpack()
            val wrapper = GemTestFixtures.install(backpack)
            wrapper.upgradeStack.set(holder, summary)
            helper.assertTrue(
                !wrapper.replaceSettings(GemTransferSettings.DEFAULT, GemTransferSettings.DEFAULT),
                "Client summary became writable",
            )
        } finally {
            buffer.release()
        }
        helper.succeed()
    }

    private fun ruleTag(): CompoundTag =
        CompoundTag().apply {
            putByte("enabled", 0)
            putString("mode", "all")
            put("gem_ids", ListTag())
            put("purities", ListTag())
        }

    private fun settings(
        deposit: CompoundTag,
        restock: CompoundTag,
    ): CustomData =
        CustomData.of(
            CompoundTag().apply {
                putInt("schema_version", 1)
                put("deposit", deposit)
                put("restock", restock)
            },
        )
}
