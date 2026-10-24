package com.wraithhawit.rsapg.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.GridData;
import com.wraithhawit.rsapg.AdvancedGridHost;
import com.wraithhawit.rsapg.AdvancedGridLogic;
import com.wraithhawit.rsapg.menu.AdvancedGridProperties;
import com.wraithhawit.rsapg.menu.AdvancedMenu;
import com.refinedmods.refinedstorage.common.support.containermenu.ClientProperty;
import com.refinedmods.refinedstorage.common.support.containermenu.ServerProperty;
import com.wraithhawit.rsapg.menu.SwitchableSlot;
import com.wraithhawit.rsapg.GridUpgradeDestination;
import com.wraithhawit.rsapg.menu.AdvancedUpgradeSlot;
import com.refinedmods.refinedstorage.common.upgrade.UpgradeContainer;
import java.util.function.IntSupplier;

import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The advanced grid's menu is RS's pattern grid menu with: the blank-pattern slot hidden and an
 * upgrade slot in its place, one synced int (advanced flag + status), and the create-pattern
 * button handed to {@link AdvancedGridLogic}. A plain pattern grid sees none of it (the int says
 * "not advanced", the extra slot stays inactive and accepts nothing).
 */
@Mixin(value = PatternGridContainerMenu.class, remap = false)
public abstract class PatternGridContainerMenuMixin extends AbstractGridContainerMenu implements AdvancedMenu {
    @Shadow
    @Nullable
    private PatternGridBlockEntity patternGrid;

    @Unique
    @Nullable
    private DataSlot rsapg$sync;
    @Unique
    @Nullable
    private DataSlot rsapg$plainBlanks;
    @Unique
    @Nullable
    private DataSlot rsapg$fluidBlanks;
    @Unique
    @Nullable
    private UpgradeContainer rsapg$clientUpgrades;

    protected PatternGridContainerMenuMixin(final MenuType<? extends AbstractGridContainerMenu> menuType,
                                            final int syncId, final Inventory playerInventory,
                                            final GridData gridData) {
        super(menuType, syncId, playerInventory, gridData);
    }

    @Override
    public int rsapg$syncValue() {
        if (patternGrid != null) {
            return ((AdvancedGridHost) patternGrid).rsapg$syncValue();
        }
        return rsapg$sync != null ? rsapg$sync.get() : 0;
    }

    @Override
    public int rsapg$blankCount(final boolean fluid) {
        final DataSlot slot = fluid ? rsapg$fluidBlanks : rsapg$plainBlanks;
        return slot != null ? slot.get() : 0;
    }

    @Override
    @Nullable
    public PatternGridBlockEntity rsapg$grid() {
        return patternGrid;
    }

    @Unique
    private boolean rsapg$advanced() {
        return AdvancedGridHost.isAdvanced(rsapg$syncValue());
    }

    @Unique
    private UpgradeContainer rsapg$upgrades() {
        if (patternGrid != null) {
            return ((AdvancedGridHost) patternGrid).rsapg$upgrades();
        }
        if (rsapg$clientUpgrades == null) {
            rsapg$clientUpgrades = new UpgradeContainer(GridUpgradeDestination.SLOTS,
                GridUpgradeDestination.ADVANCED_PATTERN_GRID, null);
        }
        return rsapg$clientUpgrades;
    }

    // Client constructor (from PatternGridData) and server constructor (from the block entity) add
    // the same data slots in the same order, so the client's copies follow the server's values.

    @Inject(
        method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
            + "Lcom/refinedmods/refinedstorage/common/autocrafting/patterngrid/PatternGridData;)V",
        at = @At("TAIL")
    )
    private void rsapg$initClient(final CallbackInfo ci) {
        rsapg$sync = addDataSlot(DataSlot.standalone());
        rsapg$plainBlanks = addDataSlot(DataSlot.standalone());
        rsapg$fluidBlanks = addDataSlot(DataSlot.standalone());
        registerProperty(new ClientProperty<>(AdvancedGridProperties.AUTO_PLACE, false));
    }

