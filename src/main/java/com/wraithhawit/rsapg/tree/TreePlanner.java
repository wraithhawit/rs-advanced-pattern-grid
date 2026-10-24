package com.wraithhawit.rsapg.tree;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.wraithhawit.rsapg.RSAPG;
import com.wraithhawit.rsapg.api.TreeDumpApi;
import com.wraithhawit.rsapg.tree.PatternTargets.Kind;
import com.wraithhawit.rsapg.tree.PatternTargets.Target;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;

/**
 * Decides, for each recipe of an EMI tree, whether a pattern is needed and where it would go.
 *
 * <p>Rules (the user's, 2026-10-24):
 * <ul>
 *   <li>skip an output the network already has a pattern for (any recipe), and repeats in the tree;</li>
 *   <li>crafting, stonecutter and smithing patterns: an rsmbac structure first, then an autocrafter
 *   that feeds no machine, then a machine-facing autocrafter;</li>
 *   <li>processing patterns: an autocrafter whose machine is one EMI lists as a workstation for the
 *   recipe's category;</li>
 *   <li>nowhere fits: not encoded, reported instead.</li>
 * </ul>
 */
public final class TreePlanner {
    private TreePlanner() {
    }

    /** The assignment, in request order; {@link Assignment#target} is null unless PLACE. */
    public record Assignment(PlanLine line, @Nullable Target target) {
    }

    public static List<Assignment> plan(final ServerLevel level, final PatternGridBlockEntity grid,
                                        final List<RecipeRequest> requests) {
        final List<Assignment> out = new ArrayList<>(requests.size());
        final Network network = grid.getNetworkForItem();
        if (network == null || !grid.isGridActive()) {
            for (int i = 0; i < requests.size(); ++i) {
                out.add(new Assignment(new PlanLine(i, PlanLine.Status.NO_TARGET, "grid is not on an active network", ""), null));
            }
            return out;
        }
        final Set<ResourceKey> onNetwork = new HashSet<>(network.getComponent(AutocraftingNetworkComponent.class).getOutputs());
        final Set<ResourceKey> inThisTree = new HashSet<>();
        final List<Target> targets = PatternTargets.collect(level.getServer(), network);
        RSAPG.LOG.info("tree: planning {} recipes against {} pattern targets ({})", requests.size(), targets.size(), targets);

        for (int i = 0; i < requests.size(); ++i) {
            final RecipeRequest request = requests.get(i);
            final ResourceAmount primary = request.primaryOutput();
            if (primary == null) {
                out.add(new Assignment(new PlanLine(i, PlanLine.Status.UNSUPPORTED, "no output RS can hold", ""), null));
                continue;
            }
            if (onNetwork.contains(primary.resource())) {
                out.add(new Assignment(new PlanLine(i, PlanLine.Status.ALREADY_HAS, "network already has a pattern", ""), null));
                continue;
            }
            if (!inThisTree.add(primary.resource())) {
                out.add(new Assignment(new PlanLine(i, PlanLine.Status.DUPLICATE, "already in this tree", ""), null));
                continue;
            }
            final int tier = request.kind().internal() ? -1 : TreeDumpApi.requiredTier(level, request.recipeId());
            final Target target = choose(request, tier, targets);
            if (target == null) {
                out.add(new Assignment(new PlanLine(i, PlanLine.Status.NO_TARGET, noTargetReason(request, tier), ""), null));
                inThisTree.remove(primary.resource());
                continue;
            }
            target.free--;
            out.add(new Assignment(new PlanLine(i, PlanLine.Status.PLACE, target.name, target.location()), target));
        }
        return out;
    }

    /**
     * Where one freshly encoded pattern would go (the create button with auto-place on). Unlike a
     * tree plan, the network already making this output does not matter: the player asked for it.
     */
    @Nullable
    public static Target chooseFor(final ServerLevel level, final PatternGridBlockEntity grid,
                                   final RecipeRequest request) {
        final Network network = grid.getNetworkForItem();
        if (network == null || !grid.isGridActive()) {
            return null;
        }
        final int tier = request.kind().internal() ? -1 : TreeDumpApi.requiredTier(level, request.recipeId());
        return choose(request, tier, PatternTargets.collect(level.getServer(), network));
    }

    /**
     * Internal patterns: rsmbac, then free autocrafters, then machine-facing ones. Processing
     * patterns: a target whose machine EMI lists as a workstation and whose tier covers the recipe's;
     * the lowest such tier wins (unknown tiers count as last resort), a pattern buffer winning a tie.
     */
    @Nullable
    private static Target choose(final RecipeRequest request, final int requiredTier, final List<Target> targets) {
        if (request.kind().internal()) {
            for (final Kind kind : List.of(Kind.RSMBAC, Kind.FREE, Kind.MACHINE)) {
                for (final Target target : targets) {
                    if (target.kind == kind && target.free > 0 && target.holder == null) {
                        return target;
                    }
                }
            }
            return null;
        }
        Target best = null;
        for (final Target target : targets) {
            if (target.kind != Kind.MACHINE || target.free <= 0
                || target.machines.stream().noneMatch(request.workstations()::contains)) {
                continue;
            }
            if (requiredTier >= 0 && target.tier >= 0 && target.tier < requiredTier) {
                continue;
            }
            if (best == null || rank(target) < rank(best)) {
                best = target;
            }
        }
        return best;
    }

    /**
     * Lower is better: the lowest known tier first, and at the same tier a pattern holder (an RS
     * Pattern Buffer: GregTech's intended feed, inputs kept per pattern) before an autocrafter
     * facing a bus. Unknown tiers come last.
     */
    private static long rank(final Target target) {
        final long tier = target.tier < 0 ? Integer.MAX_VALUE : target.tier;
        return tier * 2 + (target.holder != null ? 0 : 1);
    }

    private static String noTargetReason(final RecipeRequest request, final int tier) {
        if (request.kind().internal()) {
            return "no autocrafter with a free slot";
        }
        if (request.workstations().isEmpty()) {
            return "EMI names no machine for " + request.label();
        }
        return tier >= 0
            ? "no free autocrafter facing a " + request.label() + " machine of tier " + tier + " or above"
            : "no free autocrafter facing a " + request.label() + " machine";
    }
}
