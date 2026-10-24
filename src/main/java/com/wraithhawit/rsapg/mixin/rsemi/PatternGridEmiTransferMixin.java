package com.wraithhawit.rsapg.mixin.rsemi;

import com.wraithhawit.rsapg.AdvancedGridHost;
import com.wraithhawit.rsapg.ModNetwork;
import com.wraithhawit.rsapg.client.tree.TreeCollector;
import com.wraithhawit.rsapg.menu.AdvancedMenu;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EMI's "+" into an Advanced Pattern Grid: also tell the server which machines run the recipe, so
 * the create button's auto-place knows where a processing pattern belongs.
 */
@Mixin(targets = "com.refinedmods.refinedstorage.emi.common.PatternGridEmiRecipeHandler", remap = false)
public abstract class PatternGridEmiTransferMixin {
    @Inject(method = "craft", at = @At("HEAD"))
    private void rsapg$rememberTransfer(final EmiRecipe recipe, final EmiCraftContext<?> context,
                                        final CallbackInfoReturnable<Boolean> cir) {
        final AbstractContainerMenu menu = context.getScreenHandler();
        if (menu instanceof AdvancedMenu advanced && AdvancedGridHost.isAdvanced(advanced.rsapg$syncValue())) {
            PacketDistributor.sendToServer(new ModNetwork.TransferContext(menu.containerId, TreeCollector.convert(recipe)));
        }
    }
}
