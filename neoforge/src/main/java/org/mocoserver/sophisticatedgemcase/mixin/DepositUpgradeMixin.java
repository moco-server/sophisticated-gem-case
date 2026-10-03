package org.mocoserver.sophisticatedgemcase.mixin;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.upgrades.deposit.DepositUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeWrapper;
import org.mocoserver.sophisticatedgemcase.integration.GemTransferBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** 対象版Depositの通常転送後に、専用収納から実受入分だけを追加する。 */
@Mixin(value = DepositUpgradeWrapper.class, remap = false)
public abstract class DepositUpgradeMixin {
    @Inject(
            method = "depositToHandler(Lnet/neoforged/neoforge/items/IItemHandler;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void gemCase$afterDeposit(
            final IItemHandler target,
            final CallbackInfoReturnable<List<ItemStack>> callback
    ) {
        final IStorageWrapper storage = ((GemUpgradeStorageAccessor) this).gemCase$getStorageWrapper();
        callback.setReturnValue(
                GemTransferBridge.deposit(storage, (IUpgradeWrapper) this, target, callback.getReturnValue())
        );
    }
}
