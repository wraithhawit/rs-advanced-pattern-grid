package com.wraithhawit.rsapg;

import net.minecraft.network.chat.Component;

/**
 * What the advanced grid shows next to its output slot. Synced to the screen as an ordinal, so
 * only append new values.
 */
public enum GridStatus {
    NONE(Icon.NONE),
    NO_NETWORK(Icon.WARNING),
    NO_BLANKS(Icon.WARNING),
    NO_BLANK_PATTERN(Icon.WARNING),
    CRAFTING_BLANK(Icon.AUTOCRAFTING),
    MISSING_RESOURCES(Icon.WARNING),
    NETWORK_FULL(Icon.WARNING),
    PLAIN_FALLBACK(Icon.WARNING),
    SPARE_MISSING_RESOURCES(Icon.WARNING),
    SPARE_NO_BLANK_PATTERN(Icon.WARNING);

    public enum Icon { NONE, WARNING, AUTOCRAFTING }

    private final Icon icon;

    GridStatus(final Icon icon) {
        this.icon = icon;
    }

    public Icon icon() {
        return icon;
    }

    public Component tooltip() {
        return Component.translatable("gui.rsapg.status." + name().toLowerCase());
    }

    public static GridStatus byOrdinal(final int ordinal) {
        final GridStatus[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }
}
