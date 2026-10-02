package org.mocoserver.sophisticatedgemcase.mixin;

import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemHandler;
import org.mocoserver.sophisticatedgemcase.integration.GemCaseBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 3.26.6.2174の公開IOだけを包み、内部アップグレード処理への自動回収能力を追加しない。 */
@Mixin(value = BackpackWrapper.class, remap = false)
public abstract class BackpackWrapperMixin {
    @Inject(method = "getInventoryForInputOutput", at = @At("RETURN"), cancellable = true)
    private void gemCaseInputOutput(final CallbackInfoReturnable<ITrackedContentsItemHandler> callback) {
        callback.setReturnValue(GemCaseBridge.wrapInputOutput((BackpackWrapper) (Object) this, callback.getReturnValue()));
    }
}
