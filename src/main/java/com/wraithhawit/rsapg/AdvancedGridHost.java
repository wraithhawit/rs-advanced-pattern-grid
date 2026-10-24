package com.wraithhawit.rsapg;

import com.refinedmods.refinedstorage.common.upgrade.UpgradeContainer;

/** Mixed into Refined Storage's PatternGridBlockEntity. */
public interface AdvancedGridHost {
    /** True when the block holding this pattern grid is an {@link AdvancedPatternGridBlock}. */
    boolean rsapg$isAdvanced();

    /** The side panel's upgrade slots (RS upgrade container, destination ADVANCED_PATTERN_GRID). */
    UpgradeContainer rsapg$upgrades();

    AdvancedGridState rsapg$state();

    /** Packed for the menu's data slot: bit 0 = advanced, the rest = shown {@link GridStatus}. */
    default int rsapg$syncValue() {
        return (rsapg$isAdvanced() ? 1 : 0) | (rsapg$state().shownStatus().ordinal() << 1);
    }

    static boolean isAdvanced(final int syncValue) {
        return (syncValue & 1) != 0;
    }

    static GridStatus status(final int syncValue) {
        return GridStatus.byOrdinal(syncValue >> 1);
    }
}