    @Inject(
        method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
            + "Lcom/refinedmods/refinedstorage/common/autocrafting/patterngrid/PatternGridBlockEntity;)V",
        at = @At("TAIL")
    )
    private void rsapg$initServer(final CallbackInfo ci) {
        rsapg$sync = addDataSlot(rsapg$serverSlot(this::rsapg$syncValue));
        rsapg$plainBlanks = addDataSlot(rsapg$serverSlot(() -> rsapg$serverBlankCount(false)));
        rsapg$fluidBlanks = addDataSlot(rsapg$serverSlot(() -> rsapg$serverBlankCount(true)));
        final PatternGridBlockEntity grid = patternGrid;
        registerProperty(new ServerProperty<>(AdvancedGridProperties.AUTO_PLACE,
            () -> grid != null && ((AdvancedGridHost) grid).rsapg$state().autoPlace,
            value -> {
                if (grid != null) {
                    ((AdvancedGridHost) grid).rsapg$state().autoPlace = value;
                    grid.setChanged();
                }
            }));
    }

    @Unique
    private int rsapg$serverBlankCount(final boolean fluid) {
        return patternGrid != null && ((AdvancedGridHost) patternGrid).rsapg$isAdvanced()
            ? AdvancedGridLogic.blankCount(patternGrid, fluid)
            : 0;
    }

    @Unique
    private static DataSlot rsapg$serverSlot(final IntSupplier value) {
        return new DataSlot() {
            @Override
            public int get() {
                return value.getAsInt();
            }

            @Override
            public void set(final int ignored) {
            }
        };
    }

    /** RS's blank-pattern input slot: hidden on an advanced grid. */
    @WrapOperation(
        method = "addPatternSlots",
        at = @At(
            value = "INVOKE",
            target = "Lcom/refinedmods/refinedstorage/common/autocrafting/patterngrid/PatternGridContainerMenu;"
                + "addSlot(Lnet/minecraft/world/inventory/Slot;)Lnet/minecraft/world/inventory/Slot;",
            ordinal = 0
        )
    )
    private Slot rsapg$hideBlankSlot(final PatternGridContainerMenu menu, final Slot slot,
                                     final Operation<Slot> original) {
        return original.call(menu, new SwitchableSlot(slot, () -> !rsapg$advanced()));
    }

    /** The upgrade panel's slots, right of the grid like Refined Wireless Upgrades' panel. */
    @Inject(method = "addPatternSlots", at = @At("TAIL"))
    private void rsapg$addUpgradeSlots(final int playerInventoryY, final CallbackInfo ci) {
        final UpgradeContainer upgrades = rsapg$upgrades();
        for (int i = 0; i < upgrades.getContainerSize(); ++i) {
            addSlot(new AdvancedUpgradeSlot(upgrades, i, this::rsapg$advanced));
        }
        if (patternGrid != null && ((AdvancedGridHost) patternGrid).rsapg$isAdvanced()) {
            transferManager.addBiTransfer(playerInventory, upgrades);
        }
    }

    /** The button needs a ready recipe only; blanks come from the network. */
    @ModifyExpressionValue(
        method = "canCreatePattern",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 0)
    )
    private boolean rsapg$noBlankSlotNeeded(final boolean inputEmpty) {
        return inputEmpty && !rsapg$advanced();
    }

    @Inject(method = "createPattern", at = @At("HEAD"), cancellable = true)
    private void rsapg$createPattern(final CallbackInfo ci) {
        if (patternGrid != null && ((AdvancedGridHost) patternGrid).rsapg$isAdvanced()) {
            AdvancedGridLogic.encodeAndPlace(patternGrid, playerInventory.player.getGameProfile().getName());
            ci.cancel();
        }
    }
}
