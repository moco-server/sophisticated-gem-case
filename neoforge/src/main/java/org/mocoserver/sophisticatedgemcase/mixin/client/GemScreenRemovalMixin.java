package org.mocoserver.sophisticatedgemcase.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import org.mocoserver.sophisticatedgemcase.client.GemCaseModalLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MC 1.21.1のcontainer画面はScreen.removedを呼ばないため、直接の継承元へ接続する。 */
@Mixin(AbstractContainerScreen.class)
public abstract class GemScreenRemovalMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void gemCaseRemove(final CallbackInfo callback) {
        if ((Object) this instanceof StorageScreenBase<?> screen) {
            GemCaseModalLifecycle.closeOverlay(screen);
        }
    }
}
