package org.mocoserver.sophisticatedgemcase.mixin.client;

import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Core 1.5.2.2343の予測・差分送信とpickup-all制限をmodalから再利用する。 */
@Mixin(value = StorageScreenBase.class, remap = false)
public interface GemStorageScreenAccessor {
    @Invoker("slotClicked")
    void gemCaseClickSlot(Slot slot, int index, int button, ClickType type);

    @Accessor("modalOverlay")
    WidgetBase gemCaseModalOverlay();
}
