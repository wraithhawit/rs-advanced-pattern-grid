package com.wraithhawit.rsapg;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlock;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.content.BlockColorMap;
import com.refinedmods.refinedstorage.common.support.BaseBlockItem;
import com.refinedmods.refinedstorage.common.support.NetworkNodeBlockItem;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places Refined Storage's own {@link PatternGridBlockEntity}; being this block is what makes a
 * pattern grid "advanced".
 */
public class AdvancedPatternGridBlock extends PatternGridBlock {
    private static final Component HELP = Component.translatable("item.rsapg.advanced_pattern_grid.help");

    public AdvancedPatternGridBlock(final DyeColor color, final MutableComponent name) {
        super(color, name);
    }

    @Override
    public BlockColorMap<PatternGridBlock, BaseBlockItem> getBlockColorMap() {
        return ModBlocks.ADVANCED_PATTERN_GRID;
    }

    @Override
    public BaseBlockItem createBlockItem() {
        return new NetworkNodeBlockItem(this, HELP);
    }

    @Nullable
    @Override
    public <O extends BlockEntity> BlockEntityTicker<O> getTicker(final Level level,
                                                                  final BlockState blockState,
                                                                  final BlockEntityType<O> type) {
        final BlockEntityTicker<O> rsTicker = super.getTicker(level, blockState, type);
        if (level.isClientSide() || rsTicker == null) {
            return rsTicker;
        }
        return (tickLevel, pos, state, blockEntity) -> {
            rsTicker.tick(tickLevel, pos, state, blockEntity);
            if (blockEntity instanceof PatternGridBlockEntity grid) {
                AdvancedGridLogic.tick(grid);
            }
        };
    }
}
