package com.wraithhawit.rsapg;

import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;

import javax.annotation.Nullable;

/** Server-side runtime state of one advanced grid. Not saved: a reload starts clean. */
public final class AdvancedGridState {
    /** Result of the last encode attempt. */
    GridStatus encodeStatus = GridStatus.NONE;
    /** Result of keeping spare blanks stocked. */
    GridStatus spareStatus = GridStatus.NONE;

    /** Set while an encode waits for a blank pattern that is being crafted. */
    @Nullable
    PatternType pendingType;
    /** The waiting encode asked for a fluid blank (otherwise a plain one). */
    boolean pendingFluid;
    String requester = "Advanced Pattern Grid";

    /** Side-button toggle: the create button places the new pattern like the recipe-tree dump. Saved. */
    public boolean autoPlace;
    /**
     * The last recipe EMI's "+" transferred into this grid, so a processing pattern encoded from it
     * knows its machine. Not saved.
     */
    @javax.annotation.Nullable
    public com.wraithhawit.rsapg.tree.RecipeRequest lastTransfer;

    long ticks;
    long nextPendingEnsureTick;
    long nextSpareTick;

    /** Drops a waiting encode (a recipe-tree batch never waits on a crafted blank). */
    public void cancelPending() {
        pendingType = null;
    }

    public GridStatus shownStatus() {
        return encodeStatus != GridStatus.NONE ? encodeStatus : spareStatus;
    }
}
