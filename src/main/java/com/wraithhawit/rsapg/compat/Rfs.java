package com.wraithhawit.rsapg.compat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Refined Fluid Substitution, if installed. Every RFS class is touched only from {@link RfsImpl},
 * which is loaded only when the mod is present.
 */
public final class Rfs {
    private static final boolean LOADED = ModList.get().isLoaded("refinedfluidsubstitution");

    private Rfs() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    /** True if RFS would substitute at least one fluid container in this encoded crafting pattern. */
    public static boolean qualifies(final ItemStack encodedCraftingPattern, final Level level) {
        return LOADED && RfsImpl.qualifies(encodedCraftingPattern, level);
    }

    public static boolean isFluidPattern(final ItemStack stack) {
        return LOADED && RfsImpl.isFluidPattern(stack);
    }

    /** A blank Fluid Substitution Pattern, or empty when RFS is absent. */
    public static ItemStack blank() {
        return LOADED ? RfsImpl.blank() : ItemStack.EMPTY;
    }
}
