package com.wraithhawit.rsapg.compat;

import com.ultramega.refinedfluidsubstitution.common.fluidsubstitutionpattern.FluidSubstitutionPatternItem;
import com.ultramega.refinedfluidsubstitution.common.fluidsubstitutionpattern.FluidSubstitutionPatternResolver;
import com.ultramega.refinedfluidsubstitution.common.registry.Items;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Only loaded when Refined Fluid Substitution is installed; go through {@link Rfs}. */
final class RfsImpl {
    private RfsImpl() {
    }

    static boolean qualifies(final ItemStack encodedCraftingPattern, final Level level) {
        // RFS's own rules: per crafting slot, a container that drains exactly one bucket and whose
        // empty container comes back as a crafting remainder.
        return FluidSubstitutionPatternResolver.resolve(encodedCraftingPattern, level)
            .map(resolved -> !resolved.substitutions().isEmpty())
            .orElse(false);
    }

    static boolean isFluidPattern(final ItemStack stack) {
        return stack.getItem() instanceof FluidSubstitutionPatternItem;
    }

    static ItemStack blank() {
        return new ItemStack(Items.INSTANCE.getFluidSubstitutionPattern());
    }
}
