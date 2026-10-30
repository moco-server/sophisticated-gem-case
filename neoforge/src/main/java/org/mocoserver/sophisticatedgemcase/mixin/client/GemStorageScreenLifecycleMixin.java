package org.mocoserver.sophisticatedgemcase.mixin.client;

import net.minecraft.client.Minecraft;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase;
import org.lwjgl.glfw.GLFW;
import org.mocoserver.sophisticatedgemcase.client.GemCaseModalContent;
import org.mocoserver.sophisticatedgemcase.client.GemCaseModalLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 公開overlay APIが閉じる理由を通知しないため、差替えと再初期化を補う。 */
@Mixin(value = StorageScreenBase.class, remap = false)
public abstract class GemStorageScreenLifecycleMixin {
    @Shadow
    private WidgetBase modalOverlay;

    @Unique
    private int gemCaseClickButton = -1;

    @Unique
    private int gemCaseSuppressedReleases;

    @Inject(method = "setModalOverlay", at = @At("HEAD"))
    private void gemCaseReplaceOverlay(final WidgetBase replacement, final CallbackInfo callback) {
        final WidgetBase previous = modalOverlay;
        if (previous != replacement) {
            if (previous instanceof GemCaseModalContent) {
                if (gemCaseClickButton >= 0 && gemCaseClickButton <= GLFW.GLFW_MOUSE_BUTTON_LAST) {
                    gemCaseSuppressedReleases |= 1 << gemCaseClickButton;
                }
                final long window = Minecraft.getInstance().getWindow().getWindow();
                for (int button = 0; button <= GLFW.GLFW_MOUSE_BUTTON_LAST; button++) {
                    if (GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS) {
                        gemCaseSuppressedReleases |= 1 << button;
                    }
                }
            }
            // close内からsetModalOverlay(null)を呼んでも同じ旧表示へ再入しない。
            modalOverlay = null;
            GemCaseModalLifecycle.closePrevious(previous);
        }
    }

    @Inject(method = "init()V", at = @At("HEAD"))
    private void gemCaseReinitialize(final CallbackInfo callback) {
        GemCaseModalLifecycle.closeOverlay((StorageScreenBase<?>) (Object) this);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void gemCaseBeginClick(final double x, final double y, final int button,
            final CallbackInfoReturnable<Boolean> callback) {
        gemCaseClickButton = button;
        if (button >= 0 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST) {
            // releaseを観測できなかった古いgestureは、新しい押下へ持ち越さない。
            gemCaseSuppressedReleases &= ~(1 << button);
        }
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void gemCaseEndClick(final double x, final double y, final int button,
            final CallbackInfoReturnable<Boolean> callback) {
        gemCaseClickButton = -1;
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void gemCaseConsumeModalRelease(final double x, final double y, final int button,
            final CallbackInfoReturnable<Boolean> callback) {
        if (button >= 0 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST && (gemCaseSuppressedReleases & (1 << button)) != 0) {
            gemCaseSuppressedReleases &= ~(1 << button);
            // modalを閉じた押下のreleaseで、背面slot操作やslot=-999への投棄を発生させない。
            callback.setReturnValue(true);
        }
    }
}
