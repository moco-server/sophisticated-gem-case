package org.mocoserver.sophisticatedgemcase.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** MC 1.21.1のfinal座標をnativeタブの移動に使う。外部MODの開発用ATへ依存しない。 */
@Mixin(Slot.class)
public interface GemSlotPositionAccessor {
    @Mutable
    @Accessor("x")
    void gemCaseSetX(int value);

    @Mutable
    @Accessor("y")
    void gemCaseSetY(int value);
}
