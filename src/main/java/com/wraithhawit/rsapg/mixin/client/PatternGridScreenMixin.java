package com.wraithhawit.rsapg.mixin.client;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridContainerMenu;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridScreen;
import com.refinedmods.refinedstorage.common.content.Items;
import com.refinedmods.refinedstorage.common.support.AbstractBaseScreen;
import com.refinedmods.refinedstorage.common.support.Sprites;
import com.wraithhawit.rsapg.AdvancedGridHost;
import com.wraithhawit.rsapg.GridStatus;
import com.wraithhawit.rsapg.GridUpgradeDestination;
import com.wraithhawit.rsapg.RSAPG;
import com.wraithhawit.rsapg.compat.Rfs;
import com.wraithhawit.rsapg.menu.AdvancedMenu;
import com.wraithhawit.rsapg.menu.AdvancedUpgradeSlot;
import com.wraithhawit.rsapg.menu.SwitchableSlot;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Advanced grid additions to RS's pattern grid screen:
 * <ul>
 *   <li>the upgrade panel right of the grid, drawn like Refined Wireless Upgrades' wireless grid panel;</li>
 *   <li>in the hidden blank slot's place, a faded blank pattern with the network's blank count;</li>
 *   <li>RS's warning (or autocrafting) icon on the output slot, with the reason as a tooltip.</li>
 * </ul>
 */
@Mixin(value = PatternGridScreen.class, remap = false)
public abstract class PatternGridScreenMixin extends AbstractContainerScreen<PatternGridContainerMenu> {
    @Unique
    private static final ResourceLocation RSAPG$PANEL = RSAPG.id("textures/gui/upgrade_panel.png");
    @Unique
    private static final int RSAPG$PANEL_WIDTH = 30;
    @Unique
    private static final int RSAPG$PANEL_TEX_HEIGHT = 46;
    /** Slot frame is 7 px into the panel strip; the panel starts at the screen's top. */
    @Unique
    private static final int RSAPG$PANEL_X = AdvancedUpgradeSlot.FIRST_X - 7;
    @Unique
    private static final int RSAPG$PANEL_HEIGHT = 10 + GridUpgradeDestination.SLOTS * AdvancedUpgradeSlot.ROW;
    @Unique
    private static final int RSAPG$ICON = Sprites.WARNING_SIZE;
    /** The screen border's grey (vanilla container background). */
    @Unique
    private static final int RSAPG$BORDER = 0xFFC6C6C6;
    /** Same grey, partly transparent, to fade the blank-pattern icon into the border. */
    @Unique
    private static final int RSAPG$FADE = 0x99C6C6C6;
    /** The output slot sits this far below the blank slot, as in RS's menu. */
    @Unique
    private static final int RSAPG$OUTPUT_OFFSET = 36;

    @Unique
    @Nullable
    private Rect2i rsapg$exclusionZone;

    protected PatternGridScreenMixin(final PatternGridContainerMenu menu, final Inventory inventory,
                                     final Component title) {
        super(menu, inventory, title);
    }

