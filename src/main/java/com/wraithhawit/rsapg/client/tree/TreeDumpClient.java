package com.wraithhawit.rsapg.client.tree;

import com.wraithhawit.rsapg.ModNetwork;
import com.wraithhawit.rsapg.RSAPG;
import com.wraithhawit.rsapg.tree.RecipeRequest;

import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client state of one "encode the recipe tree" round trip. EMI-free on purpose. */
public final class TreeDumpClient {
    @Nullable
    private static List<RecipeRequest> pending;
    @Nullable
    private static Screen returnTo;
    private static int containerId;

    private TreeDumpClient() {
    }

    /** Sends the collected recipes to the server for a plan; the reply opens the preview. */
    public static void requestPlan(final int menuId, final List<RecipeRequest> recipes, final Screen from) {
        pending = List.copyOf(recipes);
        returnTo = from;
        containerId = menuId;
        RSAPG.LOG.info("tree: asking the server to plan {} recipes", recipes.size());
        PacketDistributor.sendToServer(new ModNetwork.PlanTree(containerId, pending, false));
    }

    /** The preview's Encode button: the same recipes again, this time encoded and placed. */
    static void requestEncode(final List<RecipeRequest> recipes) {
        pending = List.copyOf(recipes);
        RSAPG.LOG.info("tree: asking the server to encode {} recipes", recipes.size());
        PacketDistributor.sendToServer(new ModNetwork.PlanTree(containerId, pending, true));
    }

    public static void onPlan(final ModNetwork.TreePlan plan) {
        final List<RecipeRequest> recipes = pending;
        pending = null;
        if (recipes == null) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (!plan.error().isEmpty()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("Advanced Pattern Grid: " + plan.error()), false);
            }
            return;
        }
        mc.setScreen(new TreePreviewScreen(returnTo, recipes, plan.lines(), plan.done()));
    }
}
