package com.wraithhawit.rsapg.menu;

import com.refinedmods.refinedstorage.common.upgrade.UpgradeContainer;
import com.refinedmods.refinedstorage.common.upgrade.UpgradeSlot;

import java.util.function.BooleanSupplier;

import net.minecraft.world.item.ItemStack;

/**
 * One of the advanced grid's upgrade slots in the side panel. RS's own UpgradeSlot (so RS shows
 * the allowed-upgrades tooltip), present but switched off in a plain pattern grid's menu.
 */
public class AdvancedUpgradeSlot extends UpgradeSlot {
    /** Panel position relative to the screen's left/top, the same as Refined Wireless Upgrades' grid panel. */
    public static final int FIRST_X = 204;
    public static final int FIRST_Y = 6;
    public static final int ROW = 18;

    private final BooleanSupplier active;

    public AdvancedUpgradeSlot(final UpgradeContainer container, final int index, final BooleanSupplier active) {
        super(container, index, FIRST_X, FIRST_Y + index * ROW);
        this.active = active;
    }

    @Override
    public boolean mayPlace(final ItemStack stack) {
        return active.getAsBoolean() && super.mayPlace(stack);
    }

    @Override
    public boolean isActive() {
        return active.getAsBoolean();
    }
}
