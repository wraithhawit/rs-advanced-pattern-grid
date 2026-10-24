package com.wraithhawit.rsapg;

import com.mojang.brigadier.CommandDispatcher;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.wraithhawit.rsapg.mixin.PatternGridBlockEntityAccessor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeType;

/** Read-only comparison of direct recipe matching and the normal recipe-manager lookup. */
final class RecipeDiagnostics {
    private RecipeDiagnostics() { }

    static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rsapg").requires(source -> source.hasPermission(2))
            .then(Commands.literal("diagnose")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> diagnose(context.getSource(),
                        BlockPosArgument.getLoadedBlockPos(context, "pos"))))));
    }

    private static int diagnose(final CommandSourceStack source, final BlockPos pos) {
        final var level = source.getLevel();
        if (!(level.getBlockEntity(pos) instanceof PatternGridBlockEntity grid)) {
            source.sendFailure(Component.literal("No pattern grid at " + pos.toShortString()));
            return 0;
        }
        final var matrix = ((PatternGridBlockEntityAccessor) grid).rsapg$getCraftingMatrix();
        report(source, "Grid " + pos.toShortString() + ", active=" + grid.isGridActive()
            + ", muted=" + matrix.isMuted());
        for (int i = 0; i < matrix.getContainerSize(); i++) {
            final var stack = matrix.getItem(i);
            if (!stack.isEmpty()) {
                report(source, "Slot " + i + ": " + stack.getCount() + "x "
                    + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " components=" + stack.getComponentsPatch());
            }
        }
        final var input = matrix.asCraftInput();
        final var manager = level.getRecipeManager();
        final var lookup = manager.getRecipeFor(RecipeType.CRAFTING, input, level);
        report(source, "Normal lookup: " + lookup.map(holder -> holder.id().toString()).orElse("NONE"));
        int matches = 0;
        for (final var holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (holder.value().matches(input, level)) {
                matches++;
                if (matches <= 10) {
                    report(source, "Direct match: " + holder.id() + " -> "
                        + holder.value().assemble(input, level.registryAccess()));
                }
            }
        }
        report(source, "Direct match count: " + matches);
        return matches;
    }

    private static void report(final CommandSourceStack source, final String text) {
        source.sendSuccess(() -> Component.literal("[rsapg] " + text), false);
        RSAPG.LOG.info("diagnose: {}", text);
    }
}
