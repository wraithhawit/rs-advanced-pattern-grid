package com.wraithhawit.rsapg;

import com.refinedmods.refinedstorage.common.api.upgrade.UpgradeDestination;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The advanced grid as a Refined Storage upgrade destination, so upgrade items list it in their
 * tooltip and its empty upgrade slots show which upgrades fit.
 */
public enum GridUpgradeDestination implements UpgradeDestination {
    ADVANCED_PATTERN_GRID;

    /** Same count as RS's machines and Refined Wireless Upgrades' grid panel. */
    public static final int SLOTS = 4;

    @Override
    public Component getName() {
        return Component.translatable("block.rsapg.advanced_pattern_grid");
    }

    @Override
    public ItemStack getStackRepresentation() {
        return new ItemStack(ModBlocks.ADVANCED_PATTERN_GRID.getDefault());
    }
}
