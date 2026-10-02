package org.mocoserver.sophisticatedgemcase.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import org.mocoserver.sophisticatedgemcase.integration.GemCaseBridge;
import org.mocoserver.sophisticatedgemcase.integration.GemMergeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Core 1.5.2.2343のGUIはSlot.setで投入するため、対象の手動操作だけを接続する。 */
@Mixin(value = StorageContainerMenuBase.class, remap = false)
public abstract class StorageContainerMenuMixin {
    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void gemCaseBroadcast(final CallbackInfo callback) {
        org.mocoserver.sophisticatedgemcase.network.GemNetworking.INSTANCE.broadcast((StorageContainerMenuBase<?>) (Object) this);
    }

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void gemCaseClick(final int slot, final int button, final ClickType type, final Player player, final CallbackInfo callback) {
        if (GemCaseBridge.handleClick((StorageContainerMenuBase<?>) (Object) this, slot, button, type, player)) callback.cancel();
    }

    @Inject(method = "mergeItemStack(Lnet/minecraft/world/item/ItemStack;IIZZZ)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void gemCaseMerge(final ItemStack stack, final int start, final int end, final boolean reverse, final boolean maxStack,
            final boolean overflow, final CallbackInfoReturnable<ItemStack> callback) {
        final ItemStack result = GemCaseBridge.routeMerge((StorageContainerMenuBase<?>) (Object) this,
                new GemMergeInput(stack, start, end, reverse, maxStack, overflow));
        if (result != null) callback.setReturnValue(result);
    }
}
