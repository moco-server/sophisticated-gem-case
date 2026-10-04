package org.mocoserver.sophisticatedgemcase.integration

import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase
import org.mocoserver.sophisticatedgemcase.ModDataComponents
import org.mocoserver.sophisticatedgemcase.ModItems

internal object GemCaseConversionActions {
    // native save callbackの例外後に元upgrade/cursorを戻すためRuntimeExceptionを境界で処理する。
    @Suppress("TooGenericExceptionCaught")
    fun convert(context: GemCaseConversionContext) {
        val prepared = prepare(context) ?: return
        if (!matchesBeforeConversion(context, prepared)) return
        try {
            persistConvertedUpgrade(context, prepared)
        } catch (failure: RuntimeException) {
            restoreAfterFailure(context, prepared, failure)
            throw failure
        }
    }

    private fun prepare(context: GemCaseConversionContext): PreparedGemCaseConversion? {
        val oldStack =
            context.storage.upgradeHandler
                .getStackInSlot(context.slotIndex)
                .copy()
        val cursorStack = context.menu.carried.copy()
        val targetStack = ItemStack(ModItems.advancedGemCaseUpgrade.get(), 1)
        targetStack.applyComponents(oldStack.components)
        val eligible =
            GemCaseConversionSession.isCurrent(context) &&
                isConvertibleLower(oldStack) &&
                isConversionCursor(cursorStack) &&
                GemCaseConversionEligibility.canReplaceWithAdvanced(context, oldStack, targetStack)
        return if (eligible) PreparedGemCaseConversion(oldStack, targetStack, cursorStack) else null
    }

    private fun isConvertibleLower(stack: ItemStack): Boolean =
        !stack.isEmpty &&
            stack.item === ModItems.gemCaseUpgrade.get() &&
            stack.count == 1 &&
            hasNoUpperComponents(stack)

    private fun hasNoUpperComponents(stack: ItemStack): Boolean =
        !stack.has(ModDataComponents.gemProcessingMaterials.get()) &&
            !stack.has(ModDataComponents.gemProcessingSettings.get())

    private fun isConversionCursor(stack: ItemStack): Boolean =
        !stack.isEmpty && stack.item === ModItems.gemCaseUpgradeConversion.get()

    private fun matchesBeforeConversion(
        context: GemCaseConversionContext,
        prepared: PreparedGemCaseConversion,
    ): Boolean =
        GemCaseConversionSession.isCurrent(context) &&
            ItemStack.matches(prepared.oldStack, context.storage.upgradeHandler.getStackInSlot(context.slotIndex)) &&
            ItemStack.matches(prepared.cursorStack, context.menu.carried)

    private fun persistConvertedUpgrade(
        context: GemCaseConversionContext,
        prepared: PreparedGemCaseConversion,
    ) {
        context.storage.upgradeHandler.setStackInSlot(context.slotIndex, prepared.targetStack.copy())
        notifyCurrentSlot(context)
        check(GemCaseConversionPersistence.isPersistedUpper(context, prepared.targetStack)) {
            "Gem Case conversion did not persist the target upgrade"
        }
        check(ItemStack.matches(prepared.cursorStack, context.menu.carried)) {
            "Gem Case cursor changed during conversion"
        }
        consumeConversionItem(context, prepared.cursorStack)
    }

    private fun consumeConversionItem(
        context: GemCaseConversionContext,
        cursor: ItemStack,
    ) {
        if (context.player.abilities.instabuild) return
        val updated = cursor.copy()
        updated.shrink(1)
        context.menu.carried = if (updated.isEmpty) ItemStack.EMPTY else updated
    }

    private fun notifyCurrentSlot(context: GemCaseConversionContext) {
        val slot =
            checkNotNull(
                context.menu.getSlot(context.globalSlotId) as? StorageContainerMenuBase<*>.StorageUpgradeSlot,
            ) { "Gem Case upgrade slot disappeared during conversion" }
        check((slot as Slot).index == context.globalSlotId) {
            "Gem Case upgrade slot identity changed during conversion"
        }
        check(slot.slotIndex == context.slotIndex) { "Gem Case upgrade slot index changed during conversion" }
        slot.setChanged()
    }

    // native wrapper/save callbackはRuntimeExceptionを投げ得るため、component復元後も元例外を保つ。
    @Suppress("TooGenericExceptionCaught")
    private fun restoreAfterFailure(
        context: GemCaseConversionContext,
        prepared: PreparedGemCaseConversion,
        failure: RuntimeException,
    ) {
        try {
            val handler = context.storage.upgradeHandler
            if (!ItemStack.matches(prepared.oldStack, handler.getStackInSlot(context.slotIndex))) {
                handler.setStackInSlot(context.slotIndex, prepared.oldStack.copy())
            }
            notifyCurrentSlot(context)
            check(
                ItemStack.matches(
                    prepared.oldStack,
                    context.storage.upgradeHandler.getStackInSlot(context.slotIndex),
                ),
            ) { "Gem Case conversion could not restore its source upgrade" }
        } catch (recovery: RuntimeException) {
            failure.addSuppressed(recovery)
        }
        context.menu.carried = prepared.cursorStack.copy()
    }
}
