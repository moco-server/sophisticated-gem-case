package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.world.item.ItemStack
import net.neoforged.fml.util.thread.SidedThreadGroups
import net.neoforged.neoforge.items.IItemHandler
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.integration.GemOperationContext
import org.mocoserver.sophisticatedgemcase.integration.GemTransferPermit
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeItem
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper

/** native Deposit／Restockの個数転送だけを、Gem専用保存へ接続する。 */
@Suppress("TooManyFunctions") // 転送一件の予約・callback・復元を分割した同一境界の手順群。
internal object GemExternalTransfers {
    private const val MAX_SUCCESSFUL_STACKS = 512
    private const val MAX_HANDLER_SLOT_VISITS = 16_384

    // simulate・実extract・失敗時の復元に使う最大訪問数。
    private const val MAX_RESTOCK_VISITS_PER_STACK = 3
    private const val RESTOCK_SCAN_VISIT_BUDGET = MAX_HANDLER_SLOT_VISITS / 2

    private data class Budget(
        var successfulStacks: Int = 0,
        var slotVisits: Int = 0,
    )

    private data class DepositStackContext(
        val wrapper: GemCaseUpgradeWrapper,
        val target: IItemHandler,
        val key: GemKey,
        val standard: ItemStack,
        val budget: Budget,
        val permit: GemTransferPermit,
        val moved: MutableList<ItemStack>,
    )

    private data class DepositRunContext(
        val wrapper: GemCaseUpgradeWrapper,
        val target: IItemHandler,
        val settings: GemSettingsState.Ready,
        val budget: Budget,
        val permit: GemTransferPermit,
        val moved: MutableList<ItemStack>,
    )

    private data class RestockStackContext(
        val wrapper: GemCaseUpgradeWrapper,
        val source: IItemHandler,
        val key: GemKey,
        val standard: ItemStack,
        val sourceSlot: Int,
        val observed: ItemStack,
        val budget: Budget,
        val permit: GemTransferPermit,
    )

    private data class RestockRunContext(
        val wrapper: GemCaseUpgradeWrapper,
        val source: IItemHandler,
        val budget: Budget,
        val permit: GemTransferPermit,
        val moved: MutableList<ItemStack>,
    )

    @Suppress("ReturnCount") // server側・設定状態・exclusive再入の拒否を早期に返す境界。
    fun deposit(
        storage: IStorageWrapper,
        target: IItemHandler,
    ): List<ItemStack> =
        if (Thread.currentThread().threadGroup != SidedThreadGroups.SERVER) {
            emptyList()
        } else {
            GemCaseUpgradeItem
                .find(storage)
                ?.let { wrapper ->
                    (wrapper.settingsSnapshot() as? GemSettingsState.Ready)
                        ?.takeIf { it.settings.deposit.enabled }
                        ?.let { settings ->
                            GemOperationContext.exclusive(storage) { permit ->
                                depositExclusive(storage, wrapper, target, settings, permit)
                            }
                        }
                }.orEmpty()
        }

    @Suppress("ReturnCount") // server側・設定状態・exclusive再入の拒否を早期に返す境界。
    fun restock(
        storage: IStorageWrapper,
        source: IItemHandler,
    ): List<ItemStack> =
        if (Thread.currentThread().threadGroup != SidedThreadGroups.SERVER) {
            emptyList()
        } else {
            GemCaseUpgradeItem
                .find(storage)
                ?.let { wrapper ->
                    (wrapper.settingsSnapshot() as? GemSettingsState.Ready)
                        ?.takeIf { it.settings.restock.enabled }
                        ?.let { settings ->
                            GemOperationContext.exclusive(storage) { permit ->
                                restockExclusive(storage, wrapper, source, settings, permit)
                            }
                        }
                }.orEmpty()
        }

