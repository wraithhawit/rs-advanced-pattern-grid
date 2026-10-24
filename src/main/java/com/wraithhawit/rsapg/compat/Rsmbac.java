package com.wraithhawit.rsapg.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/** rsmbac (multiblock autocrafter), if installed. rsmbac classes are touched only from {@link RsmbacImpl}. */
public final class Rsmbac {
    private static final boolean LOADED = ModList.get().isLoaded("rsmbac");

    private Rsmbac() {
    }

    public static boolean isController(final BlockEntity be) {
        return LOADED && RsmbacImpl.isController(be);
    }

    /** Empty Pattern Storage slots of the structure the controller at {@code pos} belongs to. */
    public static int freeSlots(final Level level, final BlockPos pos) {
        return LOADED ? RsmbacImpl.freeSlots(level, pos) : 0;
    }

    /** Puts a pattern into the structure's first free Pattern Storage slot; false if none takes it. */
    public static boolean place(final Level level, final BlockPos pos, final ItemStack pattern) {
        return LOADED && RsmbacImpl.place(level, pos, pattern);
    }
}
