package com.wraithhawit.rsapg;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.support.FilteredContainer;
import com.wraithhawit.rsapg.mixin.PatternGridBlockEntityAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EncodeSafetyTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        try (var loading = mockStatic(net.neoforged.fml.loading.LoadingModList.class)) {
            var mods = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(mods.getModFiles()).thenReturn(java.util.List.of());
            loading.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    private PatternGridBlockEntity grid(AdvancedGridState state) {
        var grid = mock(PatternGridBlockEntity.class,
            withSettings().extraInterfaces(AdvancedGridHost.class, PatternGridBlockEntityAccessor.class));
        when(((AdvancedGridHost) grid).rsapg$state()).thenReturn(state);
        when(grid.getLevel()).thenReturn(mock(Level.class));
        when(((PatternGridBlockEntityAccessor) grid).rsapg$getPatternType()).thenReturn(PatternType.CRAFTING);
        return grid;
    }

    @Test void failedEncodeDoesNotInspectOrMoveOldOutput() {
        var state = new AdvancedGridState();
        state.autoPlace = true;
        var grid = grid(state);
        AdvancedGridLogic.encodeAndPlace(grid, "test");
        verify((PatternGridBlockEntityAccessor) grid, never()).rsapg$getPatternOutput();
    }

    @Test void refusedHiddenInputStopsBeforeExtractingAnotherBlank() {
        var state = new AdvancedGridState();
        var grid = grid(state);
        var a = (PatternGridBlockEntityAccessor) grid;
        when(a.rsapg$createCraftingPattern()).thenReturn(new ItemStack(Items.PAPER));
        var network = mock(Network.class);
        var storage = mock(StorageNetworkComponent.class);
        when(grid.getNetworkForItem()).thenReturn(network);
        when(grid.isGridActive()).thenReturn(true);
        when(network.getComponent(StorageNetworkComponent.class)).thenReturn(storage);
        var input = mock(FilteredContainer.class);
        when(grid.getPatternInput()).thenReturn(input);
        when(input.getItem(0)).thenReturn(new ItemStack(Items.PAPER, 32));
        when(input.isEmpty()).thenReturn(false);
        assertFalse(AdvancedGridLogic.tryEncode(grid, "test"));
        assertEquals(GridStatus.NETWORK_FULL, state.encodeStatus);
        verify(input, never()).setItem(anyInt(), any());
        verify(storage, never()).extract(any(), anyLong(), any(Action.class), any());
    }

    @Test void pendingEncodeRejectsChangedRecipeAndMissingRecipe() {
        var state = new AdvancedGridState();
        state.pendingRecipe = new ItemStack(Items.PAPER);
        assertTrue(AdvancedGridLogic.samePendingRecipe(state, new ItemStack(Items.PAPER)));
        assertFalse(AdvancedGridLogic.samePendingRecipe(state, new ItemStack(Items.STONE)));
        assertFalse(AdvancedGridLogic.samePendingRecipe(state, null));
        state.cancelPending();
        assertFalse(AdvancedGridLogic.samePendingRecipe(state, new ItemStack(Items.PAPER)));
    }
}