    private fun depositExclusive(
        storage: IStorageWrapper,
        wrapper: GemCaseUpgradeWrapper,
        target: IItemHandler,
        settings: GemSettingsState.Ready,
        permit: GemTransferPermit,
    ): List<ItemStack> {
        val initial = wrapper.snapshot() as? GemStorageState.Ready
        if (initial == null || GemCaseUpgradeItem.find(storage) !== wrapper) return emptyList()
        val budget = Budget()
        val moved = mutableListOf<ItemStack>()
        val context = DepositRunContext(wrapper, target, settings, budget, permit, moved)
        val keys = sortedKeys(initial.counts)
        var index = 0
        while (index < keys.size && hasTransferBudget(budget)) {
            transferDepositKeyIfEligible(context, keys[index])
            index++
        }
        return moved
    }

    private fun sortedKeys(counts: GemCounts): List<GemKey> =
        counts.entries().keys.sortedWith(compareBy<GemKey>({ it.gemId }, { it.purity }))

    private fun transferDepositKeyIfEligible(
        context: DepositRunContext,
        key: GemKey,
    ) {
        if (!context.settings.settings.deposit.filter
                .matches(key)
        ) {
            return
        }
        val standard = StandardGem.stackOf(key) ?: return
        transferDepositKey(
            DepositStackContext(
                context.wrapper,
                context.target,
                key,
                standard,
                context.budget,
                context.permit,
                context.moved,
            ),
        )
    }

    private fun transferDepositKey(context: DepositStackContext) {
        while (
            hasTransferBudget(context.budget) &&
            transferOneDepositStack(context)
        ) {
            // 1スタック分の保存予約・外部投入・未受入分復元をまとめる。
        }
    }

