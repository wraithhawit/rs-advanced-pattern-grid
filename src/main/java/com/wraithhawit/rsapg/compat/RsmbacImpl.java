package com.wraithhawit.rsapg.compat;

import com.wraithhawit.rsmbac.block.ControllerBlockEntity;
import com.wraithhawit.rsmbac.menu.StructurePatterns;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Only loaded when rsmbac is installed; go through {@link Rsmbac}. */
final class RsmbacImpl {
    private RsmbacImpl() {
    }

    static boolean isController(final BlockEntity be) {
        return be instanceof ControllerBlockEntity;
    }

    static int freeSlots(final Level level, final BlockPos pos) {
        final StructurePatterns patterns = StructurePatterns.of(level, pos);
        int free = 0;
        for (int i = 0; i < patterns.getContainerSize(); ++i) {
            if (patterns.getItem(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    static boolean place(final Level level, final BlockPos pos, final ItemStack pattern) {
        final StructurePatterns patterns = StructurePatterns.of(level, pos);
        if (!patterns.accepts(pattern)) {
            return false;
        }
        for (int i = 0; i < patterns.getContainerSize(); ++i) {
            if (patterns.getItem(i).isEmpty() && patterns.canPlaceItem(i, pattern)) {
                patterns.setItem(i, pattern);
                patterns.setChanged();
                return true;
            }
        }
        return false;
    }
}
