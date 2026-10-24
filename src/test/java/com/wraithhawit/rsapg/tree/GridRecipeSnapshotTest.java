package com.wraithhawit.rsapg.tree;

import com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.support.RecipeMatrixContainer;
import com.wraithhawit.rsapg.mixin.PatternGridBlockEntityAccessor;
import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GridRecipeSnapshotTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        try (var loading = mockStatic(net.neoforged.fml.loading.LoadingModList.class)) {
            var mods = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(mods.getModFiles()).thenReturn(java.util.List.of());
            loading.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    @Test void restoresIncompleteCraftingAndOtherModeContents() throws Exception {
        var grid = mock(PatternGridBlockEntity.class, withSettings()
            .extraInterfaces(PatternGridBlockEntityAccessor.class).defaultAnswer(RETURNS_DEEP_STUBS));
        var a = (PatternGridBlockEntityAccessor) grid;
        var level = mock(Level.class);
        var provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        when(level.registryAccess()).thenReturn(provider);
        when(grid.getLevel()).thenReturn(level);
        var crafting = new RecipeMatrixContainer(null, 3, 3);
        var smithing = new RecipeMatrixContainer(null, 3, 1);
        crafting.setItem(7, new ItemStack(Items.STICK));
        smithing.setItem(1, new ItemStack(Items.DIAMOND));
        when(a.rsapg$getCraftingMatrix()).thenReturn(crafting);
        when(a.rsapg$getSmithingTableMatrix()).thenReturn(smithing);
        var stone = (Container) getter(grid, "getStonecutterInput");
        when(stone.getContainerSize()).thenReturn(1);
        when(stone.getItem(0)).thenReturn(ItemStack.EMPTY);
        for (String name : java.util.List.of("getProcessingInput", "getProcessingOutput")) {
            var container = (ResourceContainer) getter(grid, name);
            when(container.toTag(provider)).thenReturn(new CompoundTag());
        }
        var snapshot = new GridRecipeSnapshot(grid);
        crafting.clearContent();
        crafting.setItem(0, new ItemStack(Items.STONE));
        smithing.clearContent();
        snapshot.restore(grid);
        assertEquals(Items.STICK, crafting.getItem(7).getItem());
        assertTrue(crafting.getItem(0).isEmpty());
        assertEquals(Items.DIAMOND, smithing.getItem(1).getItem());
    }

    private static Object getter(PatternGridBlockEntity grid, String name) throws Exception {
        Method method = PatternGridBlockEntity.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(grid);
    }
}