    @Unique
    private boolean rsapg$advanced() {
        return AdvancedGridHost.isAdvanced(((AdvancedMenu) menu).rsapg$syncValue());
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void rsapg$renderPanel(final GuiGraphics graphics, final float partialTicks, final int mouseX,
                                   final int mouseY, final CallbackInfo ci) {
        rsapg$updateExclusionZone();
        if (!rsapg$advanced()) {
            return;
        }
        // Paint over the hidden blank slot's frame so it reads as border, not as a slot.
        final Slot blankSlot = rsapg$blankSlot();
        if (blankSlot != null) {
            graphics.fill(leftPos + blankSlot.x - 1, topPos + blankSlot.y - 1,
                leftPos + blankSlot.x + 17, topPos + blankSlot.y + 17, RSAPG$BORDER);
        }
        final int x = leftPos + RSAPG$PANEL_X;
        final int y = topPos;
        rsapg$blitPanel(graphics, x, y, 0, 5);
        for (int row = 0; row < GridUpgradeDestination.SLOTS; ++row) {
            rsapg$blitPanel(graphics, x, y + 5 + row * AdvancedUpgradeSlot.ROW, 5, AdvancedUpgradeSlot.ROW);
        }
        rsapg$blitPanel(graphics, x, y + 5 + GridUpgradeDestination.SLOTS * AdvancedUpgradeSlot.ROW, 41, 5);
    }

    @Unique
    private static void rsapg$blitPanel(final GuiGraphics graphics, final int x, final int y, final int v,
                                        final int height) {
        graphics.blit(RSAPG$PANEL, x, y, 0, v, RSAPG$PANEL_WIDTH, height, RSAPG$PANEL_WIDTH, RSAPG$PANEL_TEX_HEIGHT);
    }

    /** Keeps EMI/JEI from drawing over the panel, and only while it is shown. */
    @Unique
    private void rsapg$updateExclusionZone() {
        final List<Rect2i> zones = ((AbstractBaseScreen<?>) (Object) this).getExclusionZones();
        final boolean show = rsapg$advanced();
        if (show && rsapg$exclusionZone == null) {
            rsapg$exclusionZone = new Rect2i(leftPos + RSAPG$PANEL_X, topPos, RSAPG$PANEL_WIDTH, RSAPG$PANEL_HEIGHT);
            zones.add(rsapg$exclusionZone);
        } else if (!show && rsapg$exclusionZone != null) {
            zones.remove(rsapg$exclusionZone);
            rsapg$exclusionZone = null;
        } else if (show && (rsapg$exclusionZone.getX() != leftPos + RSAPG$PANEL_X
            || rsapg$exclusionZone.getY() != topPos)) {
            rsapg$exclusionZone.setPosition(leftPos + RSAPG$PANEL_X, topPos);
        }
    }

    /** Clicks on the panel are inside the screen (otherwise a held item would be dropped). */
    @Override
    protected boolean hasClickedOutside(final double mouseX, final double mouseY, final int left, final int top,
                                        final int button) {
        if (rsapg$advanced()
            && mouseX >= left + RSAPG$PANEL_X && mouseX < left + RSAPG$PANEL_X + RSAPG$PANEL_WIDTH
            && mouseY >= top && mouseY < top + RSAPG$PANEL_HEIGHT) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void rsapg$renderOverlays(final GuiGraphics graphics, final int mouseX, final int mouseY,
                                      final float partialTicks, final CallbackInfo ci) {
        if (!rsapg$advanced()) {
            return;
        }
        final Slot blankSlot = rsapg$blankSlot();
        if (blankSlot == null) {
            return;
        }
        rsapg$renderBlankCount(graphics, mouseX, mouseY, blankSlot);
        rsapg$renderStatus(graphics, mouseX, mouseY, blankSlot);
    }

    @Unique
    private void rsapg$renderBlankCount(final GuiGraphics graphics, final int mouseX, final int mouseY,
                                        final Slot blankSlot) {
        final int x = leftPos + blankSlot.x;
        final int y = topPos + blankSlot.y;
        final int plain = ((AdvancedMenu) menu).rsapg$blankCount(false);
        final int fluid = ((AdvancedMenu) menu).rsapg$blankCount(true);
        final ItemStack icon = new ItemStack(Items.INSTANCE.getPattern());
        graphics.renderFakeItem(icon, x, y);
        // Fade it so it reads as information, not an item in a slot.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(x, y, x + 16, y + 16, RSAPG$FADE);
        graphics.pose().popPose();
        graphics.renderItemDecorations(font, icon, x, y, rsapg$shortCount(plain));
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            final List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.rsapg.blanks.title").getVisualOrderText());
            lines.add(Component.translatable("gui.rsapg.blanks.plain", rsapg$longCount(plain)).getVisualOrderText());
            if (Rfs.isLoaded()) {
                lines.add(Component.translatable("gui.rsapg.blanks.fluid", rsapg$longCount(fluid)).getVisualOrderText());
            }
            graphics.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Unique
    private void rsapg$renderStatus(final GuiGraphics graphics, final int mouseX, final int mouseY,
                                    final Slot blankSlot) {
        final GridStatus status = AdvancedGridHost.status(((AdvancedMenu) menu).rsapg$syncValue());
        final ResourceLocation sprite = switch (status.icon()) {
            case NONE -> null;
            case WARNING -> Sprites.WARNING;
            case AUTOCRAFTING -> Sprites.AUTOCRAFTING_INDICATOR;
        };
        if (sprite == null) {
            return;
        }
        // Same corner RS uses for its exporting indicators, on the output slot.
        final int x = leftPos + blankSlot.x + 8;
        final int y = topPos + blankSlot.y + RSAPG$OUTPUT_OFFSET + 8;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.blitSprite(sprite, x, y, RSAPG$ICON, RSAPG$ICON);
        graphics.pose().popPose();
        if (mouseX >= x && mouseX <= x + RSAPG$ICON && mouseY >= y && mouseY <= y + RSAPG$ICON) {
            graphics.renderTooltip(font, font.split(status.tooltip(), 200), mouseX, mouseY);
        }
    }

    @Unique
    private static String rsapg$shortCount(final int count) {
        if (count >= Short.MAX_VALUE) {
            return "32k+";
        }
        return count >= 1000 ? (count / 1000) + "k" : Integer.toString(count);
    }

    @Unique
    private static String rsapg$longCount(final int count) {
        return count >= Short.MAX_VALUE ? "32767+" : Integer.toString(count);
    }

    @Unique
    @Nullable
    private Slot rsapg$blankSlot() {
        for (final Slot slot : menu.slots) {
            if (slot instanceof SwitchableSlot) {
                return slot;
            }
        }
        return null;
    }
}
