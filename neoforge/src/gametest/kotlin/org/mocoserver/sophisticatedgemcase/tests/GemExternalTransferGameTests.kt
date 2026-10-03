package org.mocoserver.sophisticatedgemcase.tests

import dev.shadowsoffire.apotheosis.socket.gem.GemItem
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry
import dev.shadowsoffire.apotheosis.socket.gem.Purity
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.neoforged.neoforge.items.ItemStackHandler
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper
import net.p3pp3rf1y.sophisticatedbackpacks.upgrades.deposit.DepositUpgradeWrapper
import net.p3pp3rf1y.sophisticatedbackpacks.upgrades.restock.RestockUpgradeWrapper
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemFilter
import org.mocoserver.sophisticatedgemcase.core.GemFilterMode
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.core.GemTransferDirection
import org.mocoserver.sophisticatedgemcase.core.GemTransferRule
import org.mocoserver.sophisticatedgemcase.core.GemTransferSettings
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsState
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.storage.StandardGem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper
import java.util.UUID
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems as BackpackItems

/** 固定版native転送メソッドから専用収納への独立経路を検収する。 */
@GameTestHolder(ModTemplate.ID)
@PrefixGameTestTemplate(false)
object GemExternalTransferGameTests {
    private data class DepositFixture(
        val backpack: BackpackWrapper,
        val deposit: DepositUpgradeWrapper,
        val case: GemCaseUpgradeWrapper,
    )

    private data class RestockFixture(
        val backpack: BackpackWrapper,
        val restock: RestockUpgradeWrapper,
        val case: GemCaseUpgradeWrapper,
    )

