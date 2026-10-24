package com.wraithhawit.rsapg.client.tree;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.wraithhawit.rsapg.tree.RecipeRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.MaterialTree;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Reads EMI's recipe tree and converts each chosen recipe the way Refined Storage's EMI transfer
 * does, including rstweaks' corrections: tag ingredients keep their count, never-consumed inputs
 * (chance 0) are left out except GregTech's programmed circuit when gttweaks will store it, and
 * outputs below 100% chance are left off (RS would wait for them forever).
 */
final class TreeCollector {
    private static final ResourceLocation GT_CIRCUIT = ResourceLocation.fromNamespaceAndPath("gtceu", "programmed_circuit");

    private TreeCollector() {
    }

    static List<RecipeRequest> collect(final MaterialTree tree) {
        final Set<EmiRecipe> recipes = new LinkedHashSet<>();
        walk(tree.goal, recipes, 0);
        final List<RecipeRequest> requests = new ArrayList<>(recipes.size());
        for (final EmiRecipe recipe : recipes) {
            requests.add(convert(recipe));
        }
        return requests;
    }

    private static void walk(final MaterialNode node, final Set<EmiRecipe> recipes, final int depth) {
        // Catalysts (circuits, tools) are kept, not made per batch; the tree bounds the depth itself.
        if (node == null || node.catalyst || depth > 64) {
            return;
        }
        if (node.recipe != null && !(node.recipe instanceof EmiResolutionRecipe)) {
            recipes.add(node.recipe);
        }
        if (node.children != null) {
            for (final MaterialNode child : node.children) {
                walk(child, recipes, depth + 1);
            }
        }
    }

    private static RecipeRequest convert(final EmiRecipe recipe) {
        final EmiRecipeCategory category = recipe.getCategory();
        final String label = category.getName().getString();
        final List<ResourceLocation> workstations = new ArrayList<>();
        for (final EmiIngredient station : EmiApi.getRecipeManager().getWorkstations(category)) {
            for (final EmiStack stack : station.getEmiStacks()) {
                final ItemStack item = stack.getItemStack();
                if (!item.isEmpty()) {
                    workstations.add(BuiltInRegistries.ITEM.getKey(item.getItem()));
                }
            }
        }
        if (category == VanillaEmiRecipeCategories.CRAFTING) {
            return new RecipeRequest(RecipeRequest.Kind.CRAFTING, label, items(recipe.getInputs()),
                itemOutputs(recipe.getOutputs()), workstations);
        }
        if (category == VanillaEmiRecipeCategories.STONECUTTING) {
            return new RecipeRequest(RecipeRequest.Kind.STONECUTTER, label, items(recipe.getInputs()),
                itemOutputs(recipe.getOutputs()), workstations);
        }
        if (category == VanillaEmiRecipeCategories.SMITHING) {
            return new RecipeRequest(RecipeRequest.Kind.SMITHING, label, items(recipe.getInputs()),
                itemOutputs(recipe.getOutputs()), workstations);
        }
        final List<List<ResourceAmount>> inputs = new ArrayList<>();
        for (final EmiIngredient input : recipe.getInputs()) {
            if (input.getChance() != 0.0F || keepsCircuit(input)) {
                inputs.add(amounts(input));
            }
        }
        final List<List<ResourceAmount>> outputs = new ArrayList<>();
        for (final EmiStack output : recipe.getOutputs()) {
            if (!output.isEmpty() && output.getChance() >= 1.0F) {
                outputs.add(amounts(output));
            }
        }
        return new RecipeRequest(RecipeRequest.Kind.PROCESSING, label, inputs, outputs, workstations);
    }

    /** RS's crafting/stonecutter/smithing transfer: per slot, the item stacks of the ingredient. */
    private static List<List<ResourceAmount>> items(final List<EmiIngredient> ingredients) {
        final List<List<ResourceAmount>> slots = new ArrayList<>(ingredients.size());
        for (final EmiIngredient ingredient : ingredients) {
            final List<ResourceAmount> slot = new ArrayList<>();
            for (final EmiStack stack : ingredient.getEmiStacks()) {
                final ItemStack item = stack.getItemStack();
                if (!item.isEmpty()) {
                    slot.add(new ResourceAmount(ItemResource.ofItemStack(item), 1));
                }
            }
            slots.add(slot);
        }
        return slots;
    }

    private static List<List<ResourceAmount>> itemOutputs(final List<EmiStack> outputs) {
        final List<List<ResourceAmount>> slots = new ArrayList<>();
        for (final EmiStack output : outputs) {
            final ItemStack item = output.getItemStack();
            if (!item.isEmpty()) {
                slots.add(List.of(new ResourceAmount(ItemResource.ofItemStack(item), Math.max(1, output.getAmount()))));
            }
        }
        return slots;
    }

    /** RS's processing conversion, with rstweaks' rescale: every alternative at the ingredient's amount. */
    private static List<ResourceAmount> amounts(final EmiIngredient ingredient) {
        final long amount = ingredient.getAmount();
        final List<ResourceAmount> out = new ArrayList<>();
        for (final EmiStack stack : ingredient.getEmiStacks()) {
            final Optional<ResourceAmount> converted =
                RefinedStorageApi.INSTANCE.getIngredientConverter().convertToResourceAmount(stack);
            converted.ifPresent(each -> out.add(amount > 0 && each.amount() != amount
                ? new ResourceAmount(each.resource(), amount)
                : each));
        }
        return out;
    }

    private static boolean keepsCircuit(final EmiIngredient input) {
        return ModList.get().isLoaded("gttweaks")
            && input.getEmiStacks().size() == 1
            && GT_CIRCUIT.equals(BuiltInRegistries.ITEM.getKey(input.getEmiStacks().getFirst().getItemStack().getItem()));
    }
}
