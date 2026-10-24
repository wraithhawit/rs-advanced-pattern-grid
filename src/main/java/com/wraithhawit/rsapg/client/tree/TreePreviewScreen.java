package com.wraithhawit.rsapg.client.tree;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.api.RefinedStorageClientApi;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceRendering;
import com.wraithhawit.rsapg.tree.PlanLine;
import com.wraithhawit.rsapg.tree.RecipeRequest;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The plan for an EMI recipe tree: one line per recipe, what it makes and where its pattern would
 * go, or why not. Stage 1 shows it only; nothing is encoded yet.
 */
public class TreePreviewScreen extends Screen {
    private static final int ROW = 20;
    private static final int TOP = 44;

    @Nullable
    private final Screen parent;
    private final List<RecipeRequest> recipes;
    private final List<PlanLine> lines;
    private final Map<PlanLine.Status, Integer> counts = new EnumMap<>(PlanLine.Status.class);
    /** True once the server has encoded: the lines are results, not a plan. */
    private final boolean done;
    @Nullable
    private Button encodeButton;
    private double scroll;

    public TreePreviewScreen(@Nullable final Screen parent, final List<RecipeRequest> recipes, final List<PlanLine> lines,
                             final boolean done) {
        super(Component.translatable(done ? "gui.rsapg.tree.title.done" : "gui.rsapg.tree.title"));
        this.parent = parent;
        this.recipes = recipes;
        this.lines = lines;
        this.done = done;
        for (final PlanLine line : lines) {
            counts.merge(line.status(), 1, Integer::sum);
        }
    }

    @Override
    protected void init() {
        final int toPlace = count(PlanLine.Status.PLACE);
        if (!done && toPlace > 0) {
            encodeButton = addRenderableWidget(Button.builder(Component.translatable("gui.rsapg.tree.encode", toPlace), b -> {
                b.active = false;
                b.setMessage(Component.translatable("gui.rsapg.tree.encoding"));
                TreeDumpClient.requestEncode(recipes);
            }).bounds(width / 2 - 104, height - 26, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(width / 2 + 4, height - 26, 100, 20)
                .build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20)
                .build());
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private int visibleRows() {
        return Math.max(1, (height - TOP - 32) / ROW);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double dx, final double dy) {
        final int max = Math.max(0, lines.size() - visibleRows());
        scroll = Math.max(0, Math.min(max, scroll - dy));
        return true;
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        final Component summary = done
            ? Component.translatable("gui.rsapg.tree.summary.done", count(PlanLine.Status.PLACED),
                count(PlanLine.Status.FAILED), count(PlanLine.Status.ALREADY_HAS),
                count(PlanLine.Status.NO_TARGET) + count(PlanLine.Status.UNSUPPORTED))
            : Component.translatable("gui.rsapg.tree.summary",
                count(PlanLine.Status.PLACE), count(PlanLine.Status.ALREADY_HAS),
                count(PlanLine.Status.NO_TARGET) + count(PlanLine.Status.UNSUPPORTED),
                count(PlanLine.Status.DUPLICATE));
        graphics.drawCenteredString(font, summary, width / 2, 24, 0xC0C0C0);

        String hoveredLocation = null;
        final int left = Math.max(10, width / 2 - 200);
        final int first = (int) scroll;
        final int last = Math.min(lines.size(), first + visibleRows());
        for (int i = first; i < last; ++i) {
            final PlanLine line = lines.get(i);
            final int y = TOP + (i - first) * ROW;
            final RecipeRequest recipe = line.index() < recipes.size() ? recipes.get(line.index()) : null;
            final ResourceAmount output = recipe == null ? null : recipe.primaryOutput();
            Component name = Component.literal("?");
            if (output != null) {
                final ResourceRendering rendering =
                    RefinedStorageClientApi.INSTANCE.getResourceRendering(output.resource().getClass());
                rendering.render(output.resource(), graphics, left, y);
                name = rendering.getDisplayName(output.resource());
            }
            graphics.drawString(font, name, left + 20, y + 1, 0xFFFFFF);
            if (recipe != null) {
                graphics.drawString(font, Component.literal(recipe.label()).withStyle(ChatFormatting.DARK_GRAY),
                    left + 20, y + 10, 0x808080);
            }
            final Component status = status(line);
            final int sx = left + 200;
            graphics.drawString(font, status, sx, y + 5, color(line.status()));
            if (!line.location().isEmpty() && mouseX >= sx && mouseX < sx + font.width(status)
                && mouseY >= y + 4 && mouseY < y + 14) {
                hoveredLocation = line.location();
            }
        }
        if (hoveredLocation != null) {
            graphics.renderComponentTooltip(font,
                List.of(Component.translatable("gui.rsapg.tree.location", hoveredLocation)), mouseX, mouseY);
        }
    }

    private Component status(final PlanLine line) {
        return switch (line.status()) {
            case PLACE -> Component.translatable("gui.rsapg.tree.place", line.target());
            case PLACED -> Component.translatable("gui.rsapg.tree.placed", line.target());
            default -> Component.literal(line.target());
        };
    }

    private static int color(final PlanLine.Status status) {
        return switch (status) {
            case PLACE, PLACED -> 0x7FE07F;
            case ALREADY_HAS, DUPLICATE -> 0x9A9A9A;
            case NO_TARGET, UNSUPPORTED, FAILED -> 0xFF7F7F;
        };
    }

    private int count(final PlanLine.Status status) {
        return counts.getOrDefault(status, 0);
    }
}
