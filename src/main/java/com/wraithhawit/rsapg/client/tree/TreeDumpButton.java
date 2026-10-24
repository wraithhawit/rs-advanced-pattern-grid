package com.wraithhawit.rsapg.client.tree;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridContainerMenu;
import com.wraithhawit.rsapg.AdvancedGridHost;
import com.wraithhawit.rsapg.menu.AdvancedMenu;
import com.wraithhawit.rsapg.tree.RecipeRequest;

import java.util.List;
import javax.annotation.Nullable;

import dev.emi.emi.bom.BoM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The "encode missing patterns" icon in EMI's recipe tree screen, left of EMI's help icon. EMI's
 * screen draws and handles its own controls without vanilla widgets, so this does the same.
 */
public final class TreeDumpButton {
    private static final int SIZE = 12;
    /** EMI's own recipe-screen "+" (fill) button: u 24 on its buttons sheet; v 0 normal, 12 hovered, 24 disabled. */
    private static final ResourceLocation EMI_BUTTONS = ResourceLocation.fromNamespaceAndPath("emi", "textures/gui/buttons.png");
    private static final int PLUS_U = 24;

    private TreeDumpButton() {
    }

    /** Left of EMI's 16x16 help icon (at width-18, height-18), vertically centred on it. */
    private static int x(final Screen screen) {
        return screen.width - 18 - SIZE - 4;
    }

    private static int y(final Screen screen) {
        return screen.height - 18 + 2;
    }

    private static boolean over(final Screen screen, final double mouseX, final double mouseY) {
        return mouseX >= x(screen) && mouseX < x(screen) + SIZE && mouseY >= y(screen) && mouseY < y(screen) + SIZE;
    }

    /** Why the button can't be used right now, or null if it can. */
    @Nullable
    private static Component unavailable(@Nullable final AbstractContainerScreen<?> from) {
        if (BoM.tree == null) {
            return Component.translatable("gui.rsapg.tree.no_tree");
        }
        if (from == null || !(from.getMenu() instanceof PatternGridContainerMenu)
            || !(from.getMenu() instanceof AdvancedMenu menu)
            || !AdvancedGridHost.isAdvanced(menu.rsapg$syncValue())) {
            return Component.translatable("gui.rsapg.tree.need_grid");
        }
        return null;
    }

    public static void render(final Screen screen, final GuiGraphics graphics, final int mouseX, final int mouseY,
                              @Nullable final AbstractContainerScreen<?> from) {
        final int x = x(screen);
        final int y = y(screen);
        final boolean hovered = over(screen, mouseX, mouseY);
        final Component reason = unavailable(from);
        final int v = reason != null ? 24 : hovered ? 12 : 0;
        graphics.blit(EMI_BUTTONS, x, y, PLUS_U, v, SIZE, SIZE, 256, 256);
        if (hovered) {
            final List<Component> lines = reason == null
                ? List.of(Component.translatable("gui.rsapg.tree.button"), Component.translatable("gui.rsapg.tree.button.help"))
                : List.of(Component.translatable("gui.rsapg.tree.button"), reason);
            graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
        }
    }

    /** @return true if the click was ours */
    public static boolean click(final Screen screen, final double mouseX, final double mouseY, final int button,
                                @Nullable final AbstractContainerScreen<?> from) {
        if (button != 0 || !over(screen, mouseX, mouseY)) {
            return false;
        }
        if (unavailable(from) != null || from == null) {
            return true;
        }
        final List<RecipeRequest> requests = TreeCollector.collect(BoM.tree);
        TreeDumpClient.requestPlan(from.getMenu().containerId, requests, screen);
        return true;
    }
}
