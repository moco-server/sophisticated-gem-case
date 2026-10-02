package org.mocoserver.sophisticatedgemcase.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** protectedの元処理へ戻り、通常収納の既存規則を再実装しない。対象版限定のbridge。 */
@Mixin(value = StorageContainerMenuBase.class, remap = false)
public interface GemMergeInvoker {
    @Invoker("mergeItemStack")
    ItemStack gemCaseMerge(ItemStack stack, int start, int end, boolean reverse, boolean maxStack, boolean overflow);
    @Accessor("player")
    Player gemCasePlayer();
}
