package com.wraithhawit.rsapg;

import com.refinedmods.refinedstorage.api.autocrafting.Ingredient;
import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent.EnsureResult;
import com.refinedmods.refinedstorage.api.network.impl.autocrafting.TimeoutableCancellationToken;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.autocrafting.CraftingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.content.Items;
import com.refinedmods.refinedstorage.common.support.FilteredContainer;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.wraithhawit.rsapg.compat.Rfs;
import com.wraithhawit.rsapg.mixin.PatternGridBlockEntityAccessor;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Server-side behaviour of the advanced grid.
 *
 * <p>Encoding: the grid takes a blank from the network, puts it in RS's (hidden) pattern input
 * slot and lets RS's own {@code createPattern} run, so RS and Refined Fluid Substitution encode
 * exactly as they do in a plain pattern grid. Afterwards a staged blank RS did not use goes back to
 * the network.
 */
public final class AdvancedGridLogic {
    private static final int PENDING_CHECK_TICKS = 20;
    /** How often a waiting encode re-asks for its blank (the job may have been cancelled). */
    private static final int PENDING_ENSURE_TICKS = 600;
    private static final int SPARE_CHECK_TICKS = 100;
    /** After a spare could not be planned, wait this long before planning again. */
    private static final int SPARE_BACKOFF_TICKS = 600;
    /** Planning budget for background spare requests; RS's own requests allow 5 s. */
    private static final long SPARE_PLAN_BUDGET_MS = 250;
    private static final int BUCKETS_PER_FLUID_PATTERN = 2;

    /** For the log line only; set by take() on the server thread. */
    private static String lastFluidSource = "fluid blank";

    private AdvancedGridLogic() {
    }

    private record GridActor(String name) implements Actor {
        @Override
        public String getName() {
            return name;
        }
    }

    private static AdvancedGridHost host(final PatternGridBlockEntity grid) {
        return (AdvancedGridHost) grid;
    }

    private static PatternGridBlockEntityAccessor acc(final PatternGridBlockEntity grid) {
        return (PatternGridBlockEntityAccessor) grid;
    }

    private static ItemResource plainBlank() {
        return new ItemResource(Items.INSTANCE.getPattern());
    }

    @Nullable
    private static ItemResource fluidBlank() {
        return Rfs.isLoaded() ? ItemResource.ofItemStack(Rfs.blank()) : null;
    }

    private static ItemResource bucket() {
        return new ItemResource(net.minecraft.world.item.Items.BUCKET);
    }

    // ---------------------------------------------------------------- encode

    /** The create-pattern button of an advanced grid. */
    public static void encode(final PatternGridBlockEntity grid, final String requester) {
        final AdvancedGridState state = host(grid).rsapg$state();
        state.requester = requester;
        final Level level = grid.getLevel();
        if (level == null) {
            return;
        }
        final PatternType type = acc(grid).rsapg$getPatternType();
        final ItemStack draft = draft(grid, type);
        if (draft == null) {
            // RS would do nothing either; the screen only enables the button for a ready recipe.
            state.pendingType = null;
            return;
        }
        final Network network = grid.getNetworkForItem();
        if (network == null || !grid.isGridActive()) {
            setEncodeStatus(grid, GridStatus.NO_NETWORK);
            return;
        }
        final StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
        final Actor actor = new GridActor(requester);
        flushInput(grid, storage, actor);

        final boolean wantFluid = type == PatternType.CRAFTING && Rfs.qualifies(draft, level);
        final FilteredContainer output = acc(grid).rsapg$getPatternOutput();
        final ItemStack current = output.getItem(0);

        if (!current.isEmpty() && Rfs.isFluidPattern(current) == wantFluid) {
            // Same kind already in the output slot: RS re-encodes it without using a blank.
            runRs(grid, level, storage, actor);
            finish(grid, GridStatus.NONE, "re-encoded the " + kind(wantFluid) + " pattern in the output slot", type);
            return;
        }

        ItemStack blank = take(storage, wantFluid, actor);
        String source = blank.isEmpty() ? null : (wantFluid ? lastFluidSource : "blank");
        boolean fallback = false;
        if (blank.isEmpty() && wantFluid) {
            if (!current.isEmpty()) {
                runRs(grid, level, storage, actor);
                finish(grid, GridStatus.PLAIN_FALLBACK, "no fluid blank or buckets; re-encoded the plain pattern", type);
                return;
            }
            blank = take(storage, false, actor);
            fallback = !blank.isEmpty();
            source = "plain blank (no fluid blank or buckets)";
        }
        if (blank.isEmpty()) {
            noBlank(grid, network, state, wantFluid, actor, type);
            return;
        }
        if (!current.isEmpty()) {
            // Wrong kind in the output slot: hand it back as a blank of its own kind.
            final ItemStack cleared = new ItemStack(current.getItem());
            if (insert(storage, cleared, actor) < 1) {
                if (insert(storage, blank, actor) < 1) {
                    grid.getPatternInput().setItem(0, blank);
                }
                setEncodeStatus(grid, GridStatus.NETWORK_FULL);
                RSAPG.LOG.info("{}: network full, could not return the {} pattern from the output slot",
                    where(grid), kind(Rfs.isFluidPattern(current)));
                return;
            }
            output.setItem(0, ItemStack.EMPTY);
        }
        grid.getPatternInput().setItem(0, blank);
        runRs(grid, level, storage, actor);
        finish(grid, fallback ? GridStatus.PLAIN_FALLBACK : GridStatus.NONE, "used a " + source, type);
        // Craft ahead: top the spares back up soon instead of waiting for the next check.
        state.nextSpareTick = Math.min(state.nextSpareTick, state.ticks + 1);
    }

