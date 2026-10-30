package org.mocoserver.sophisticatedgemcase.mixin.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.p3pp3rf1y.sophisticatedbackpacks.client.gui.BackpackScreen;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import org.mocoserver.sophisticatedgemcase.client.GemCaseModalContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Backpacks 3.26.6.2174がCoreより先に閉じるキーを処理するため、modal中だけCoreへ渡す。
 * 固定作者版: https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/fd0e91bb6cd294a778c4bea770b64d71ef09d488/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/client/gui/BackpackScreen.java
 */
@Mixin(value = BackpackScreen.class, remap = false)
public abstract class GemBackpackScreenKeysMixin extends StorageScreenBase<BackpackContainer> {
    protected GemBackpackScreenKeysMixin(final BackpackContainer menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void gemCaseModalKeys(final int key, final int scan, final int modifiers,
            final CallbackInfoReturnable<Boolean> callback) {
        if (((GemStorageScreenAccessor) this).gemCaseModalOverlay() instanceof GemCaseModalContent) {
            // invokespecialで直接Coreへ進め、BackpackScreenのshortcutへ再入しない。
            callback.setReturnValue(super.keyPressed(key, scan, modifiers));
        }
    }
}
