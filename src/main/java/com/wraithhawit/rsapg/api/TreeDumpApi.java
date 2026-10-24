package com.wraithhawit.rsapg.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Hooks other mods register so the EMI recipe-tree dump understands their machines (server side).
 * gttweaks registers GregTech's: machine tiers, multiblock parts standing in for their controller,
 * recipe voltage tiers, and RS Pattern Buffers as pattern holders.
 *
 * <p>Tiers are plain integers where higher means more capable (GregTech: 0 = ULV, 1 = LV, ...);
 * -1 means unknown. A pattern goes to the lowest known tier that is at least the recipe's tier.
 */
public final class TreeDumpApi {
    private static final List<MachineResolver> MACHINES = new CopyOnWriteArrayList<>();
    private static final List<RecipeTierResolver> RECIPE_TIERS = new CopyOnWriteArrayList<>();
    private static final List<PatternHolderResolver> HOLDERS = new CopyOnWriteArrayList<>();

    private TreeDumpApi() {
    }

    /**
     * What an autocrafter is feeding.
     *
     * @param ids  item ids matched against EMI's workstations for a recipe's category (a multiblock
     *             part answers with its controllers' ids)
     * @param tier how capable it is, or -1
     */
    public record MachineInfo(List<ResourceLocation> ids, int tier) {
    }

    @FunctionalInterface
    public interface MachineResolver {
        /** @return null if this resolver does not know the block entity */
        @Nullable
        MachineInfo resolve(ServerLevel level, BlockEntity machine);
    }

    @FunctionalInterface
    public interface RecipeTierResolver {
        /** @return the tier the recipe needs, or -1 if this resolver does not know the recipe */
        int requiredTier(ServerLevel level, ResourceLocation recipeId);
    }

    /** A block that holds patterns itself and feeds a machine (e.g. an RS Pattern Buffer). */
    public interface PatternHolder {
        String name();

        int freeSlots();

        MachineInfo machine();

        /** @return false if it no longer takes the pattern */
        boolean place(ItemStack pattern);
    }

    @FunctionalInterface
    public interface PatternHolderResolver {
        /** @return null if this resolver does not know the block entity */
        @Nullable
        PatternHolder resolve(ServerLevel level, BlockEntity patternProvider);
    }

    public static void registerMachineResolver(final MachineResolver resolver) {
        MACHINES.add(resolver);
    }

    public static void registerRecipeTierResolver(final RecipeTierResolver resolver) {
        RECIPE_TIERS.add(resolver);
    }

    public static void registerPatternHolderResolver(final PatternHolderResolver resolver) {
        HOLDERS.add(resolver);
    }

    @Nullable
    public static MachineInfo machine(final ServerLevel level, final BlockEntity machine) {
        for (final MachineResolver resolver : MACHINES) {
            final MachineInfo info = resolver.resolve(level, machine);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    public static int requiredTier(final ServerLevel level, @Nullable final ResourceLocation recipeId) {
        if (recipeId == null) {
            return -1;
        }
        for (final RecipeTierResolver resolver : RECIPE_TIERS) {
            final int tier = resolver.requiredTier(level, recipeId);
            if (tier >= 0) {
                return tier;
            }
        }
        return -1;
    }

    @Nullable
    public static PatternHolder holder(final ServerLevel level, final BlockEntity patternProvider) {
        for (final PatternHolderResolver resolver : HOLDERS) {
            final PatternHolder holder = resolver.resolve(level, patternProvider);
            if (holder != null) {
                return holder;
            }
        }
        return null;
    }
}