    /**
     * The create button: encode, then, with the auto-place toggle on, put the pattern where the
     * recipe-tree dump would (rsmbac / free autocrafters for internal patterns; for processing, the
     * machine of the recipe EMI's "+" last transferred, if the pattern still makes those outputs),
     * else into network storage. The player is told where it went on the action bar.
     */
    public static void encodeAndPlace(final PatternGridBlockEntity grid, final String requester) {
        encode(grid, requester);
        final AdvancedGridState state = host(grid).rsapg$state();
        final FilteredContainer output = acc(grid).rsapg$getPatternOutput();
        final ItemStack pattern = output.getItem(0);
        if (!state.autoPlace || pattern.isEmpty() || !(grid.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        final net.minecraft.server.level.ServerPlayer player = level.getServer().getPlayerList().getPlayerByName(requester);
        final com.wraithhawit.rsapg.tree.RecipeRequest request = describe(pattern, state.lastTransfer);
        final com.wraithhawit.rsapg.tree.PatternTargets.Target target =
            request == null ? null : com.wraithhawit.rsapg.tree.TreePlanner.chooseFor(level, grid, request);
        if (target != null && com.wraithhawit.rsapg.tree.PatternTargets.place(target, pattern.copy())) {
            output.setItem(0, ItemStack.EMPTY);
            tell(player, Component.translatable("gui.rsapg.auto_place.placed", target.name));
            RSAPG.LOG.info("{}: auto-placed {} in {} at {}", where(grid), describe(pattern), target.name, target.location());
            return;
        }
        final String why = request != null && !request.kind().internal() && request.workstations().isEmpty()
            ? "gui.rsapg.auto_place.unknown_machine" : "gui.rsapg.auto_place.no_target";
        final Network network = grid.getNetworkForItem();
        if (network != null && insert(network.getComponent(StorageNetworkComponent.class), pattern, new GridActor(requester))
            == pattern.getCount()) {
            output.setItem(0, ItemStack.EMPTY);
            tell(player, Component.translatable("gui.rsapg.auto_place.stored", Component.translatable(why)));
            RSAPG.LOG.info("{}: auto-place found no target ({}); {} went into network storage", where(grid), why, describe(pattern));
        } else {
            tell(player, Component.translatable("gui.rsapg.auto_place.kept", Component.translatable(why)));
        }
    }

    /** The encoded pattern as a one-recipe request, borrowing the machine from the last EMI transfer if it matches. */
    @Nullable
    private static com.wraithhawit.rsapg.tree.RecipeRequest describe(final ItemStack pattern,
                                                                     @Nullable final com.wraithhawit.rsapg.tree.RecipeRequest transfer) {
        final com.refinedmods.refinedstorage.common.autocrafting.PatternState state =
            pattern.get(DataComponents.INSTANCE.getPatternState());
        if (state == null) {
            return null;
        }
        final com.wraithhawit.rsapg.tree.RecipeRequest.Kind kind = switch (state.type()) {
            case CRAFTING -> com.wraithhawit.rsapg.tree.RecipeRequest.Kind.CRAFTING;
            case PROCESSING -> com.wraithhawit.rsapg.tree.RecipeRequest.Kind.PROCESSING;
            case STONECUTTER -> com.wraithhawit.rsapg.tree.RecipeRequest.Kind.STONECUTTER;
            case SMITHING_TABLE -> com.wraithhawit.rsapg.tree.RecipeRequest.Kind.SMITHING;
        };
        if (kind.internal()) {
            return new com.wraithhawit.rsapg.tree.RecipeRequest(kind, "", List.of(), List.of(), List.of(), null);
        }
        final com.refinedmods.refinedstorage.common.autocrafting.ProcessingPatternState processing =
            pattern.get(DataComponents.INSTANCE.getProcessingPatternState());
        final java.util.Set<ResourceKey> made = new java.util.HashSet<>();
        if (processing != null) {
            processing.outputs().forEach(o -> o.ifPresent(amount -> made.add(amount.resource())));
        }
        if (transfer != null && transfer.kind() == kind) {
            final java.util.Set<ResourceKey> transferred = new java.util.HashSet<>();
            for (final List<com.refinedmods.refinedstorage.api.resource.ResourceAmount> out : transfer.outputs()) {
                if (!out.isEmpty()) {
                    transferred.add(out.getFirst().resource());
                }
            }
            if (!made.isEmpty() && transferred.containsAll(made) && made.containsAll(transferred)) {
                return new com.wraithhawit.rsapg.tree.RecipeRequest(kind, transfer.label(), List.of(), List.of(),
                    transfer.workstations(), transfer.recipeId());
            }
        }
        return new com.wraithhawit.rsapg.tree.RecipeRequest(kind, "", List.of(), List.of(), List.of(), null);
    }

    private static void tell(@Nullable final net.minecraft.server.level.ServerPlayer player, final Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    private static void runRs(final PatternGridBlockEntity grid, final Level level,
                              final StorageNetworkComponent storage, final Actor actor) {
        acc(grid).rsapg$createPattern();
        final ItemStack result = acc(grid).rsapg$getPatternOutput().getItem(0);
        if (dropUselessFuzzy(result, level)) {
            grid.setChanged();
        }
        flushInput(grid, storage, actor);
    }

    private static void finish(final PatternGridBlockEntity grid, final GridStatus status, final String what,
                               final PatternType type) {
        host(grid).rsapg$state().pendingType = null;
        setEncodeStatus(grid, status);
        final ItemStack result = acc(grid).rsapg$getPatternOutput().getItem(0);
        RSAPG.LOG.info("{}: encoded {} pattern for {}: {} -> {}", where(grid), type.name().toLowerCase(),
            host(grid).rsapg$state().requester, what, describe(result));
    }

    private static void noBlank(final PatternGridBlockEntity grid, final Network network,
                                final AdvancedGridState state, final boolean wantFluid, final Actor actor,
                                final PatternType type) {
        if (!hasUpgrade(grid)) {
            state.pendingType = null;
            setEncodeStatus(grid, GridStatus.NO_BLANKS);
            return;
        }
        final AutocraftingNetworkComponent autocrafting = network.getComponent(AutocraftingNetworkComponent.class);
        final ItemResource fluid = fluidBlank();
        final boolean craftFluid = wantFluid && fluid != null && !autocrafting.getPatternsByOutput(fluid).isEmpty();
        final ItemResource target = craftFluid ? fluid : plainBlank();
        if (autocrafting.getPatternsByOutput(target).isEmpty()) {
            state.pendingType = null;
            setEncodeStatus(grid, GridStatus.NO_BLANK_PATTERN);
            return;
        }
        final EnsureResult result = autocrafting.ensureTask(target, 1, actor, new TimeoutableCancellationToken());
        RSAPG.LOG.info("{}: no blank for a {} pattern, asked for 1 {}: {}", where(grid), kind(wantFluid),
            craftFluid ? "fluid blank" : "blank", result);
        if (result == EnsureResult.MISSING_RESOURCES) {
            state.pendingType = null;
            setEncodeStatus(grid, GridStatus.MISSING_RESOURCES);
            return;
        }
        state.pendingType = type;
        state.pendingFluid = craftFluid;
        state.nextPendingEnsureTick = state.ticks + PENDING_ENSURE_TICKS;
        setEncodeStatus(grid, GridStatus.CRAFTING_BLANK);
    }

    /** One blank of the wanted kind out of the network, or empty. */
    private static ItemStack take(final StorageNetworkComponent storage, final boolean fluid, final Actor actor) {
        if (!fluid) {
            return extractOne(storage, plainBlank(), actor) ? new ItemStack(Items.INSTANCE.getPattern()) : ItemStack.EMPTY;
        }
        final ItemResource fluidBlank = fluidBlank();
        if (fluidBlank == null) {
            return ItemStack.EMPTY;
        }
        if (extractOne(storage, fluidBlank, actor)) {
            lastFluidSource = "stocked fluid blank";
            return Rfs.blank();
        }
        // RFS's own recipe: a blank pattern between two buckets.
        final ItemResource plain = plainBlank();
        final ItemResource bucket = bucket();
        if (storage.extract(plain, 1, Action.SIMULATE, actor) == 1
            && storage.extract(bucket, BUCKETS_PER_FLUID_PATTERN, Action.SIMULATE, actor) == BUCKETS_PER_FLUID_PATTERN) {
            final long plainTaken = storage.extract(plain, 1, Action.EXECUTE, actor);
            final long bucketsTaken = storage.extract(bucket, BUCKETS_PER_FLUID_PATTERN, Action.EXECUTE, actor);
            if (plainTaken == 1 && bucketsTaken == BUCKETS_PER_FLUID_PATTERN) {
                lastFluidSource = "fluid blank made from a blank + 2 buckets";
                return Rfs.blank();
            }
            // Storage changed between simulate and execute: put back what was taken.
            storage.insert(plain, plainTaken, Action.EXECUTE, actor);
            storage.insert(bucket, bucketsTaken, Action.EXECUTE, actor);
        }
        return ItemStack.EMPTY;
    }

    private static boolean extractOne(final StorageNetworkComponent storage, final ResourceKey resource,
                                      final Actor actor) {
        if (storage.extract(resource, 1, Action.SIMULATE, actor) != 1) {
            return false;
        }
        return storage.extract(resource, 1, Action.EXECUTE, actor) == 1;
    }

    private static long insert(final StorageNetworkComponent storage, final ItemStack stack, final Actor actor) {
        return storage.insert(ItemResource.ofItemStack(stack), stack.getCount(), Action.EXECUTE, actor);
    }

    /** Anything in the hidden input slot (a staged blank, shift-clicked patterns) goes to the network. */
    private static void flushInput(final PatternGridBlockEntity grid, final StorageNetworkComponent storage,
                                   final Actor actor) {
        final ItemStack staged = grid.getPatternInput().getItem(0);
        if (staged.isEmpty()) {
            return;
        }
        final long inserted = insert(storage, staged, actor);
        if (inserted <= 0) {
            return;
        }
        final ItemStack rest = staged.copyWithCount(staged.getCount() - (int) inserted);
        grid.getPatternInput().setItem(0, rest.isEmpty() ? ItemStack.EMPTY : rest);
    }

    /** RS's builder for the current type; null when the recipe is not ready. */
    @Nullable
    public static ItemStack draft(final PatternGridBlockEntity grid, final PatternType type) {
        final PatternGridBlockEntityAccessor a = acc(grid);
        return switch (type) {
            case CRAFTING -> a.rsapg$createCraftingPattern();
            case PROCESSING -> a.rsapg$createProcessingPattern();
            case STONECUTTER -> a.rsapg$createStonecutterPattern();
            case SMITHING_TABLE -> a.rsapg$createSmithingTablePattern();
        };
    }

    // ---------------------------------------------------------------- fuzzy

    /**
     * Turns fuzzy mode off on an encoded crafting pattern when it changes nothing: every slot's
     * fuzzy alternatives are just the item already in it. Compares RS's own resolved patterns.
     *
     * @return true if the stack was changed
     */
    static boolean dropUselessFuzzy(final ItemStack pattern, final Level level) {
        final CraftingPatternState fuzzy = pattern.get(DataComponents.INSTANCE.getCraftingPatternState());
        if (fuzzy == null || !fuzzy.fuzzyMode()) {
            return false;
        }
        final CraftingPatternState exact = new CraftingPatternState(false, fuzzy.input());
        final ItemStack exactStack = pattern.copy();
        exactStack.set(DataComponents.INSTANCE.getCraftingPatternState(), exact);
        final Optional<Pattern> fuzzyPattern = Items.INSTANCE.getPattern().getPattern(pattern, level);
        final Optional<Pattern> exactPattern = Items.INSTANCE.getPattern().getPattern(exactStack, level);
        if (fuzzyPattern.isEmpty() || exactPattern.isEmpty()
            || !sameInputs(fuzzyPattern.get().layout().ingredients(), exactPattern.get().layout().ingredients())) {
            return false;
        }
        pattern.set(DataComponents.INSTANCE.getCraftingPatternState(), exact);
        RSAPG.LOG.info("fuzzy mode changes nothing for {}, encoded without it", describe(pattern));
        return true;
    }

    static boolean sameInputs(final List<Ingredient> a, final List<Ingredient> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); ++i) {
            if (a.get(i).amount() != b.get(i).amount()
                || !new HashSet<>(a.get(i).inputs()).equals(new HashSet<>(b.get(i).inputs()))) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- tick

    public static void tick(final PatternGridBlockEntity grid) {
        final AdvancedGridHost host = host(grid);
        if (!host.rsapg$isAdvanced()) {
            return;
        }
        final AdvancedGridState state = host.rsapg$state();
        state.ticks++;
        if (state.ticks % PENDING_CHECK_TICKS != 0 && state.ticks < state.nextSpareTick) {
            return;
        }
        final Network network = grid.getNetworkForItem();
        if (network == null || !grid.isGridActive()) {
            return;
        }
        final StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
        final Actor actor = new GridActor(state.requester);
        if (state.ticks % PENDING_CHECK_TICKS == 0) {
            flushInput(grid, storage, actor);
            if (state.pendingType != null) {
                tickPending(grid, network, storage, state, actor);
            }
        }
        if (state.ticks >= state.nextSpareTick) {
            state.nextSpareTick = state.ticks + SPARE_CHECK_TICKS;
            stockSpares(grid, network, storage, state, actor);
        }
    }

    private static void tickPending(final PatternGridBlockEntity grid, final Network network,
                                    final StorageNetworkComponent storage, final AdvancedGridState state,
                                    final Actor actor) {
        final PatternType type = acc(grid).rsapg$getPatternType();
        if (type != state.pendingType || draft(grid, type) == null || !hasUpgrade(grid)) {
            RSAPG.LOG.info("{}: dropped the waiting encode (recipe, pattern type or upgrade changed)", where(grid));
            state.pendingType = null;
            if (state.encodeStatus == GridStatus.CRAFTING_BLANK) {
                setEncodeStatus(grid, GridStatus.NONE);
            }
            return;
        }
        if (blankArrived(storage, state.pendingFluid, actor)) {
            encodeAndPlace(grid, state.requester);
            return;
        }
        if (state.ticks >= state.nextPendingEnsureTick) {
            final Level level = grid.getLevel();
            final ItemStack draft = draft(grid, type);
            final boolean wantFluid = level != null && draft != null && type == PatternType.CRAFTING
                && Rfs.qualifies(draft, level);
            noBlank(grid, network, state, wantFluid, actor, type);
        }
    }

    private static boolean blankArrived(final StorageNetworkComponent storage, final boolean fluid,
                                        final Actor actor) {
        final ItemResource plain = plainBlank();
        if (!fluid) {
            return storage.extract(plain, 1, Action.SIMULATE, actor) == 1;
        }
        final ItemResource fluidBlank = fluidBlank();
        return fluidBlank != null && storage.extract(fluidBlank, 1, Action.SIMULATE, actor) == 1;
    }

    /**
     * Craft ahead: with the upgrade in, keep one plain blank in the network, and one fluid blank
     * when the network has a pattern for them.
     */
    private static void stockSpares(final PatternGridBlockEntity grid, final Network network,
                                    final StorageNetworkComponent storage, final AdvancedGridState state,
                                    final Actor actor) {
        if (!hasUpgrade(grid)) {
            setSpareStatus(grid, GridStatus.NONE);
            return;
        }
        final AutocraftingNetworkComponent autocrafting = network.getComponent(AutocraftingNetworkComponent.class);
        GridStatus status = keepOne(grid, autocrafting, storage, plainBlank(), "blank", actor, true);
        final ItemResource fluid = fluidBlank();
        if (fluid != null && !autocrafting.getPatternsByOutput(fluid).isEmpty()) {
            final GridStatus fluidStatus = keepOne(grid, autocrafting, storage, fluid, "fluid blank", actor, false);
            if (status == GridStatus.NONE) {
                status = fluidStatus;
            }
        }
        if (status == GridStatus.SPARE_MISSING_RESOURCES) {
            state.nextSpareTick = state.ticks + SPARE_BACKOFF_TICKS;
        }
        setSpareStatus(grid, status);
    }

    private static GridStatus keepOne(final PatternGridBlockEntity grid, final AutocraftingNetworkComponent autocrafting,
                                      final StorageNetworkComponent storage, final ItemResource blank,
                                      final String name, final Actor actor, final boolean warnWithoutPattern) {
        if (storage.get(blank) >= 1) {
            return GridStatus.NONE;
        }
        if (autocrafting.getPatternsByOutput(blank).isEmpty()) {
            return warnWithoutPattern ? GridStatus.SPARE_NO_BLANK_PATTERN : GridStatus.NONE;
        }
        final EnsureResult result = autocrafting.ensureTask(blank, 1, actor, budget(SPARE_PLAN_BUDGET_MS));
        if (result == EnsureResult.TASK_CREATED) {
            RSAPG.LOG.info("{}: no spare {} in the network, asked for 1", where(grid), name);
        } else if (result == EnsureResult.MISSING_RESOURCES) {
            RSAPG.LOG.info("{}: no spare {} and its crafting is missing resources; retry in {} s",
                where(grid), name, SPARE_BACKOFF_TICKS / 20);
            return GridStatus.SPARE_MISSING_RESOURCES;
        }
        return GridStatus.NONE;
    }

    private static CancellationToken budget(final long millis) {
        final long deadline = System.currentTimeMillis() + millis;
        return new CancellationToken() {
            private boolean cancelled;

            @Override
            public boolean isCancelled() {
                if (System.currentTimeMillis() >= deadline) {
                    cancelled = true;
                }
                return cancelled;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        };
    }

    // ---------------------------------------------------------------- helpers

    public static boolean hasUpgrade(final PatternGridBlockEntity grid) {
        return host(grid).rsapg$upgrades().has(Items.INSTANCE.getAutocraftingUpgrade());
    }

    /** Plain and fluid blanks in the grid's network, for the screen; 0 when offline. */
    public static int blankCount(final PatternGridBlockEntity grid, final boolean fluid) {
        final Network network = grid.getNetworkForItem();
        if (network == null || !grid.isGridActive()) {
            return 0;
        }
        final ItemResource resource = fluid ? fluidBlank() : plainBlank();
        if (resource == null) {
            return 0;
        }
        // Menu data slots travel as shorts.
        return (int) Math.min(Short.MAX_VALUE, network.getComponent(StorageNetworkComponent.class).get(resource));
    }

    private static void setEncodeStatus(final PatternGridBlockEntity grid, final GridStatus status) {
        host(grid).rsapg$state().encodeStatus = status;
    }

    private static void setSpareStatus(final PatternGridBlockEntity grid, final GridStatus status) {
        host(grid).rsapg$state().spareStatus = status;
    }

    private static String kind(final boolean fluid) {
        return fluid ? "fluid substitution" : "plain";
    }

    private static String where(final PatternGridBlockEntity grid) {
        final BlockPos pos = grid.getBlockPos();
        return "advanced grid " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String describe(final ItemStack pattern) {
        if (pattern.isEmpty()) {
            return "nothing";
        }
        final CraftingPatternState crafting = pattern.get(DataComponents.INSTANCE.getCraftingPatternState());
        return pattern.getItem().builtInRegistryHolder().key().location()
            + (crafting != null ? " fuzzy=" + crafting.fuzzyMode() : "");
    }
}
