package org.mocoserver.sophisticatedgemcase.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.upgrades.restock.RestockUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeWrapper;
import org.mocoserver.sophisticatedgemcase.integration.GemTransferBridge;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

/** native Restock全体をfinally付き抑止文脈で包み、その後に専用転送を行う。 */
@Mixin(value = RestockUpgradeWrapper.class, remap = false)
public abstract class RestockUpgradeMixin {
    @WrapMethod(
            method = "restockFromHandler(Lnet/neoforged/neoforge/items/IItemHandler;)Ljava/util/List;",
            remap = false
    )
    private List<ItemStack> gemCase$wrapRestock(
            final IItemHandler source,
            final Operation<List<ItemStack>> original
    ) {
        final IStorageWrapper storage = ((GemUpgradeStorageAccessor) this).gemCase$getStorageWrapper();
        final List<ItemStack> transferred = GemTransferBridge.withoutRouting(storage, () -> original.call(source));
        return GemTransferBridge.restock(storage, (IUpgradeWrapper) this, source, transferred);
    }
}