    @JvmStatic
    @GameTest(template = "empty")
    fun enabledDepositTransfersDedicatedGemAndReportsTheMove(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.DEPOSIT_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val deposit = backpack.upgradeHandler.slotWrappers.getValue(0) as DepositUpgradeWrapper
        deposit.filterLogic.setAllowList(false)
        val case = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val gem = standardGem(2)
        val key = requireNotNull(StandardGem.keyOf(gem))
        setDedicatedCount(case, key, 2)
        setSettings(case, GemTransferDirection.DEPOSIT)
        val target = ItemStackHandler(1)

        val moved = deposit.depositToHandler(target)

        helper.assertTrue(
            target.getStackInSlot(0).count == 2,
            "Enabled Deposit did not move dedicated Gems into the external handler",
        )
        helper.assertTrue(
            (case.snapshot() as GemStorageState.Ready).counts.entries().isEmpty(),
            "Deposit left transferred Gems in dedicated storage",
        )
        helper.assertTrue(
            moved.sumOf { it.count } == 2,
            "Deposit result list did not report the actual dedicated transfer",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun enabledRestockTransfersGemWhenNativeFilterRejectsIt(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.RESTOCK_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val restock = backpack.upgradeHandler.slotWrappers.getValue(0) as RestockUpgradeWrapper
        restock.filterLogic.setAllowList(true)
        restock.filterLogic.filterHandler.setStackInSlot(0, ItemStack(Items.IRON_INGOT))
        val case = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val gem = standardGem(2)
        val key = requireNotNull(StandardGem.keyOf(gem))
        setSettings(case, GemTransferDirection.RESTOCK)
        fillInventory(backpack)
        val source = ItemStackHandler(1).apply { setStackInSlot(0, gem) }

        val moved = restock.restockFromHandler(source)

        helper.assertTrue(
            (case.snapshot() as GemStorageState.Ready).counts.entries()[key] == 2,
            "Enabled Gem Restock did not store source Gems",
        )
        helper.assertTrue(source.getStackInSlot(0).isEmpty, "Restock left transferred Gems in the external source")
        helper.assertTrue(
            moved.sumOf { it.count } == 2,
            "Restock result list did not report the actual dedicated transfer",
        )
        helper.assertTrue(
            backpack.inventoryHandler.getStackInSlot(0).`is`(Items.STONE),
            "Dedicated Restock changed the full normal inventory",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun depositOffAndDisabledNativeUpgradePreserveDedicatedCounts(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.DEPOSIT_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val deposit = backpack.upgradeHandler.slotWrappers.getValue(0) as DepositUpgradeWrapper
        deposit.filterLogic.setAllowList(false)
        val case = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val key = requireNotNull(StandardGem.keyOf(standardGem(1)))
        setDedicatedCount(case, key, 2)
        val target = ItemStackHandler(1)

        val offResult = deposit.depositToHandler(target)
        helper.assertTrue(offResult.isEmpty() && target.getStackInSlot(0).isEmpty, "Deposit OFF moved dedicated Gems")
        helper.assertTrue(dedicatedCount(case, key) == 2, "Deposit OFF changed dedicated counts")

        setSettings(case, GemTransferDirection.DEPOSIT)
        deposit.setEnabled(false)
        val disabledResult = deposit.depositToHandler(target)
        helper.assertTrue(
            disabledResult.isEmpty() && target.getStackInSlot(0).isEmpty,
            "Disabled native Deposit ran dedicated transfer",
        )
        helper.assertTrue(dedicatedCount(case, key) == 2, "Disabled native Deposit changed dedicated counts")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun depositHonorsGemFilterAndDoesNotReapplyNativeFilter(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.DEPOSIT_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val deposit = backpack.upgradeHandler.slotWrappers.getValue(0) as DepositUpgradeWrapper
        val case = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val gem = standardGem(2)
        val key = requireNotNull(StandardGem.keyOf(gem))
        setDedicatedCount(case, key, 2)
        val rejectedFilter = GemFilter.of(GemFilterMode.INCLUDE, setOf("apotheosis:unmatched_gem"), emptySet())
        setSettings(case, GemTransferDirection.DEPOSIT, rejectedFilter)
        deposit.filterLogic.setAllowList(false)
        val target = ItemStackHandler(1)

        val rejected = deposit.depositToHandler(target)
        helper.assertTrue(rejected.isEmpty() && target.getStackInSlot(0).isEmpty, "Gem filter exclusion was ignored")
        helper.assertTrue(dedicatedCount(case, key) == 2, "Gem filter exclusion changed dedicated counts")

        val acceptedFilter = GemFilter.of(GemFilterMode.ALL, emptySet(), emptySet())
        replaceSettings(case, GemTransferDirection.DEPOSIT, acceptedFilter)
        deposit.filterLogic.setAllowList(true)
        deposit.filterLogic.filterHandler.setStackInSlot(0, ItemStack(Items.IRON_INGOT))
        val accepted = deposit.depositToHandler(target)
        helper.assertTrue(
            target.getStackInSlot(0).count == 2,
            "Native filter was incorrectly applied to dedicated Deposit",
        )
        helper.assertTrue(
            accepted.sumOf { it.count } == 2,
            "Dedicated Deposit notification did not match external movement",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun partialAndFullExternalDepositRestoreEveryUnacceptedGem(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.DEPOSIT_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val deposit = backpack.upgradeHandler.slotWrappers.getValue(0) as DepositUpgradeWrapper
        deposit.filterLogic.setAllowList(false)
        val case = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val gem = standardGem(3)
        val key = requireNotNull(StandardGem.keyOf(gem))
        setDedicatedCount(case, key, 3)
        setSettings(case, GemTransferDirection.DEPOSIT)
        val oneItemTarget =
            object : ItemStackHandler(1) {
                override fun getSlotLimit(slot: Int): Int = 1
            }

        val partial = deposit.depositToHandler(oneItemTarget)
        helper.assertTrue(oneItemTarget.getStackInSlot(0).count == 1, "Limited target did not accept one Gem")
        helper.assertTrue(dedicatedCount(case, key) == 2, "Deposit discarded the unaccepted reservation")
        helper.assertTrue(partial.sumOf { it.count } == 1, "Partial Deposit reported more than the real acceptance")

        val full = deposit.depositToHandler(oneItemTarget)
        helper.assertTrue(
            full.isEmpty() && dedicatedCount(case, key) == 2,
            "Full target changed the remaining dedicated counts",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun depositCombinesNativeAndDedicatedMovementOfTheSameGem(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.DEPOSIT_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val deposit = backpack.upgradeHandler.slotWrappers.getValue(0) as DepositUpgradeWrapper
        deposit.filterLogic.setAllowList(false)
        val case = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val gem = standardGem(2)
        val key = requireNotNull(StandardGem.keyOf(gem))
        setDedicatedCount(case, key, 2)
        backpack.inventoryHandler.setStackInSlot(0, gem.copyWithCount(1))
        setSettings(case, GemTransferDirection.DEPOSIT)
        val target = ItemStackHandler(1)

        val moved = deposit.depositToHandler(target)

        helper.assertTrue(
            target.getStackInSlot(0).count == 3,
            "Normal and dedicated Deposit did not both reach the target",
        )
        helper.assertTrue(
            backpack.inventoryHandler.getStackInSlot(0).isEmpty,
            "Native Deposit left the normal Gem behind",
        )
        helper.assertTrue(dedicatedCount(case, key) == 0, "Dedicated Deposit left the Gem behind")
        helper.assertTrue(moved.sumOf { it.count } == 3, "Transfer list does not match total real movement")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restockOffKeepsNativeTransferInNormalInventory(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.RESTOCK_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val restock = backpack.upgradeHandler.slotWrappers.getValue(0) as RestockUpgradeWrapper
        restock.filterLogic.setAllowList(false)
        val case = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val gem = standardGem(2)
        val key = requireNotNull(StandardGem.keyOf(gem))
        val source = ItemStackHandler(1).apply { setStackInSlot(0, gem) }

        val moved = restock.restockFromHandler(source)

        helper.assertTrue(source.getStackInSlot(0).isEmpty, "Native Restock OFF should retain its normal behavior")
        helper.assertTrue(
            backpack.inventoryHandler.getStackInSlot(0).count == 2,
            "Native Restock OFF did not use normal inventory",
        )
        helper.assertTrue(dedicatedCount(case, key) == 0, "Restock OFF routed into dedicated storage")
        helper.assertTrue(moved.sumOf { it.count } == 2, "Native Restock result list changed while OFF")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun disabledNativeRestockAndGemFilterExclusionLeaveSourceUnchanged(helper: GameTestHelper) {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.RESTOCK_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val restock = backpack.upgradeHandler.slotWrappers.getValue(0) as RestockUpgradeWrapper
        restock.filterLogic.setAllowList(true)
        restock.filterLogic.filterHandler.setStackInSlot(0, ItemStack(Items.IRON_INGOT))
        val case = requireNotNull(GemCaseUpgradeItem.find(backpack))
        val gem = standardGem(2)
        val key = requireNotNull(StandardGem.keyOf(gem))
        fillInventory(backpack)
        setSettings(case, GemTransferDirection.RESTOCK)
        val source = ItemStackHandler(1).apply { setStackInSlot(0, gem.copy()) }
        restock.setEnabled(false)
        val disabled = restock.restockFromHandler(source)
        helper.assertTrue(
            disabled.isEmpty() && source.getStackInSlot(0).count == 2,
            "Disabled native Restock moved a dedicated Gem",
        )
        helper.assertTrue(dedicatedCount(case, key) == 0, "Disabled native Restock changed dedicated storage")

        restock.setEnabled(true)
        setSettings(
            case,
            GemTransferDirection.RESTOCK,
            GemFilter.of(GemFilterMode.INCLUDE, setOf("apotheosis:unmatched_gem"), emptySet()),
        )
        val excluded = restock.restockFromHandler(source)
        helper.assertTrue(
            excluded.isEmpty() && source.getStackInSlot(0).count == 2,
            "Gem Restock filter exclusion was ignored",
        )
        helper.assertTrue(dedicatedCount(case, key) == 0, "Gem Restock filter exclusion changed dedicated storage")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restockUsesActualExtractCountAndRejectsExtractionFailure(helper: GameTestHelper) {
        val partialFixture = restockFixture()
        val partialKey = requireNotNull(StandardGem.keyOf(standardGem(1)))
        val partialSource =
            object : ItemStackHandler(1) {
                override fun extractItem(
                    slot: Int,
                    amount: Int,
                    simulate: Boolean,
                ): ItemStack = super.extractItem(slot, if (simulate) amount else minOf(1, amount), simulate)
            }.apply { setStackInSlot(0, standardGem(2)) }
        val partial = partialFixture.restock.restockFromHandler(partialSource)
        helper.assertTrue(
            dedicatedCount(partialFixture.case, partialKey) == 1,
            "Restock counted simulated extraction instead of actual extraction",
        )
        helper.assertTrue(
            partialSource.getStackInSlot(0).count == 1,
            "Partial extraction consumed more than the handler returned",
        )
        helper.assertTrue(
            partial.sumOf { it.count } == 1,
            "Partial Restock reported simulated rather than actual quantity",
        )

        val rejectedFixture = restockFixture()
        val rejectedKey = requireNotNull(StandardGem.keyOf(standardGem(1)))
        val rejectedSource =
            object : ItemStackHandler(1) {
                override fun extractItem(
                    slot: Int,
                    amount: Int,
                    simulate: Boolean,
                ): ItemStack = if (simulate) super.extractItem(slot, amount, true) else ItemStack.EMPTY
            }.apply { setStackInSlot(0, standardGem(2)) }
        val rejected = rejectedFixture.restock.restockFromHandler(rejectedSource)
        helper.assertTrue(
            rejected.isEmpty() && dedicatedCount(rejectedFixture.case, rejectedKey) == 0,
            "Rejected actual extraction changed dedicated counts",
        )
        helper.assertTrue(rejectedSource.getStackInSlot(0).count == 2, "Rejected actual extraction changed the source")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restockSkipsComponentGemAndKeepsUnknownDedicatedEntries(helper: GameTestHelper) {
        val fixture = restockFixture()
        val unknown = GemKey("future:unknown_gem", "mystery")
        setDedicatedCount(fixture.case, unknown, 7)
        val key = requireNotNull(StandardGem.keyOf(standardGem(1)))
        val source =
            ItemStackHandler(2).apply {
                setStackInSlot(
                    0,
                    standardGem(2).apply {
                        set(
                            net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            net.minecraft.network.chat.Component
                                .literal("marked"),
                        )
                    },
                )
                setStackInSlot(1, standardGem(1))
            }

        val moved = fixture.restock.restockFromHandler(source)

        helper.assertTrue(source.getStackInSlot(0).count == 2, "Extra-component Gem was extracted as a standard Gem")
        helper.assertTrue(source.getStackInSlot(1).isEmpty, "Known standard Gem was not extracted")
        val counts = (fixture.case.snapshot() as GemStorageState.Ready).counts
        helper.assertTrue(
            counts.count(unknown) == 7 && counts.count(key) == 1,
            "Restock dropped an unknown stored entry",
        )
        helper.assertTrue(moved.sumOf { it.count } == 1, "Restock result list counted a component-bearing source Gem")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun reentrantCallbacksCannotMutateSameBagButCanUseAnotherBag(helper: GameTestHelper) {
        val fixture = depositFixture()
        val key = requireNotNull(StandardGem.keyOf(standardGem(1)))
        setDedicatedCount(fixture.case, key, 1)
        setSettings(fixture.case, GemTransferDirection.DEPOSIT)
        val otherBackpack = backpack()
        otherBackpack.upgradeHandler.setStackInSlot(0, ItemStack(ModItems.gemCaseUpgrade.get()))
        val otherCase = requireNotNull(GemCaseUpgradeItem.find(otherBackpack))
        val reentrantGem = standardGem(1)
        var ioRemainder = ItemStack.EMPTY
        var countReplaceSucceeded = true
        var settingsReplaceSucceeded = true
        var removalSucceeded = true
        var otherRemainder = ItemStack.EMPTY
        val target =
            object : ItemStackHandler(1) {
                override fun insertItem(
                    slot: Int,
                    stack: ItemStack,
                    simulate: Boolean,
                ): ItemStack {
                    if (!simulate) {
                        ioRemainder = fixture.backpack.inventoryForInputOutput.insertItem(reentrantGem.copy(), false)
                        val current = (fixture.case.snapshot() as GemStorageState.Ready).counts
                        countReplaceSucceeded = fixture.case.replace(current, current)
                        val settings = (fixture.case.settingsSnapshot() as GemSettingsState.Ready).settings
                        settingsReplaceSucceeded = fixture.case.replaceSettings(settings, settings)
                        removalSucceeded =
                            ModItems.gemCaseUpgrade
                                .get()
                                .canRemoveUpgradeFrom(fixture.backpack, false)
                                .successful()
                        otherRemainder = otherBackpack.inventoryForInputOutput.insertItem(standardGem(1), false)
                    }
                    return super.insertItem(slot, stack, simulate)
                }
            }

        fixture.deposit.depositToHandler(target)

        helper.assertTrue(ioRemainder.count == 1, "Same-bag callback IO did not return the whole input")
        helper.assertTrue(
            !countReplaceSucceeded && !settingsReplaceSucceeded,
            "Same-bag callback used a regular save path",
        )
        helper.assertTrue(!removalSucceeded, "Gem Case removal was allowed during an empty reservation")
        helper.assertTrue(otherRemainder.isEmpty, "A different bag was blocked by the active transfer")
        helper.assertTrue(dedicatedCount(otherCase, key) == 1, "The other bag did not accept its valid Gem")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun transferCallbackExceptionRestoresKnownReservationAndReleasesContext(helper: GameTestHelper) {
        val fixture = depositFixture()
        val key = requireNotNull(StandardGem.keyOf(standardGem(1)))
        setDedicatedCount(fixture.case, key, 2)
        setSettings(fixture.case, GemTransferDirection.DEPOSIT)
        var shouldThrow = true
        val target =
            object : ItemStackHandler(1) {
                override fun insertItem(
                    slot: Int,
                    stack: ItemStack,
                    simulate: Boolean,
                ): ItemStack {
                    if (!simulate && shouldThrow) {
                        shouldThrow = false
                        throw IllegalStateException("test target callback failure")
                    }
                    return super.insertItem(slot, stack, simulate)
                }
            }
        var propagated = false
        try {
            fixture.deposit.depositToHandler(target)
        } catch (_: IllegalStateException) {
            propagated = true
        }
        helper.assertTrue(propagated, "External callback exception was swallowed")
        helper.assertTrue(dedicatedCount(fixture.case, key) == 2, "Known unaccepted reservation was not restored")
        helper.assertTrue(
            !org.mocoserver.sophisticatedgemcase.integration.GemOperationContext
                .isExclusive(fixture.backpack),
            "Callback exception leaked the exclusive context",
        )
        val next = fixture.deposit.depositToHandler(target)
        helper.assertTrue(
            target.getStackInSlot(0).count == 2 &&
                next.sumOf {
                    it.count
                } == 2,
            "A later transfer did not recover after the exception",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun depositRestoresOnlyUnacceptedRemainderWhenLaterSlotThrows(helper: GameTestHelper) {
        val fixture = depositFixture()
        val key = requireNotNull(StandardGem.keyOf(standardGem(1)))
        setDedicatedCount(fixture.case, key, 3)
        setSettings(fixture.case, GemTransferDirection.DEPOSIT)
        val target =
            object : ItemStackHandler(2) {
                override fun getSlotLimit(slot: Int): Int = 1

                override fun insertItem(
                    slot: Int,
                    stack: ItemStack,
                    simulate: Boolean,
                ): ItemStack {
                    if (!simulate && slot == 1) throw IllegalStateException("second target slot failed before mutation")
                    return super.insertItem(slot, stack, simulate)
                }
            }
        var propagated = false
        try {
            fixture.deposit.depositToHandler(target)
        } catch (_: IllegalStateException) {
            propagated = true
        }

        helper.assertTrue(propagated, "Later target failure was swallowed")
        helper.assertTrue(target.getStackInSlot(0).count == 1, "First target slot did not retain its accepted Gem")
        helper.assertTrue(
            dedicatedCount(fixture.case, key) == 2,
            "Deposit restored an already accepted Gem after a later failure",
        )
        helper.assertTrue(
            target.getStackInSlot(0).count + dedicatedCount(fixture.case, key) == 3,
            "Partial callback failure violated quantity conservation",
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restockStopsAfterSuccessStackBudgetAndLeavesTheRemainder(helper: GameTestHelper) {
        val fixture = restockFixture()
        val key = requireNotNull(StandardGem.keyOf(standardGem(1)))
        val source = ItemStackHandler(513)
        repeat(source.slots) { source.setStackInSlot(it, standardGem(1)) }

        val moved = fixture.restock.restockFromHandler(source)

        helper.assertTrue(
            dedicatedCount(fixture.case, key) == 512,
            "Restock exceeded or failed to reach the 512 successful-stack limit",
        )
        helper.assertTrue(
            source.getStackInSlot(512).count == 1,
            "Restock consumed the stack after its operation budget",
        )
        helper.assertTrue(moved.sumOf { it.count } == 512, "Budgeted Restock reported a different amount than it moved")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restockStopsAtHandlerVisitBudgetWithMaximumSavedEntries(helper: GameTestHelper) {
        val fixture = restockFixture()
        val existing = (0 until 4096).associate { index -> GemKey("test:gem_$index", "cracked") to GemCounts.CAPACITY }
        val ready = fixture.case.snapshot() as GemStorageState.Ready
        helper.assertTrue(
            fixture.case.replace(ready.counts, GemCounts.of(existing)),
            "Could not prepare maximum storage",
        )
        val source =
            ItemStackHandler(16_385).apply {
                setStackInSlot(0, standardGem(1))
                setStackInSlot(16_384, standardGem(1))
            }

        val moved = fixture.restock.restockFromHandler(source)

        val counts = (fixture.case.snapshot() as GemStorageState.Ready).counts
        helper.assertTrue(counts.entries().size == 4096, "Restock exceeded the maximum saved entry count")
        helper.assertTrue(
            source.getStackInSlot(0).count == 1 && source.getStackInSlot(16_384).count == 1,
            "A full store consumed an unaccepted source Gem",
        )
        helper.assertTrue(moved.isEmpty(), "Full storage reported a Restock that did not fit")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun restockCountsEveryExternalHandlerVisitWithinBudget(helper: GameTestHelper) {
        val fixture = restockFixture()
        val key = requireNotNull(StandardGem.keyOf(standardGem(1)))
        var visits = 0
        val source =
            object : ItemStackHandler(16_385) {
                override fun getStackInSlot(slot: Int): ItemStack {
                    visits++
                    return super.getStackInSlot(slot)
                }

                override fun extractItem(
                    slot: Int,
                    amount: Int,
                    simulate: Boolean,
                ): ItemStack {
                    visits++
                    return super.extractItem(slot, amount, simulate)
                }
            }.apply { setStackInSlot(0, standardGem(1)) }

        val moved =
            org.mocoserver.sophisticatedgemcase.storage.GemExternalTransfers
                .restock(fixture.backpack, source)

        helper.assertTrue(
            dedicatedCount(fixture.case, key) == 1,
            "Restock did not process an early source slot under the visit budget",
        )
        helper.assertTrue(moved.sumOf { it.count } == 1, "Restock did not report the early transferred stack")
        helper.assertTrue(visits <= 16_384, "Restock exceeded the total source-handler call budget: $visits")
        helper.succeed()
    }

    private fun backpack(): BackpackWrapper =
        BackpackWrapper(ItemStack(BackpackItems.DIAMOND_BACKPACK.get())).apply {
            setContentsUuid(UUID.randomUUID())
        }

    private fun depositFixture(): DepositFixture {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.DEPOSIT_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val deposit = backpack.upgradeHandler.slotWrappers.getValue(0) as DepositUpgradeWrapper
        deposit.filterLogic.setAllowList(false)
        return DepositFixture(backpack, deposit, requireNotNull(GemCaseUpgradeItem.find(backpack)))
    }

    private fun restockFixture(): RestockFixture {
        val backpack = backpack()
        backpack.upgradeHandler.setStackInSlot(0, ItemStack(BackpackItems.RESTOCK_UPGRADE.get()))
        backpack.upgradeHandler.setStackInSlot(1, ItemStack(ModItems.gemCaseUpgrade.get()))
        val restock = backpack.upgradeHandler.slotWrappers.getValue(0) as RestockUpgradeWrapper
        restock.filterLogic.setAllowList(true)
        restock.filterLogic.filterHandler.setStackInSlot(0, ItemStack(Items.IRON_INGOT))
        fillInventory(backpack)
        val case = requireNotNull(GemCaseUpgradeItem.find(backpack))
        setSettings(case, GemTransferDirection.RESTOCK)
        return RestockFixture(backpack, restock, case)
    }

    private fun dedicatedCount(
        wrapper: GemCaseUpgradeWrapper,
        key: GemKey,
    ): Int = (wrapper.snapshot() as GemStorageState.Ready).counts.count(key)

    private fun standardGem(count: Int): ItemStack {
        val gem = GemRegistry.INSTANCE.values.first { it.minPurity == Purity.CRACKED }
        return GemItem.createStack(gem, Purity.CRACKED, count)
    }

    private fun setDedicatedCount(
        wrapper: org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper,
        key: org.mocoserver.sophisticatedgemcase.core.GemKey,
        count: Int,
    ) {
        val state = wrapper.snapshot() as GemStorageState.Ready
        helperCheck(wrapper.replace(state.counts, GemCounts.of(mapOf(key to count))))
    }

    private fun setSettings(
        wrapper: org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper,
        direction: GemTransferDirection,
        filter: GemFilter = GemFilter.of(GemFilterMode.ALL, emptySet(), emptySet()),
    ) {
        replaceSettings(wrapper, direction, filter)
    }

    private fun replaceSettings(
        wrapper: org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper,
        direction: GemTransferDirection,
        filter: GemFilter,
    ) {
        val current = (wrapper.settingsSnapshot() as GemSettingsState.Ready).settings
        val enabled = GemTransferRule(true, filter)
        val settings =
            when (direction) {
                GemTransferDirection.DEPOSIT -> GemTransferSettings(enabled, current.restock)
                GemTransferDirection.RESTOCK -> GemTransferSettings(current.deposit, enabled)
            }
        helperCheck(wrapper.replaceSettings(current, settings))
    }

    private fun fillInventory(backpack: BackpackWrapper) {
        repeat(
            backpack.inventoryHandler.slots,
        ) { slot -> backpack.inventoryHandler.setStackInSlot(slot, ItemStack(Items.STONE, 64)) }
    }

    private fun helperCheck(condition: Boolean) {
        check(condition)
    }
}
