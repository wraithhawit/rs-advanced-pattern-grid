package com.wraithhawit.rsapg;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlock;
import com.refinedmods.refinedstorage.common.content.BlockColorMap;
import com.refinedmods.refinedstorage.common.content.RegistryCallback;
import com.refinedmods.refinedstorage.common.support.BaseBlockItem;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, RSAPG.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, RSAPG.MOD_ID);

    /** Sixteen dyeable colors, like RS's own grids; shift + dye recolors within this set. */
    public static final BlockColorMap<PatternGridBlock, BaseBlockItem> ADVANCED_PATTERN_GRID = new BlockColorMap<>(
        AdvancedPatternGridBlock::new,
        RSAPG.id("advanced_pattern_grid"),
        Component.translatable("block.rsapg.advanced_pattern_grid"),
        DyeColor.LIGHT_BLUE
    );

    private ModBlocks() {
    }

    static void register(final IEventBus modBus) {
        ADVANCED_PATTERN_GRID.registerBlocks(callback(BLOCKS));
        ADVANCED_PATTERN_GRID.registerItems(callback(ITEMS));
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    private static <T> RegistryCallback<T> callback(final DeferredRegister<T> register) {
        return new RegistryCallback<>() {
            @Override
            public <R extends T> Supplier<R> register(final ResourceLocation id, final Supplier<R> value) {
                return register.register(id.getPath(), value);
            }
        };
    }
}
