package com.wraithhawit.rsapg.menu;

import java.util.function.BooleanSupplier;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Stands in for one of Refined Storage's slots and can be switched off. Slot lists must be the
 * same on client and server from the moment the menu is built, before anything is synced, so the
 * advanced grid keeps RS's blank-pattern slot and hides it instead of leaving it out.
 */
public class SwitchableSlot extends Slot {
    private final Slot inner;
    private final BooleanSupplier active;

    public SwitchableSlot(final Slot inner, final BooleanSupplier active) {
        super(inner.container, inner.getContainerSlot(), inner.x, inner.y);
        this.inner = inner;
        this.active = active;
    }

    @Override
    public boolean mayPlace(final ItemStack stack) {
        return inner.mayPlace(stack);
    }

    @Override
    public int getMaxStackSize(final ItemStack stack) {
        return inner.getMaxStackSize(stack);
    }

    @Override
    public boolean isActive() {
        return active.getAsBoolean();
    }
}