    /**
     * 各段階の失敗では保存を進めず、外部callback例外では未受入分を戻して再送出する。
     * 任意の外部handler callback後も未受入予約を戻す契約のため、RuntimeException全体を捕捉する。
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private fun transferOneDepositStack(context: DepositStackContext): Boolean {
        val state = context.wrapper.snapshot() as? GemStorageState.Ready ?: return false
        val available = state.counts.count(context.key)
        if (available <= 0) return false
        val amount = minOf(available, context.standard.maxStackSize)
        val reservation = state.counts.extract(context.key, amount)
        if (reservation.moved != amount ||
            !GemStorageCodec.canWrite(reservation.state)
        ) {
            return false
        }
        if (!context.wrapper.replace(state.counts, reservation.state, context.permit)) return false

        val reservedStack = context.standard.copyWithCount(amount)
        var unacceptedCount = amount
        // IItemHandler実装は外部MOD callbackなので、任意のRuntimeExceptionでも未受入予約を戻す。
        val remainder =
            try {
                insertAcross(context.target, reservedStack, context.budget) { currentRemainder ->
                    unacceptedCount = currentRemainder.count
                }
            } catch (failure: RuntimeException) {
                try {
                    restore(context.wrapper, context.key, unacceptedCount, context.permit)
                } catch (restoreFailure: RuntimeException) {
                    failure.addSuppressed(restoreFailure)
                }
                throw failure
            }
        val accepted = amount - remainder.count
        if (!remainder.isEmpty) {
            restore(context.wrapper, context.key, remainder.count, context.permit)
        }
        if (accepted <= 0) return false
        context.moved.add(context.standard.copyWithCount(accepted))
        context.budget.successfulStacks++
        return true
    }

    private fun hasTransferBudget(budget: Budget): Boolean =
        budget.successfulStacks < MAX_SUCCESSFUL_STACKS &&
            budget.slotVisits < MAX_HANDLER_SLOT_VISITS

    private fun insertAcross(
        target: IItemHandler,
        stack: ItemStack,
        budget: Budget,
        onRemainder: (ItemStack) -> Unit,
    ): ItemStack {
        var remainder = stack.copy()
        onRemainder(remainder)
        var slot = 0
        while (
            !remainder.isEmpty &&
            slot < target.slots &&
            budget.slotVisits < MAX_HANDLER_SLOT_VISITS
        ) {
            budget.slotVisits++
            val offered = remainder.copy()
            val unchangedOffer = offered.copy()
            val after = target.insertItem(slot, offered, false)
            check(ItemStack.matches(offered, unchangedOffer)) {
                "External target mutated the offered stack"
            }
            validateRemainder(unchangedOffer, after)
            remainder = after
            onRemainder(remainder)
            slot++
        }
        return remainder
    }

    private fun restockExclusive(
        storage: IStorageWrapper,
        wrapper: GemCaseUpgradeWrapper,
        source: IItemHandler,
        settings: GemSettingsState.Ready,
        permit: GemTransferPermit,
    ): List<ItemStack> {
        if (GemCaseUpgradeItem.find(storage) !== wrapper) return emptyList()
        val budget = Budget()
        val candidates = collectRestockCandidates(source, settings.settings.restock, budget)
        return transferRestockCandidates(wrapper, source, candidates, budget, permit)
    }

    private fun transferRestockCandidates(
        wrapper: GemCaseUpgradeWrapper,
        source: IItemHandler,
        candidates: Map<GemKey, List<Pair<Int, ItemStack>>>,
        budget: Budget,
        permit: GemTransferPermit,
    ): List<ItemStack> {
        val moved = mutableListOf<ItemStack>()
        val context = RestockRunContext(wrapper, source, budget, permit, moved)
        val keys = candidates.keys.toList()
        var index = 0
        while (index < keys.size && budget.successfulStacks < MAX_SUCCESSFUL_STACKS) {
            transferRestockKey(context, keys[index], candidates.getValue(keys[index]))
            index++
        }
        return moved
    }

    private fun transferRestockKey(
        context: RestockRunContext,
        key: GemKey,
        stacks: List<Pair<Int, ItemStack>>,
    ) {
        val standard = StandardGem.stackOf(key) ?: return
        var index = 0
        while (index < stacks.size && context.budget.successfulStacks < MAX_SUCCESSFUL_STACKS) {
            val (sourceSlot, observed) = stacks[index]
            val stackContext =
                RestockStackContext(
                    context.wrapper,
                    context.source,
                    key,
                    standard,
                    sourceSlot,
                    observed,
                    context.budget,
                    context.permit,
                )
            restockOneStack(stackContext)?.let { transferred ->
                context.moved.add(transferred)
                context.budget.successfulStacks++
            }
            index++
        }
    }

    private fun collectRestockCandidates(
        source: IItemHandler,
        rule: org.mocoserver.sophisticatedgemcase.core.GemTransferRule,
        budget: Budget,
    ): Map<GemKey, List<Pair<Int, ItemStack>>> {
        val candidates =
            sortedMapOf<GemKey, MutableList<Pair<Int, ItemStack>>>(
                compareBy({ it.gemId }, { it.purity }),
            )
        var slot = 0
        while (slot < source.slots && budget.slotVisits < RESTOCK_SCAN_VISIT_BUDGET) {
            budget.slotVisits++
            val stack = source.getStackInSlot(slot).copy()
            val key = StandardGem.keyOf(stack)
            if (key != null && rule.filter.matches(key)) {
                candidates.getOrPut(key) { mutableListOf() }.add(slot to stack)
            }
            slot++
        }
        return candidates
    }

    private data class RestockPlan(
        val previousCounts: GemCounts,
        val requestedCount: Int,
    )

    @Suppress("ReturnCount") // 保存不可・空きなし・codec拒否を各段階で明示して副作用前に終える。
    private fun prepareRestock(
        wrapper: GemCaseUpgradeWrapper,
        key: GemKey,
        observed: ItemStack,
        standard: ItemStack,
    ): RestockPlan? {
        val state = wrapper.snapshot() as? GemStorageState.Ready ?: return null
        val currentCount = state.counts.count(key)
        val free =
            if (currentCount > 0) {
                GemCounts.CAPACITY - currentCount
            } else if (state.counts.entries().size < GemStorageCodec.MAX_ENTRIES) {
                GemCounts.CAPACITY
            } else {
                0
            }
        if (free <= 0) return null
        val requested = minOf(observed.count, standard.maxStackSize, free)
        val preview = state.counts.insert(key, requested)
        return if (preview.moved == requested && GemStorageCodec.canWrite(preview.state)) {
            RestockPlan(state.counts, requested)
        } else {
            null
        }
    }

    @Suppress("ReturnCount") // source観測／抽出／保存確定の各拒否点で未変更のまま返す。
    private fun restockOneStack(context: RestockStackContext): ItemStack? {
        // 3枠はsimulate、実extract、失敗時のsource復元に使うhandler訪問上限。
        if (context.budget.slotVisits + MAX_RESTOCK_VISITS_PER_STACK > MAX_HANDLER_SLOT_VISITS) {
            return null
        }
        val plan =
            prepareRestock(context.wrapper, context.key, context.observed, context.standard)
                ?: return null
        val extracted =
            extractRestockStack(
                context.source,
                context.sourceSlot,
                context.observed,
                plan.requestedCount,
                context.budget,
            ) ?: return null
        val current = context.wrapper.snapshot() as? GemStorageState.Ready
        val change = current?.counts?.insert(context.key, extracted.count)
        val accepted =
            current != null &&
                change?.moved == extracted.count &&
                GemStorageCodec.canWrite(change.state)
        val committed =
            accepted &&
                context.wrapper.replace(
                    current?.counts ?: plan.previousCounts,
                    change?.state ?: plan.previousCounts,
                    context.permit,
                )
        return if (committed) {
            context.standard.copyWithCount(extracted.count)
        } else {
            context.budget.slotVisits++
            restoreSource(context.source, context.sourceSlot, extracted)
            null
        }
    }

    @Suppress("ReturnCount") // simulate/extract拒否と契約違反を区別して早期終了する。
    private fun extractRestockStack(
        source: IItemHandler,
        sourceSlot: Int,
        observed: ItemStack,
        requested: Int,
        budget: Budget,
    ): ItemStack? {
        budget.slotVisits++
        val simulated = source.extractItem(sourceSlot, requested, true)
        if (simulated.isEmpty || !ItemStack.isSameItemSameComponents(observed, simulated)) {
            return null
        }
        val simulatedCount = minOf(requested, simulated.count)
        if (simulatedCount <= 0) return null
        budget.slotVisits++
        val extracted = source.extractItem(sourceSlot, simulatedCount, false)
        if (extracted.isEmpty) return null
        if (!ItemStack.isSameItemSameComponents(observed, extracted) ||
            extracted.count > simulatedCount
        ) {
            budget.slotVisits++
            restoreSource(source, sourceSlot, extracted)
            error("External source returned an invalid extracted stack")
        }
        return extracted
    }

    private fun restore(
        wrapper: GemCaseUpgradeWrapper,
        key: GemKey,
        count: Int,
        permit: GemTransferPermit,
    ) {
        if (count <= 0) return
        val current =
            wrapper.snapshot() as? GemStorageState.Ready
                ?: error("Gem storage became unavailable during transfer")
        val restored = current.counts.insert(key, count)
        if (restored.moved != count ||
            !GemStorageCodec.canWrite(restored.state) ||
            !wrapper.replace(current.counts, restored.state, permit)
        ) {
            error("Could not restore an unaccepted dedicated Gem reservation")
        }
    }

    private fun restoreSource(
        source: IItemHandler,
        slot: Int,
        extracted: ItemStack,
    ) {
        val remainder = source.insertItem(slot, extracted.copy(), false)
        check(remainder.isEmpty) { "External source refused restoration after a failed transfer" }
    }

    private fun validateRemainder(
        offered: ItemStack,
        remainder: ItemStack,
    ) {
        check(
            remainder.isEmpty ||
                (
                    ItemStack.isSameItemSameComponents(offered, remainder) &&
                        remainder.count in 0..offered.count
                ),
        ) { "External target returned an invalid remainder" }
    }
}
