package com.wraithhawit.rsapg.mixin;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.support.FilteredContainer;
import com.refinedmods.refinedstorage.common.support.RecipeMatrixContainer;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = PatternGridBlockEntity.class, remap = false)
public interface PatternGridBlockEntityAccessor {
    @Invoker("createPattern")
    void rsapg$createPattern();

    @Invoker("getPatternOutput")
    FilteredContainer rsapg$getPatternOutput();

    @Invoker("getPatternType")
    PatternType rsapg$getPatternType();

    @Invoker("setPatternType")
    void rsapg$setPatternType(PatternType type);

    @Invoker("setFuzzyMode")
    void rsapg$setFuzzyMode(boolean fuzzyMode);

    @Invoker("getCraftingMatrix")
    RecipeMatrixContainer rsapg$getCraftingMatrix();

    @Invoker("isFuzzyMode")
    boolean rsapg$isFuzzyMode();

    @Invoker("clear")
    void rsapg$clear();

    @Invoker("copyPattern")
    void rsapg$copyPattern(ItemStack stack);

    // RS's EMI/JEI transfer entry points: load a recipe into the grid exactly as the "+" does.

    @Invoker("transferCraftingRecipe")
    void rsapg$transferCraftingRecipe(Player player, List<List<ItemResource>> recipe);

    @Invoker("transferProcessingRecipe")
    void rsapg$transferProcessingRecipe(Player player, List<List<ResourceAmount>> inputs,
                                        List<List<ResourceAmount>> outputs);

    @Invoker("transferStonecutterRecipe")
    void rsapg$transferStonecutterRecipe(ItemResource input, ItemResource selectedOutput);

    @Invoker("transferSmithingTableRecipe")
    void rsapg$transferSmithingTableRecipe(Player player, List<ItemResource> templates, List<ItemResource> bases,
                                           List<ItemResource> additions);

    // RS's builders for each pattern type: side-effect free, null when the recipe is not ready.

    @Nullable
    @Invoker("createCraftingPattern")
    ItemStack rsapg$createCraftingPattern();

    @Nullable
    @Invoker("createProcessingPattern")
    ItemStack rsapg$createProcessingPattern();

    @Nullable
    @Invoker("createStonecutterPattern")
    ItemStack rsapg$createStonecutterPattern();

    @Nullable
    @Invoker("createSmithingTablePattern")
    ItemStack rsapg$createSmithingTablePattern();
}
