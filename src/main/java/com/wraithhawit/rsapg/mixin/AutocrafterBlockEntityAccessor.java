package com.wraithhawit.rsapg.mixin;

import com.refinedmods.refinedstorage.common.autocrafting.autocrafter.AutocrafterBlockEntity;
import com.refinedmods.refinedstorage.common.support.FilteredContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Used by /rsapg selftest only, to load a pattern into an autocrafter. */
@Mixin(value = AutocrafterBlockEntity.class, remap = false)
public interface AutocrafterBlockEntityAccessor {
    @Invoker("getPatternContainer")
    FilteredContainer rsapg$getPatternContainer();
}
