package com.wraithhawit.rsapg.menu;

import com.refinedmods.refinedstorage.common.support.containermenu.PropertyType;
import com.refinedmods.refinedstorage.common.support.containermenu.PropertyTypes;
import com.wraithhawit.rsapg.RSAPG;

/** Menu properties of the advanced grid, synced through Refined Storage's own property system. */
public final class AdvancedGridProperties {
    /** The create button places the pattern like the recipe-tree dump. */
    public static final PropertyType<Boolean> AUTO_PLACE = PropertyTypes.createBooleanProperty(RSAPG.id("auto_place"));

    private AdvancedGridProperties() {
    }
}
