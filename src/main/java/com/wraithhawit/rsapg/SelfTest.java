package com.wraithhawit.rsapg;

import com.mojang.brigadier.CommandDispatcher;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.autocrafting.CraftingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.content.Items;
import com.refinedmods.refinedstorage.common.support.RecipeMatrixContainer;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.wraithhawit.rsapg.compat.Rfs;
import com.wraithhawit.rsapg.mixin.AutocrafterBlockEntityAccessor;
import com.wraithhawit.rsapg.mixin.PatternGridBlockEntityAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * {@code /rsapg selftest <pos>}: drives an advanced grid on a live network through the encode
 * scenarios and logs PASS/FAIL per check. It changes the network's blank patterns and buckets and
 * the grid's slots, so run it on a test rig only.
 */
final class SelfTest {
    private static final Actor ACTOR = () -> "rsapg selftest";

    private final ServerLevel level;
    private final PatternGridBlockEntity grid;
    private final StorageNetworkComponent storage;
    private final List<String> failures = new ArrayList<>();
    private int passed;

    private SelfTest(final ServerLevel level, final PatternGridBlockEntity grid, final StorageNetworkComponent storage) {
        this.level = level;
        this.grid = grid;
        this.storage = storage;
    }

    static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rsapg")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("selftest")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> run(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                        Phase.MAIN, null))
                    .then(Commands.literal("autocraft")
                        .then(Commands.argument("autocrafter", BlockPosArgument.blockPos())
                            .executes(ctx -> run(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                Phase.AUTOCRAFT_START, BlockPosArgument.getLoadedBlockPos(ctx, "autocrafter")))))
                    .then(Commands.literal("autocraftcheck")
                        .executes(ctx -> run(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                            Phase.AUTOCRAFT_CHECK, null)))
                    .then(Commands.literal("sparecheck")
                        .executes(ctx -> run(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                            Phase.SPARE_CHECK, null)))
                    .then(Commands.literal("plan")
                        .executes(ctx -> run(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                            Phase.PLAN, null)))
                    .then(Commands.literal("encode")
                        .executes(ctx -> run(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                            Phase.ENCODE, null))))));
    }

    private enum Phase { MAIN, AUTOCRAFT_START, AUTOCRAFT_CHECK, SPARE_CHECK, PLAN, ENCODE }

    private static int run(final CommandSourceStack source, final BlockPos pos, final Phase phase,
                           @Nullable final BlockPos autocrafterPos) {
        final ServerLevel level = source.getLevel();
        final BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PatternGridBlockEntity grid) || !((AdvancedGridHost) grid).rsapg$isAdvanced()) {
            source.sendFailure(Component.literal("no advanced pattern grid at " + pos.toShortString()));
            return 0;
        }
        final Network network = grid.getNetworkForItem();
        if (network == null || !grid.isGridActive()) {
            source.sendFailure(Component.literal("grid is not on an active network"));
            return 0;
        }
        final SelfTest test = new SelfTest(level, grid, network.getComponent(StorageNetworkComponent.class));
        try {
            switch (phase) {
                case MAIN -> test.runAll();
                case AUTOCRAFT_START -> test.autocraftStart(level.getBlockEntity(autocrafterPos));
                case AUTOCRAFT_CHECK -> test.autocraftCheck();
                case SPARE_CHECK -> test.spareCheck();
                case PLAN -> test.plan();
                case ENCODE -> test.encodeTree();
            }
        } catch (final RuntimeException e) {
            RSAPG.LOG.error("selftest: aborted", e);
            test.failures.add("aborted: " + e);
        } finally {
            if (phase == Phase.MAIN || phase == Phase.SPARE_CHECK) {
                test.cleanup();
            }
        }
        final String summary = "rsapg selftest " + phase.name().toLowerCase() + ": " + test.passed + " passed, " + test.failures.size() + " failed"
            + (test.failures.isEmpty() ? "" : " -- " + String.join("; ", test.failures));
        RSAPG.LOG.info(summary);
        source.sendSuccess(() -> Component.literal(summary), false);
        return test.failures.isEmpty() ? 1 : 0;
    }

    // ---------------------------------------------------------------- scenarios

    private void runAll() {
        reset();
        acc().rsapg$setPatternType(PatternType.CRAFTING);

        // 1. No blanks, no upgrade: nothing encoded, warning shown.
        placeRecipe(recipe("minecraft:stone_bricks"));
        check("recipe ready (stone bricks)", acc().rsapg$createCraftingPattern() != null, "");
        AdvancedGridLogic.encode(grid, "selftest");
        check("no blanks: output empty", output().isEmpty(), describe(output()));
        check("no blanks: status NO_BLANKS", status() == GridStatus.NO_BLANKS, "" + status());

        // 2. Blanks in the network (and one left in the hidden input slot, as shift-click would).
        give(plain(), 2);
        grid.getPatternInput().setItem(0, new ItemStack(Items.INSTANCE.getPattern()));
        AdvancedGridLogic.encode(grid, "selftest");
        check("plain: output is a pattern", output().is(Items.INSTANCE.getPattern())
            && output().has(DataComponents.INSTANCE.getCraftingPatternState()), describe(output()));
        check("plain: used one blank (3 -> 2 in network)", count(plain()) == 2, "blanks=" + count(plain()));
        check("plain: hidden input emptied", grid.getPatternInput().getItem(0).isEmpty(),
            describe(grid.getPatternInput().getItem(0)));
        check("plain: status NONE", status() == GridStatus.NONE, "" + status());

        // 3. Same kind already in the output: re-encoded without a blank.
        acc().rsapg$setFuzzyMode(true);
        AdvancedGridLogic.encode(grid, "selftest");
        check("re-encode: no blank used", count(plain()) == 2, "blanks=" + count(plain()));

        // 4. Fuzzy mode changes nothing for stone bricks (one exact item) -> encoded without it.
        check("fuzzy dropped: stone bricks pattern fuzzy=false", !fuzzy(output()), describe(output()));

        // 5. Fuzzy mode matters for a crafting table (any planks) -> kept.
        placeRecipe(recipe("minecraft:crafting_table"));
        AdvancedGridLogic.encode(grid, "selftest");
        check("fuzzy kept: crafting table pattern fuzzy=true", fuzzy(output()), describe(output()));
        acc().rsapg$setFuzzyMode(false);

        if (Rfs.isLoaded()) {
            fluidScenarios();
        } else {
            RSAPG.LOG.info("selftest: Refined Fluid Substitution not installed, fluid scenarios skipped");
        }

        // Upgrade in, no blanks, no pattern for blanks: says so.
        drain(plain());
        clearOutput();
        placeRecipe(recipe("minecraft:stone_bricks"));
        ((AdvancedGridHost) grid).rsapg$upgrades().setItem(0, new ItemStack(Items.INSTANCE.getAutocraftingUpgrade()));
        AdvancedGridLogic.encode(grid, "selftest");
        final boolean hasBlankPattern = !grid.getNetworkForItem().getComponent(AutocraftingNetworkComponent.class)
            .getPatternsByOutput(plain()).isEmpty();
        if (hasBlankPattern) {
            // The rig has an autocrafter for blanks (from the autocraft phase): it should ask for one.
            check("upgrade, blank pattern on network: status CRAFTING_BLANK", status() == GridStatus.CRAFTING_BLANK,
                "" + status());
            ((AdvancedGridHost) grid).rsapg$state().pendingType = null;
        } else {
            check("upgrade, no blank pattern: status NO_BLANK_PATTERN", status() == GridStatus.NO_BLANK_PATTERN,
                "" + status());
        }
        check("upgrade, no blanks: output empty", output().isEmpty(), describe(output()));
        check("upgrade counts as installed in any panel slot", rsapgUpgradeInSlot(3), "");
    }

    private void fluidScenarios() {
        final RecipeHolder<CraftingRecipe> fluidRecipe = findFluidRecipe();
        if (fluidRecipe == null) {
            failures.add("no crafting recipe with a water bucket qualifies for RFS");
            return;
        }
        RSAPG.LOG.info("selftest: fluid recipe {}", fluidRecipe.id());
        placeRecipe(fluidRecipe);
        drain(Rfs.blank().isEmpty() ? plain() : ItemResource.ofItemStack(Rfs.blank()));
        drain(bucket());
        // Output holds a plain pattern; no fluid blank, no buckets -> plain re-encode with a warning.
        check("fluid setup: plain pattern in output", !output().isEmpty() && !Rfs.isFluidPattern(output()),
            describe(output()));
        final long plainBefore = count(plain());
        AdvancedGridLogic.encode(grid, "selftest");
        check("fluid, no blank/buckets: stays plain", !Rfs.isFluidPattern(output()), describe(output()));
        check("fluid, no blank/buckets: status PLAIN_FALLBACK", status() == GridStatus.PLAIN_FALLBACK, "" + status());
        check("fluid, no blank/buckets: no blank used", count(plain()) == plainBefore, "blanks=" + count(plain()));

        // Two buckets: the plain output goes back as a blank, a blank + 2 buckets become a fluid blank.
        give(bucket(), 2);
        AdvancedGridLogic.encode(grid, "selftest");
        check("fluid from plain+buckets: output is RFS", Rfs.isFluidPattern(output()), describe(output()));
        check("fluid from plain+buckets: RFS substitutes a fluid in it", Rfs.qualifies(output(), level),
            describe(output()));
        check("fluid from plain+buckets: buckets used", count(bucket()) == 0, "buckets=" + count(bucket()));
        check("fluid from plain+buckets: blanks net unchanged", count(plain()) == plainBefore,
            "blanks=" + count(plain()) + " before=" + plainBefore);
        check("fluid from plain+buckets: status NONE", status() == GridStatus.NONE, "" + status());

        // A stocked fluid blank is used directly.
        clearOutput();
        final ItemResource fluidBlank = ItemResource.ofItemStack(Rfs.blank());
        give(fluidBlank, 1);
        AdvancedGridLogic.encode(grid, "selftest");
        check("fluid blank: output is RFS", Rfs.isFluidPattern(output()), describe(output()));
        check("fluid blank: consumed", count(fluidBlank) == 0, "fluid blanks=" + count(fluidBlank));
        check("fluid blank: plain blanks untouched", count(plain()) == plainBefore, "blanks=" + count(plain()));

        // Back to a non-fluid recipe with an RFS pattern in the output: swapped for a plain one.
        placeRecipe(recipe("minecraft:stone_bricks"));
        AdvancedGridLogic.encode(grid, "selftest");
        check("swap back: output plain", !output().isEmpty() && !Rfs.isFluidPattern(output()), describe(output()));
        check("swap back: RFS pattern returned as a blank", count(fluidBlank) == 1, "fluid blanks=" + count(fluidBlank));
        check("swap back: one plain blank used", count(plain()) == plainBefore - 1, "blanks=" + count(plain()));
    }

    // ---------------------------------------------------------------- autocraft (three commands, wait between)

    /**
     * Loads a blank-pattern crafting pattern into the autocrafter, stocks its ingredients, then
     * asks the grid (upgrade in, no blanks) to encode: it must ask for a blank and wait.
     */
    private void autocraftStart(@Nullable final BlockEntity autocrafter) {
        if (!(autocrafter instanceof AutocrafterBlockEntityAccessor crafter)) {
            failures.add("no autocrafter at the given position");
            return;
        }
        reset();
        acc().rsapg$setPatternType(PatternType.CRAFTING);
        final RecipeHolder<CraftingRecipe> blankRecipe = findRecipeFor(Items.INSTANCE.getPattern());
        RSAPG.LOG.info("selftest: blank pattern recipe {}", blankRecipe.id());
        // Encode the pattern for blanks with the grid itself.
        give(plain(), 1);
        placeRecipe(blankRecipe);
        AdvancedGridLogic.encode(grid, "selftest");
        final ItemStack blankPattern = output().copy();
        check("autocraft setup: pattern for blanks encoded", !blankPattern.isEmpty(), describe(blankPattern));
        clearOutput();
        crafter.rsapg$getPatternContainer().setItem(0, blankPattern);
        // Ingredients for a handful of blanks.
        for (final Ingredient ingredient : blankRecipe.value().getIngredients()) {
            final ItemStack[] items = ingredient.getItems();
            if (items.length > 0) {
                give(ItemResource.ofItemStack(items[0].copyWithCount(1)), 16);
            }
        }
        drain(plain());
        placeRecipe(recipe("minecraft:stone_bricks"));
        ((AdvancedGridHost) grid).rsapg$upgrades().setItem(0, new ItemStack(Items.INSTANCE.getAutocraftingUpgrade()));
        AdvancedGridLogic.encode(grid, "selftest");
        check("autocraft: status CRAFTING_BLANK", status() == GridStatus.CRAFTING_BLANK, "" + status());
        check("autocraft: waiting", ((AdvancedGridHost) grid).rsapg$state().pendingType == PatternType.CRAFTING,
            "" + ((AdvancedGridHost) grid).rsapg$state().pendingType);
        check("autocraft: nothing encoded yet", output().isEmpty(), describe(output()));
    }

    /** A few seconds later: the crafted blank was encoded. */
    private void autocraftCheck() {
        check("autocraft later: output encoded", !output().isEmpty()
            && output().has(DataComponents.INSTANCE.getCraftingPatternState()), describe(output()));
        check("autocraft later: status NONE", status() == GridStatus.NONE, "" + status());
        check("autocraft later: no longer waiting", ((AdvancedGridHost) grid).rsapg$state().pendingType == null,
            "" + ((AdvancedGridHost) grid).rsapg$state().pendingType);
    }

    /** A few seconds more: a spare blank was crafted ahead. */
    private void spareCheck() {
        check("craft ahead: a spare blank in the network", count(plain()) >= 1, "blanks=" + count(plain()));
        check("craft ahead: spare status NONE", ((AdvancedGridHost) grid).rsapg$state().spareStatus == GridStatus.NONE,
            "" + ((AdvancedGridHost) grid).rsapg$state().spareStatus);
    }

    private RecipeHolder<CraftingRecipe> findRecipeFor(final net.minecraft.world.item.Item item) {
        for (final RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!(holder.value() instanceof CustomRecipe)
                && holder.value().getResultItem(level.registryAccess()).is(item)) {
                return holder;
            }
        }
        throw new IllegalStateException("no crafting recipe makes " + item);
    }

    // ---------------------------------------------------------------- recipe-tree plan (stage 1)

    /** Plans a hand-built "tree" against the rig and logs every line; checks the expected verdicts. */
    private void plan() {
        final ResourceAmount bricks = itemAmount("minecraft:stone_bricks", 4);
        final ResourceAmount stone = itemAmount("minecraft:stone", 1);
        final java.util.List<java.util.List<ResourceAmount>> crafting = new ArrayList<>();
        for (int i = 0; i < 9; ++i) {
            crafting.add(i == 0 || i == 1 || i == 3 || i == 4 ? List.of(stone) : List.of());
        }
        final ResourceLocation wiremill = ResourceLocation.parse("gtceu:lv_wiremill");
        final ResourceLocation bender = ResourceLocation.parse("gtceu:lv_bender");
        final List<com.wraithhawit.rsapg.tree.RecipeRequest> requests = List.of(
            new com.wraithhawit.rsapg.tree.RecipeRequest(com.wraithhawit.rsapg.tree.RecipeRequest.Kind.CRAFTING,
                "Crafting", crafting, List.of(List.of(bricks)), List.of()),
            new com.wraithhawit.rsapg.tree.RecipeRequest(com.wraithhawit.rsapg.tree.RecipeRequest.Kind.PROCESSING,
                "Wiremill", List.of(List.of(itemAmount("minecraft:copper_ingot", 1))),
                List.of(List.of(itemAmount("gtceu:copper_single_wire", 2))), List.of(wiremill)),
            new com.wraithhawit.rsapg.tree.RecipeRequest(com.wraithhawit.rsapg.tree.RecipeRequest.Kind.PROCESSING,
                "Bender", List.of(List.of(itemAmount("minecraft:iron_ingot", 1))),
                List.of(List.of(itemAmount("gtceu:iron_plate", 1))), List.of(bender)),
            new com.wraithhawit.rsapg.tree.RecipeRequest(com.wraithhawit.rsapg.tree.RecipeRequest.Kind.CRAFTING,
                "Crafting", crafting, List.of(List.of(bricks)), List.of()),
            new com.wraithhawit.rsapg.tree.RecipeRequest(com.wraithhawit.rsapg.tree.RecipeRequest.Kind.CRAFTING,
                "Crafting", crafting, List.of(List.of(new ResourceAmount(plain(), 1))), List.of())
        );
        final var plan = com.wraithhawit.rsapg.tree.TreePlanner.plan(level, grid, requests);
        for (final var a : plan) {
            RSAPG.LOG.info("selftest plan: #{} {} {}", a.line().index(), a.line().status(), a.line().target());
        }
        check("plan: stone bricks to a machine-free autocrafter",
            plan.get(0).target() != null && plan.get(0).target().kind == com.wraithhawit.rsapg.tree.PatternTargets.Kind.FREE,
            plan.get(0).line().toString());
        check("plan: copper wire to the wiremill's autocrafter",
            plan.get(1).target() != null && wiremill.equals(plan.get(1).target().machine), plan.get(1).line().toString());
        check("plan: bender recipe has nowhere to go",
            plan.get(2).line().status() == com.wraithhawit.rsapg.tree.PlanLine.Status.NO_TARGET, plan.get(2).line().toString());
        check("plan: repeated stone bricks flagged",
            plan.get(3).line().status() == com.wraithhawit.rsapg.tree.PlanLine.Status.DUPLICATE, plan.get(3).line().toString());
        check("plan: blank patterns already on the network",
            plan.get(4).line().status() == com.wraithhawit.rsapg.tree.PlanLine.Status.ALREADY_HAS, plan.get(4).line().toString());
    }

    /**
     * Stage 2 end to end with a fake player: the player's grid recipe and output pattern must come
     * back, two patterns must land in the planned autocrafters, two blanks must be used. The test
     * patterns are removed again afterwards so the rig stays reusable.
     */
    private void encodeTree() {
        final net.minecraft.server.level.ServerPlayer player =
            net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        reset();
        // The "player's" grid: a stone bricks recipe in the matrix and an encoded pattern in the output.
        acc().rsapg$setPatternType(PatternType.CRAFTING);
        placeRecipe(recipe("minecraft:stone_bricks"));
        give(plain(), 1);
        AdvancedGridLogic.encode(grid, "selftest");
        final ItemStack ownOutput = output().copy();
        check("encode setup: own pattern in the output", !ownOutput.isEmpty(), describe(ownOutput));
        give(plain(), 3);
        final long blanksBefore = count(plain());

        final java.util.List<java.util.List<ResourceAmount>> andesite = new ArrayList<>();
        for (int i = 0; i < 9; ++i) {
            andesite.add(i == 0 || i == 1 || i == 3 || i == 4 ? new ArrayList<>(List.of(itemAmount("minecraft:andesite", 1)))
                : new ArrayList<>());
        }
        final ResourceLocation wiremill = ResourceLocation.parse("gtceu:lv_wiremill");
        final List<com.wraithhawit.rsapg.tree.RecipeRequest> requests = List.of(
            new com.wraithhawit.rsapg.tree.RecipeRequest(com.wraithhawit.rsapg.tree.RecipeRequest.Kind.CRAFTING,
                "Crafting", andesite, List.of(List.of(itemAmount("minecraft:polished_andesite", 4))), List.of()),
            new com.wraithhawit.rsapg.tree.RecipeRequest(com.wraithhawit.rsapg.tree.RecipeRequest.Kind.PROCESSING,
                "Wiremill", List.of(new ArrayList<>(List.of(itemAmount("gtceu:tin_ingot", 1)))),
                List.of(new ArrayList<>(List.of(itemAmount("gtceu:tin_single_wire", 2)))), List.of(wiremill))
        );
        final List<com.wraithhawit.rsapg.tree.PlanLine> results =
            com.wraithhawit.rsapg.tree.TreeEncoder.encode(player, grid, requests);
        for (final var line : results) {
            RSAPG.LOG.info("selftest encode: #{} {} {} {}", line.index(), line.status(), line.target(), line.location());
        }
        check("encode: polished andesite placed",
            results.get(0).status() == com.wraithhawit.rsapg.tree.PlanLine.Status.PLACED, results.get(0).toString());
        check("encode: tin wire placed",
            results.get(1).status() == com.wraithhawit.rsapg.tree.PlanLine.Status.PLACED, results.get(1).toString());
        check("encode: two blanks used", count(plain()) == blanksBefore - 2,
            "blanks " + blanksBefore + " -> " + count(plain()));
        check("encode: own output pattern back", ItemStack.isSameItemSameComponents(output(), ownOutput),
            describe(output()));
        final ItemStack restored = AdvancedGridLogic.draft(grid, PatternType.CRAFTING);
        final CraftingPatternState restoredState = restored == null ? null
            : restored.get(DataComponents.INSTANCE.getCraftingPatternState());
        final CraftingPatternState ownState = ownOutput.get(DataComponents.INSTANCE.getCraftingPatternState());
        check("encode: own grid recipe back (stone bricks)",
            acc().rsapg$getPatternType() == PatternType.CRAFTING && restoredState != null && ownState != null
                && restoredState.input().equals(ownState.input()), String.valueOf(restoredState));
        final int removed = removeTestPatterns(List.of("minecraft:polished_andesite", "gtceu:tin_single_wire"));
        check("encode: found and removed both test patterns in autocrafters", removed == 2, "removed " + removed);
    }

    /** Takes patterns making any of these items out of every pattern target on the network. */
    private int removeTestPatterns(final List<String> outputIds) {
        int removed = 0;
        final var network = grid.getNetworkForItem();
        for (final var target : com.wraithhawit.rsapg.tree.PatternTargets.collect(level.getServer(), network)) {
            final net.minecraft.world.level.block.entity.BlockEntity be = target.level.getBlockEntity(target.pos);
            if (be == null) {
                continue;
            }
            try {
                java.lang.reflect.Method m = null;
                for (Class<?> c = be.getClass(); c != null && m == null; c = c.getSuperclass()) {
                    try {
                        m = c.getDeclaredMethod("getPatternContainer");
                    } catch (final NoSuchMethodException ignored) {
                        // up the hierarchy
                    }
                }
                if (m == null) {
                    continue;
                }
                m.setAccessible(true);
                final net.minecraft.world.Container container = (net.minecraft.world.Container) m.invoke(be);
                for (int i = 0; i < container.getContainerSize(); ++i) {
                    final ItemStack stack = container.getItem(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    final java.util.Optional<ItemStack> out = Items.INSTANCE.getPattern().getOutput(stack, level);
                    if (out.isPresent() && outputIds.contains(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(out.get().getItem()).toString())) {
                        container.setItem(i, ItemStack.EMPTY);
                        removed++;
                    }
                }
            } catch (final ReflectiveOperationException e) {
                RSAPG.LOG.warn("selftest: cleanup failed at {}", target.pos, e);
            }
        }
        return removed;
    }

    private static ResourceAmount itemAmount(final String id, final long amount) {
        return new ResourceAmount(new ItemResource(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            ResourceLocation.parse(id))), amount);
    }

    // ---------------------------------------------------------------- helpers

    private boolean rsapgUpgradeInSlot(final int slot) {
        final var upgrades = ((AdvancedGridHost) grid).rsapg$upgrades();
        upgrades.clearContent();
        upgrades.setItem(slot, new ItemStack(Items.INSTANCE.getAutocraftingUpgrade()));
        final boolean seen = AdvancedGridLogic.hasUpgrade(grid);
        upgrades.clearContent();
        return seen;
    }

    private void check(final String name, final boolean ok, final String detail) {
        if (ok) {
            passed++;
            RSAPG.LOG.info("selftest: PASS {}", name);
        } else {
            failures.add(name + " (" + detail + ")");
            RSAPG.LOG.info("selftest: FAIL {} ({})", name, detail);
        }
    }

    private PatternGridBlockEntityAccessor acc() {
        return (PatternGridBlockEntityAccessor) grid;
    }

    private ItemStack output() {
        return acc().rsapg$getPatternOutput().getItem(0);
    }

    private void clearOutput() {
        acc().rsapg$getPatternOutput().setItem(0, ItemStack.EMPTY);
    }

    private GridStatus status() {
        return ((AdvancedGridHost) grid).rsapg$state().encodeStatus;
    }

    private void reset() {
        ((AdvancedGridHost) grid).rsapg$upgrades().setItem(0, ItemStack.EMPTY);
        ((AdvancedGridHost) grid).rsapg$state().pendingType = null;
        grid.getPatternInput().setItem(0, ItemStack.EMPTY);
        clearOutput();
        drain(plain());
        drain(bucket());
        if (Rfs.isLoaded()) {
            drain(ItemResource.ofItemStack(Rfs.blank()));
        }
        acc().rsapg$setFuzzyMode(false);
    }

    private void cleanup() {
        ((AdvancedGridHost) grid).rsapg$upgrades().setItem(0, ItemStack.EMPTY);
        ((AdvancedGridHost) grid).rsapg$state().pendingType = null;
        acc().rsapg$setFuzzyMode(false);
    }

    private static ItemResource plain() {
        return new ItemResource(Items.INSTANCE.getPattern());
    }

    private static ItemResource bucket() {
        return new ItemResource(net.minecraft.world.item.Items.BUCKET);
    }

    private long count(final ItemResource resource) {
        return storage.get(resource);
    }

    private void give(final ItemResource resource, final long amount) {
        final long inserted = storage.insert(resource, amount, Action.EXECUTE, ACTOR);
        if (inserted != amount) {
            throw new IllegalStateException("network took " + inserted + " of " + amount + " " + resource);
        }
    }

    private void drain(final ItemResource resource) {
        final long amount = storage.get(resource);
        if (amount > 0) {
            storage.extract(resource, amount, Action.EXECUTE, ACTOR);
        }
    }

    private static boolean fuzzy(final ItemStack pattern) {
        final CraftingPatternState state = pattern.get(DataComponents.INSTANCE.getCraftingPatternState());
        return state != null && state.fuzzyMode();
    }

    private static String describe(final ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        return stack.getCount() + "x " + stack.getItem().builtInRegistryHolder().key().location()
            + (stack.has(DataComponents.INSTANCE.getCraftingPatternState()) ? " fuzzy=" + fuzzy(stack) : "");
    }

    @SuppressWarnings("unchecked")
    private RecipeHolder<CraftingRecipe> recipe(final String id) {
        final Optional<RecipeHolder<?>> holder = level.getRecipeManager().byKey(ResourceLocation.parse(id));
        if (holder.isEmpty() || !(holder.get().value() instanceof CraftingRecipe)) {
            throw new IllegalStateException("no crafting recipe " + id);
        }
        return (RecipeHolder<CraftingRecipe>) holder.get();
    }

    private void placeRecipe(final RecipeHolder<CraftingRecipe> holder) {
        final RecipeMatrixContainer matrix = acc().rsapg$getCraftingMatrix();
        matrix.clearContent();
        final CraftingRecipe recipe = holder.value();
        final List<Ingredient> ingredients = recipe.getIngredients();
        final int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        for (int i = 0; i < ingredients.size(); ++i) {
            final ItemStack[] items = ingredients.get(i).getItems();
            if (items.length == 0) {
                continue;
            }
            final int row = i / width;
            final int col = i % width;
            matrix.setItem(row * 3 + col, items[0].copyWithCount(1));
        }
    }

    private RecipeHolder<CraftingRecipe> findFluidRecipe() {
        for (final RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            final CraftingRecipe recipe = holder.value();
            if (recipe instanceof CustomRecipe || recipe.getIngredients().size() > 9) {
                continue;
            }
            final boolean hasWater = recipe.getIngredients().stream().anyMatch(ingredient ->
                ingredient.getItems().length == 1 && ingredient.getItems()[0].is(net.minecraft.world.item.Items.WATER_BUCKET));
            if (!hasWater) {
                continue;
            }
            placeRecipe(holder);
            final ItemStack draft = acc().rsapg$createCraftingPattern();
            if (draft != null && Rfs.qualifies(draft, level)) {
                return holder;
            }
        }
        return null;
    }
}
