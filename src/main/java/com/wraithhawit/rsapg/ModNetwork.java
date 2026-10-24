package com.wraithhawit.rsapg;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.wraithhawit.rsapg.menu.AdvancedMenu;
import com.wraithhawit.rsapg.tree.PlanLine;
import com.wraithhawit.rsapg.tree.RecipeRequest;
import com.wraithhawit.rsapg.tree.TreeEncoder;
import com.wraithhawit.rsapg.tree.TreePlanner;

import java.util.List;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * rsapg's network channel. NeoForge compares {@link #PROTOCOL} at join, so a client and server
 * with different menu layouts or packets are refused up front with the mismatched-mods screen
 * naming rsapg (instead of "Network Protocol Error" when a grid opens).
 *
 * <p>Bump {@link #PROTOCOL} whenever menu slots, data slots or payloads change.
 */
public final class ModNetwork {
    /**
     * 2 = upgrade panel + blank counts (0.1.1+); 3 = recipe-tree plan (0.1.6); 4 = plan line location
     * (0.1.7); 5 = recipe-tree encode (0.1.8+).
     */
    public static final String PROTOCOL = "5";

    private ModNetwork() {
    }

    private record LayoutCheck() implements CustomPacketPayload {
        static final Type<LayoutCheck> TYPE = new Type<>(RSAPG.id("menu_layout"));
        static final StreamCodec<ByteBuf, LayoutCheck> CODEC = StreamCodec.unit(new LayoutCheck());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: plan (or, with {@code encode}, encode and place) these EMI tree recipes against
     * the grid open in container {@code containerId}.
     */
    public record PlanTree(int containerId, List<RecipeRequest> recipes, boolean encode) implements CustomPacketPayload {
        public static final Type<PlanTree> TYPE = new Type<>(RSAPG.id("plan_tree"));
        static final StreamCodec<RegistryFriendlyByteBuf, PlanTree> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlanTree::containerId,
            RecipeRequest.STREAM_CODEC.apply(ByteBufCodecs.list(512)), PlanTree::recipes,
            ByteBufCodecs.BOOL, PlanTree::encode,
            PlanTree::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → client: one line per requested recipe (or a single error); {@code done} after encoding. */
    public record TreePlan(List<PlanLine> lines, String error, boolean done) implements CustomPacketPayload {
        public static final Type<TreePlan> TYPE = new Type<>(RSAPG.id("tree_plan"));
        static final StreamCodec<ByteBuf, TreePlan> CODEC = StreamCodec.composite(
            PlanLine.STREAM_CODEC.apply(ByteBufCodecs.list()), TreePlan::lines,
            ByteBufCodecs.STRING_UTF8, TreePlan::error,
            ByteBufCodecs.BOOL, TreePlan::done,
            TreePlan::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(final RegisterPayloadHandlersEvent event) {
        event.registrar(RSAPG.MOD_ID)
            .versioned(PROTOCOL)
            .playToClient(LayoutCheck.TYPE, LayoutCheck.CODEC, (payload, context) -> {
            })
            .playToServer(PlanTree.TYPE, PlanTree.CODEC, ModNetwork::onPlanTree)
            .playToClient(TreePlan.TYPE, TreePlan.CODEC, (payload, context) -> ClientHooks.onTreePlan(payload));
    }

    /** The advanced grid behind the player's open menu, or null. */
    @Nullable
    private static PatternGridBlockEntity advancedGrid(final ServerPlayer player, final int containerId) {
        if (player.containerMenu.containerId != containerId || !(player.containerMenu instanceof AdvancedMenu menu)) {
            return null;
        }
        final PatternGridBlockEntity grid = menu.rsapg$grid();
        return grid != null && ((AdvancedGridHost) grid).rsapg$isAdvanced() ? grid : null;
    }

    private static void onPlanTree(final PlanTree payload, final IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        final PatternGridBlockEntity grid = advancedGrid(player, payload.containerId());
        if (grid == null) {
            PacketDistributor.sendToPlayer(player,
                new TreePlan(List.of(), "open the recipe tree from an Advanced Pattern Grid", payload.encode()));
            return;
        }
        final List<PlanLine> lines;
        if (payload.encode()) {
            lines = TreeEncoder.encode(player, grid, payload.recipes());
        } else {
            lines = TreePlanner.plan(player.serverLevel(), grid, payload.recipes())
                .stream().map(TreePlanner.Assignment::line).toList();
            RSAPG.LOG.info("tree: {} asked to plan {} recipes: {} to place", player.getGameProfile().getName(),
                payload.recipes().size(), lines.stream().filter(l -> l.status() == PlanLine.Status.PLACE).count());
        }
        PacketDistributor.sendToPlayer(player, new TreePlan(lines, "", payload.encode()));
    }
}
