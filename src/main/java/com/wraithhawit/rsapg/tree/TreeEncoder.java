package com.wraithhawit.rsapg.tree;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.support.FilteredContainer;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.wraithhawit.rsapg.AdvancedGridHost;
import com.wraithhawit.rsapg.AdvancedGridLogic;
import com.wraithhawit.rsapg.GridStatus;
import com.wraithhawit.rsapg.RSAPG;
import com.wraithhawit.rsapg.mixin.PatternGridBlockEntityAccessor;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The Encode button: plans the tree again (the network may have changed since the preview) and,
 * for every recipe the plan places, loads it into the grid through RS's own transfer methods (what
 * EMI's "+" calls), encodes it with the advanced grid's normal encode (blanks from the network,
 * fluid substitution, fuzzy check, gttweaks' circuit) and puts the pattern into its target.
 *
 * <p>The player's own grid recipe and output pattern are set aside first and put back afterwards.
 * Out of blanks stops the batch; what is done stays done, so running it again carries on.
 */
public final class TreeEncoder {
    private TreeEncoder() {
    }

    private record GridActor(String name) implements Actor {
        @Override
        public String getName() {
            return name;
        }
    }

    public static List<PlanLine> encode(final ServerPlayer player, final PatternGridBlockEntity grid,
                                        final List<RecipeRequest> requests) {
        final List<TreePlanner.Assignment> plan = TreePlanner.plan(player.serverLevel(), grid, requests);
        final List<PlanLine> results = new ArrayList<>(plan.size());
        final PatternGridBlockEntityAccessor acc = (PatternGridBlockEntityAccessor) grid;
        final FilteredContainer output = acc.rsapg$getPatternOutput();

        // Set the player's grid aside.
        final PatternType typeBefore = acc.rsapg$getPatternType();
        final boolean fuzzyBefore = acc.rsapg$isFuzzyMode();
        final ItemStack recipeBefore = AdvancedGridLogic.draft(grid, typeBefore);
        final ItemStack outputBefore = output.getItem(0).copy();
        output.setItem(0, ItemStack.EMPTY);

        String stopReason = null;
        int placed = 0;
        try {
            for (final TreePlanner.Assignment assignment : plan) {
                final PlanLine line = assignment.line();
                if (line.status() != PlanLine.Status.PLACE || assignment.target() == null) {
                    results.add(line);
                    continue;
                }
                if (stopReason != null) {
                    results.add(failed(line, stopReason));
                    continue;
                }
                final RecipeRequest request = requests.get(line.index());
                final String problem = encodeOne(player, grid, request);
                if (problem != null) {
                    if (problem.startsWith("!")) {
                        stopReason = problem.substring(1);
                        results.add(failed(line, stopReason));
                    } else {
                        results.add(failed(line, problem));
                    }
                    continue;
                }
                final ItemStack pattern = output.getItem(0).copy();
                output.setItem(0, ItemStack.EMPTY);
                if (PatternTargets.place(assignment.target(), pattern)) {
                    placed++;
                    results.add(new PlanLine(line.index(), PlanLine.Status.PLACED, line.target(), line.location()));
                } else {
                    final boolean stored = toNetwork(grid, pattern, player);
                    results.add(failed(line, stored
                        ? line.target() + " was full; the pattern went into network storage"
                        : line.target() + " was full and the network refused the pattern; it is in the grid's output"));
                    if (!stored) {
                        output.setItem(0, pattern);
                        stopReason = "the grid's output slot is holding a pattern that had nowhere to go";
                    }
                }
            }
        } finally {
            restore(grid, acc, output, typeBefore, fuzzyBefore, recipeBefore, outputBefore, player);
        }
        RSAPG.LOG.info("tree: {} encoded {} of {} planned patterns{}", player.getGameProfile().getName(), placed,
            plan.stream().filter(a -> a.line().status() == PlanLine.Status.PLACE).count(),
            stopReason != null ? "; stopped: " + stopReason : "");
        return results;
    }

    /**
     * Loads and encodes one recipe; the pattern is left in the output slot.
     *
     * @return null on success, a reason otherwise; a reason starting with '!' stops the batch
     */
    @Nullable
    private static String encodeOne(final ServerPlayer player, final PatternGridBlockEntity grid,
                                     final RecipeRequest request) {
        final PatternGridBlockEntityAccessor acc = (PatternGridBlockEntityAccessor) grid;
        final PatternType type = switch (request.kind()) {
            case CRAFTING -> PatternType.CRAFTING;
            case PROCESSING -> PatternType.PROCESSING;
            case STONECUTTER -> PatternType.STONECUTTER;
            case SMITHING -> PatternType.SMITHING_TABLE;
        };
        try {
            switch (request.kind()) {
                case CRAFTING -> acc.rsapg$transferCraftingRecipe(player, items(request.inputs()));
                case PROCESSING -> acc.rsapg$transferProcessingRecipe(player, mutable(request.inputs()),
                    mutable(request.outputs()));
                case STONECUTTER -> {
                    final List<List<ItemResource>> in = items(request.inputs());
                    final List<List<ItemResource>> out = items(request.outputs());
                    if (in.isEmpty() || in.getFirst().isEmpty() || out.isEmpty() || out.getFirst().isEmpty()) {
                        return "incomplete stonecutter recipe";
                    }
                    acc.rsapg$transferStonecutterRecipe(in.getFirst().getFirst(), out.getFirst().getFirst());
                }
                case SMITHING -> {
                    final List<List<ItemResource>> in = items(request.inputs());
                    if (in.size() != 3) {
                        return "incomplete smithing recipe";
                    }
                    acc.rsapg$transferSmithingTableRecipe(player, in.get(0), in.get(1), in.get(2));
                }
            }
        } catch (final RuntimeException e) {
            RSAPG.LOG.warn("tree: loading a {} recipe failed", request.label(), e);
            return "could not load the recipe into the grid";
        }
        if (acc.rsapg$getPatternType() != type || AdvancedGridLogic.draft(grid, type) == null) {
            return "RS could not build this recipe in the grid";
        }
        final AdvancedGridHost host = (AdvancedGridHost) grid;
        AdvancedGridLogic.encode(grid, player.getGameProfile().getName());
        if (!acc.rsapg$getPatternOutput().getItem(0).isEmpty()) {
            return null;
        }
        final GridStatus status = host.rsapg$state().shownStatus();
        // A batch never waits on a crafted blank; the upgrade keeps making spares for the next run.
        host.rsapg$state().cancelPending();
        return "!" + switch (status) {
            case CRAFTING_BLANK -> "out of blank patterns (more are being crafted; run it again)";
            case NO_BLANKS, NO_BLANK_PATTERN, MISSING_RESOURCES -> "out of blank patterns";
            case NO_NETWORK -> "the grid lost its network";
            default -> "encoding failed (" + status.name().toLowerCase() + ")";
        };
    }

    private static void restore(final PatternGridBlockEntity grid, final PatternGridBlockEntityAccessor acc,
                                final FilteredContainer output, final PatternType type, final boolean fuzzy,
                                @Nullable final ItemStack recipe, final ItemStack outputBefore,
                                final ServerPlayer player) {
        acc.rsapg$clear();
        if (recipe != null) {
            acc.rsapg$copyPattern(recipe);
        }
        acc.rsapg$setPatternType(type);
        acc.rsapg$setFuzzyMode(fuzzy);
        if (!outputBefore.isEmpty()) {
            if (output.getItem(0).isEmpty()) {
                output.setItem(0, outputBefore);
            } else if (!toNetwork(grid, outputBefore, player) && !player.getInventory().add(outputBefore)) {
                player.drop(outputBefore, false);
            }
        }
        grid.setChanged();
    }

    private static boolean toNetwork(final PatternGridBlockEntity grid, final ItemStack stack, final ServerPlayer player) {
        final Network network = grid.getNetworkForItem();
        if (network == null) {
            return false;
        }
        final long inserted = network.getComponent(StorageNetworkComponent.class).insert(ItemResource.ofItemStack(stack),
            stack.getCount(), Action.EXECUTE, new GridActor(player.getGameProfile().getName()));
        return inserted == stack.getCount();
    }

    private static PlanLine failed(final PlanLine line, final String why) {
        return new PlanLine(line.index(), PlanLine.Status.FAILED, why, line.location());
    }

    /** Mutable copies: RS's transfer sorts each slot's possibilities in place. */
    private static List<List<ResourceAmount>> mutable(final List<List<ResourceAmount>> slots) {
        final List<List<ResourceAmount>> copy = new ArrayList<>(slots.size());
        for (final List<ResourceAmount> slot : slots) {
            copy.add(new ArrayList<>(slot));
        }
        return copy;
    }

    private static List<List<ItemResource>> items(final List<List<ResourceAmount>> slots) {
        final List<List<ItemResource>> out = new ArrayList<>(slots.size());
        for (final List<ResourceAmount> slot : slots) {
            final List<ItemResource> items = new ArrayList<>(slot.size());
            for (final ResourceAmount amount : slot) {
                if (amount.resource() instanceof ItemResource item) {
                    items.add(item);
                }
            }
            out.add(items);
        }
        return out;
    }
}
