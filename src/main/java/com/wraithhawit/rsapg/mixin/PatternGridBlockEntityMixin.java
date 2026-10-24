package com.wraithhawit.rsapg.mixin;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.upgrade.UpgradeContainer;
import com.refinedmods.refinedstorage.common.util.ContainerUtil;
import com.wraithhawit.rsapg.AdvancedGridHost;
import com.wraithhawit.rsapg.AdvancedGridState;
import com.wraithhawit.rsapg.AdvancedPatternGridBlock;
import com.wraithhawit.rsapg.GridUpgradeDestination;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Upgrade slots, runtime state, save data, drops and name for advanced grids. */
@Mixin(value = PatternGridBlockEntity.class, remap = false)
public abstract class PatternGridBlockEntityMixin implements AdvancedGridHost {
    @Unique
    private static final String RSAPG$TAG_UPGRADES = "rsapg_upgrades";
    /** 0.1.0 test builds kept one upgrade under this key. */
    @Unique
    private static final String RSAPG$TAG_OLD_UPGRADE = "rsapg_upgrade";
    @Unique
    private static final String RSAPG$TAG_AUTO_PLACE = "rsapg_auto_place";

    @Unique
    private UpgradeContainer rsapg$upgrades;
    @Unique
    private final AdvancedGridState rsapg$state = new AdvancedGridState();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void rsapg$init(final BlockPos pos, final BlockState state, final CallbackInfo ci) {
        rsapg$upgrades = new UpgradeContainer(GridUpgradeDestination.SLOTS, GridUpgradeDestination.ADVANCED_PATTERN_GRID,
            null);
        rsapg$upgrades.addListener(container -> ((BlockEntity) (Object) this).setChanged());
    }

    @Override
    public boolean rsapg$isAdvanced() {
        return ((BlockEntity) (Object) this).getBlockState().getBlock() instanceof AdvancedPatternGridBlock;
    }

    @Override
    public UpgradeContainer rsapg$upgrades() {
        return rsapg$upgrades;
    }

    @Override
    public AdvancedGridState rsapg$state() {
        return rsapg$state;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void rsapg$save(final CompoundTag tag, final HolderLookup.Provider provider, final CallbackInfo ci) {
        if (!rsapg$upgrades.isEmpty()) {
            tag.put(RSAPG$TAG_UPGRADES, ContainerUtil.write(rsapg$upgrades, provider));
        }
        if (rsapg$state.autoPlace) {
            tag.putBoolean(RSAPG$TAG_AUTO_PLACE, true);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void rsapg$load(final CompoundTag tag, final HolderLookup.Provider provider, final CallbackInfo ci) {
        rsapg$state.autoPlace = tag.getBoolean(RSAPG$TAG_AUTO_PLACE);
        rsapg$upgrades.clearContent();
        if (tag.contains(RSAPG$TAG_UPGRADES)) {
            ContainerUtil.read(tag.getCompound(RSAPG$TAG_UPGRADES), rsapg$upgrades, provider);
        } else if (tag.contains(RSAPG$TAG_OLD_UPGRADE)) {
            rsapg$upgrades.setItem(0, ItemStack.parse(provider, tag.getCompound(RSAPG$TAG_OLD_UPGRADE))
                .orElse(ItemStack.EMPTY));
        }
    }

    @Inject(method = "getDrops", at = @At("RETURN"))
    private void rsapg$dropUpgrades(final CallbackInfoReturnable<NonNullList<ItemStack>> cir) {
        for (int i = 0; i < rsapg$upgrades.getContainerSize(); ++i) {
            final ItemStack upgrade = rsapg$upgrades.getItem(i);
            if (!upgrade.isEmpty()) {
                cir.getReturnValue().add(upgrade.copy());
            }
        }
    }

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void rsapg$name(final CallbackInfoReturnable<Component> cir) {
        if (!rsapg$isAdvanced()) {
            return;
        }
        final Component custom = ((AbstractBaseNetworkNodeContainerBlockEntity<?>) (Object) this).getCustomName();
        cir.setReturnValue(custom != null
            ? custom
            : ((BlockEntity) (Object) this).getBlockState().getBlock().getName());
    }
}
