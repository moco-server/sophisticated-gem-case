package org.mocoserver.sophisticatedgemcase.mixin;

import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 固定版UpgradeWrapperBaseから、native wrapperが保持するバッグを取得する。 */
@Mixin(value = UpgradeWrapperBase.class, remap = false)
public interface GemUpgradeStorageAccessor {
    @Accessor(value = "storageWrapper", remap = false)
    IStorageWrapper gemCase$getStorageWrapper();
}
