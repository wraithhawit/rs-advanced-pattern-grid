package com.wraithhawit.rsapg.tree;

import com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.util.ContainerUtil;
import com.wraithhawit.rsapg.mixin.PatternGridBlockEntityAccessor;
import java.lang.reflect.Method;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;

/** Saves raw ghost slots, including unfinished recipes, without reloading the block entity. */
final class GridRecipeSnapshot {
    // These two RS getter return types are package-private, so Java cannot name them in an invoker.
    private static final Method PROCESSING_INPUT = getter("getProcessingInput");
    private static final Method PROCESSING_OUTPUT = getter("getProcessingOutput");
    private static final Method STONECUTTER = getter("getStonecutterInput");
    private final CompoundTag crafting, smithing, stone, inputs, outputs;
    private final PatternType type;
    private final boolean fuzzy;
    private final int selected;

    GridRecipeSnapshot(final PatternGridBlockEntity grid) {
        final var a = (PatternGridBlockEntityAccessor) grid;
        final var provider = grid.getLevel().registryAccess();
        crafting = ContainerUtil.write(a.rsapg$getCraftingMatrix(), provider);
        smithing = ContainerUtil.write(a.rsapg$getSmithingTableMatrix(), provider);
        stone = ContainerUtil.write((Container) read(STONECUTTER, grid), provider);
        inputs = ((ResourceContainer) read(PROCESSING_INPUT, grid)).toTag(provider);
        outputs = ((ResourceContainer) read(PROCESSING_OUTPUT, grid)).toTag(provider);
        type = a.rsapg$getPatternType();
        fuzzy = a.rsapg$isFuzzyMode();
        selected = a.rsapg$getStonecutterSelectedRecipe();
    }

    void restore(final PatternGridBlockEntity grid) {
        final var a = (PatternGridBlockEntityAccessor) grid;
        final var provider = grid.getLevel().registryAccess();
        restoreItems(crafting, a.rsapg$getCraftingMatrix(), provider);
        restoreItems(smithing, a.rsapg$getSmithingTableMatrix(), provider);
        restoreItems(stone, (Container) read(STONECUTTER, grid), provider);
        restoreResources(inputs, (ResourceContainer) read(PROCESSING_INPUT, grid), provider);
        restoreResources(outputs, (ResourceContainer) read(PROCESSING_OUTPUT, grid), provider);
        a.rsapg$setStonecutterSelectedRecipe(selected);
        a.rsapg$setPatternType(type);
        a.rsapg$setFuzzyMode(fuzzy);
        a.rsapg$getCraftingMatrix().changed();
        a.rsapg$getSmithingTableMatrix().changed();
        grid.setChanged();
    }

    private static void restoreItems(final CompoundTag tag, final Container container,
                                     final HolderLookup.Provider provider) {
        container.clearContent();
        ContainerUtil.read(tag, container, provider);
    }

    private static void restoreResources(final CompoundTag tag, final ResourceContainer container,
                                         final HolderLookup.Provider provider) {
        container.clear();
        container.fromTag(tag, provider);
    }

    private static Method getter(final String name) {
        try {
            final Method method = PatternGridBlockEntity.class.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported RS recipe getter: " + name, e);
        }
    }

    private static Object read(final Method method, final PatternGridBlockEntity grid) {
        try {
            return method.invoke(grid);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read recipe container", e);
        }
    }
}
