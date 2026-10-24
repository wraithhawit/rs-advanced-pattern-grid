package com.wraithhawit.rsapg.menu;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;

import javax.annotation.Nullable;

/** Mixed into Refined Storage's PatternGridContainerMenu. */
public interface AdvancedMenu {
    /** See {@link com.wraithhawit.rsapg.AdvancedGridHost#rsapg$syncValue()}. */
    int rsapg$syncValue();

    /** Blank patterns (or fluid substitution blanks) in the network, as last synced. */
    int rsapg$blankCount(boolean fluid);

    /** The grid's block entity; server side only (null on the client). */
    @Nullable
    PatternGridBlockEntity rsapg$grid();
}
