package com.wraithhawit.rsapg.mixin.emi;

import com.wraithhawit.rsapg.client.tree.TreeDumpButton;

import dev.emi.emi.screen.BoMScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the "encode missing patterns" icon to EMI's recipe tree screen. */
@Mixin(value = BoMScreen.class, remap = false)
public abstract class BoMScreenMixin extends Screen {
    @Shadow
    public AbstractContainerScreen<?> old;

    protected BoMScreenMixin(final Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void rsapg$renderTreeButton(final GuiGraphics graphics, final int mouseX, final int mouseY,
                                        final float delta, final CallbackInfo ci) {
        TreeDumpButton.render(this, graphics, mouseX, mouseY, old);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void rsapg$clickTreeButton(final double mouseX, final double mouseY, final int button,
                                       final CallbackInfoReturnable<Boolean> cir) {
        if (TreeDumpButton.click(this, mouseX, mouseY, button, old)) {
            cir.setReturnValue(true);
        }
    }
}
