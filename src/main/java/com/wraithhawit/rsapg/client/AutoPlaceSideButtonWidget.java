package com.wraithhawit.rsapg.client;

import com.refinedmods.refinedstorage.common.support.containermenu.ClientProperty;
import com.refinedmods.refinedstorage.common.support.widget.AbstractSideButtonWidget;
import com.wraithhawit.rsapg.RSAPG;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Advanced grid side button: whether the create button places the new pattern in an autocrafter
 * (like the recipe-tree dump) or leaves it in the output slot. RS's own side-button widget, so it
 * looks and behaves like the grid's other toggles.
 */
public class AutoPlaceSideButtonWidget extends AbstractSideButtonWidget {
    private static final MutableComponent TITLE = Component.translatable("gui.rsapg.auto_place");
    private static final List<MutableComponent> ON = List.of(
        Component.translatable("gui.rsapg.auto_place.on").withStyle(ChatFormatting.GRAY));
    private static final List<MutableComponent> OFF = List.of(
        Component.translatable("gui.rsapg.auto_place.off").withStyle(ChatFormatting.GRAY));
    private static final Component HELP_ON = Component.translatable("gui.rsapg.auto_place.on.help");
    private static final Component HELP_OFF = Component.translatable("gui.rsapg.auto_place.off.help");
    private static final ResourceLocation SPRITE_ON = RSAPG.id("widget/side_button/auto_place/on");
    private static final ResourceLocation SPRITE_OFF = RSAPG.id("widget/side_button/auto_place/off");

    private final ClientProperty<Boolean> property;

    public AutoPlaceSideButtonWidget(final ClientProperty<Boolean> property) {
        super(btn -> property.setValue(!Boolean.TRUE.equals(property.getValue())));
        this.property = property;
    }

    private boolean on() {
        return Boolean.TRUE.equals(property.getValue());
    }

    @Override
    protected ResourceLocation getSprite() {
        return on() ? SPRITE_ON : SPRITE_OFF;
    }

    @Override
    protected MutableComponent getTitle() {
        return TITLE;
    }

    @Override
    protected List<MutableComponent> getSubText() {
        return on() ? ON : OFF;
    }

    @Override
    protected Component getHelpText() {
        return on() ? HELP_ON : HELP_OFF;
    }
}
